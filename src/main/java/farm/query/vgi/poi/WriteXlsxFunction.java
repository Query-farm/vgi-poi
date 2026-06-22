package farm.query.vgi.poi;

import farm.query.vgi.function.ArgSpec;
import farm.query.vgi.function.Arguments;
import farm.query.vgi.function.FunctionMetadata;
import farm.query.vgi.internal.SchemaUtil;
import farm.query.vgi.protocol.BindResponse;
import farm.query.vgi.tableinout.TableInOutBindParams;
import farm.query.vgi.tableinout.TableInOutExchangeState;
import farm.query.vgi.tableinout.TableInOutFunction;
import farm.query.vgi.tableinout.TableInOutInitParams;
import farm.query.vgi.types.Schemas;
import farm.query.vgirpc.AnnotatedBatch;
import farm.query.vgirpc.CallContext;
import farm.query.vgirpc.OutputCollector;
import farm.query.vgirpc.wire.Allocators;
import org.apache.arrow.vector.BigIntVector;
import org.apache.arrow.vector.BitVector;
import org.apache.arrow.vector.DateDayVector;
import org.apache.arrow.vector.DateMilliVector;
import org.apache.arrow.vector.FieldVector;
import org.apache.arrow.vector.Float4Vector;
import org.apache.arrow.vector.Float8Vector;
import org.apache.arrow.vector.IntVector;
import org.apache.arrow.vector.SmallIntVector;
import org.apache.arrow.vector.TimeStampVector;
import org.apache.arrow.vector.TinyIntVector;
import org.apache.arrow.vector.VarBinaryVector;
import org.apache.arrow.vector.VarCharVector;
import org.apache.arrow.vector.VectorSchemaRoot;
import org.apache.arrow.vector.types.pojo.Field;
import org.apache.arrow.vector.types.pojo.Schema;
import org.apache.arrow.vector.util.Text;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellStyle;
import org.apache.poi.ss.usermodel.CreationHelper;
import org.apache.poi.ss.usermodel.Font;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;

import java.io.ByteArrayOutputStream;
import java.util.List;

/**
 * {@code excel.write_xlsx(TABLE, sheet_name := 'Sheet1', header := true)} — a
 * buffering TABLE-IN-OUT that consumes the streamed input relation and emits a
 * single BLOB row containing an .xlsx workbook of that relation.
 *
 * <p><b>Streaming model.</b> The VGI table-in-out driver calls
 * {@link #onInputBatch} once per input batch and then stops when the input
 * stream closes — there is no separate finalize callback and (as DuckDB's vgi
 * extension delivers it) no guaranteed terminal empty batch. So this function
 * builds the workbook incrementally, appending each batch's rows, and emits a
 * single BLOB row of the workbook-so-far after each input batch. For the common
 * single-batch input that is exactly one row carrying the whole relation; an
 * input large enough to span multiple Arrow batches yields one row per batch,
 * the last of which is the complete workbook (a documented simplification).
 *
 * <p>Cell typing: numeric Arrow columns -> numeric cells, booleans -> boolean
 * cells, date/timestamp columns -> date-formatted cells, everything else ->
 * string cells. With {@code header := true} a bold first row carries the input
 * column names.
 */
public final class WriteXlsxFunction implements TableInOutFunction {

    @Override public String name() { return "write_xlsx"; }

    @Override public FunctionMetadata metadata() {
        return FunctionMetadata.describe(
                        "Serialize an input relation into an .xlsx workbook BLOB with typed cells and an "
                                + "optional bold header row (Apache POI).")
                .withCategories("spreadsheet", "excel", "poi");
    }

    @Override public List<ArgSpec> argumentSpecs() {
        return List.of(
                ArgSpec.table("input", 0),
                ArgSpec.named("sheet_name", Schemas.UTF8, "Sheet1"),
                ArgSpec.named("header", Schemas.BOOL, "true"));
    }

    @Override public BindResponse onBind(TableInOutBindParams p) {
        return BindResponse.forSchema(SchemaUtil.serializeSchema(PoiSchemas.WRITE_XLSX_SCHEMA));
    }

    @Override public TableInOutExchangeState createExchange(TableInOutInitParams params) {
        Arguments a = params.arguments();
        return new State(params.inputSchema(),
                a.namedString("sheet_name", "Sheet1"),
                a.namedBool("header", true));
    }

    public static final class State extends TableInOutExchangeState {
        private final Schema inputSchema;
        private final String sheetName;
        private final boolean header;

        private XSSFWorkbook workbook;
        private Sheet sheet;
        private CellStyle headerStyle;
        private CellStyle dateStyle;
        private int nextRow;        // next physical row to write into the sheet
        private boolean built;      // header written / workbook initialised

        State(Schema inputSchema, String sheetName, boolean header) {
            this.inputSchema = inputSchema;
            this.sheetName = sheetName == null || sheetName.isEmpty() ? "Sheet1" : sheetName;
            this.header = header;
        }

        private void ensureWorkbook() {
            if (built) return;
            workbook = new XSSFWorkbook();
            sheet = workbook.createSheet(sheetName);

            Font bold = workbook.createFont();
            bold.setBold(true);
            headerStyle = workbook.createCellStyle();
            headerStyle.setFont(bold);

            CreationHelper helper = workbook.getCreationHelper();
            dateStyle = workbook.createCellStyle();
            dateStyle.setDataFormat(helper.createDataFormat().getFormat("yyyy-mm-dd hh:mm:ss"));

            if (header && inputSchema != null) {
                Row hr = sheet.createRow(nextRow++);
                List<Field> fields = inputSchema.getFields();
                for (int c = 0; c < fields.size(); c++) {
                    Cell cell = hr.createCell(c);
                    cell.setCellValue(fields.get(c).getName());
                    cell.setCellStyle(headerStyle);
                }
            }
            built = true;
        }

        @Override public void onInputBatch(AnnotatedBatch batch, OutputCollector out, CallContext ctx) {
            VectorSchemaRoot in = batch.root();
            int n = in.getRowCount();

            // A zero-row batch (some plans send one) is a terminal flush signal:
            // emit the complete workbook and finish.
            if (n == 0) {
                emitWorkbook(out);
                out.finish();
                return;
            }

            ensureWorkbook();
            List<Field> fields = in.getSchema().getFields();
            for (int r = 0; r < n; r++) {
                Row row = sheet.createRow(nextRow++);
                for (int c = 0; c < fields.size(); c++) {
                    FieldVector v = in.getVector(c);
                    writeCell(row.createCell(c), v, r);
                }
            }

            // The VGI table-in-out transport delivers input batches until the
            // input stream closes; there is no separate finalize callback and no
            // guaranteed terminal empty batch. So we emit the workbook built from
            // all rows seen SO FAR after each input batch. For the common
            // single-batch input this is exactly one BLOB row of the whole
            // relation; for a relation large enough to span multiple batches the
            // last emitted row is the complete workbook (one row per batch).
            emitWorkbook(out);
        }

        /** Serialize the current accumulated workbook and emit it as a BLOB row. */
        private void emitWorkbook(OutputCollector out) {
            ensureWorkbook(); // handles empty input (header-only / blank book)
            byte[] bytes;
            try (ByteArrayOutputStream bos = new ByteArrayOutputStream()) {
                workbook.write(bos);
                bytes = bos.toByteArray();
            } catch (Exception e) {
                System.err.println("write_xlsx: failed to serialize workbook: " + e);
                bytes = new byte[0];
            }

            VectorSchemaRoot root = VectorSchemaRoot.create(PoiSchemas.WRITE_XLSX_SCHEMA, Allocators.root());
            root.allocateNew();
            VarBinaryVector xlsx = (VarBinaryVector) root.getVector("xlsx");
            xlsx.setSafe(0, bytes);
            root.setRowCount(1);
            out.emit(root);
        }

        private void writeCell(Cell cell, FieldVector v, int row) {
            if (v.isNull(row)) {
                cell.setBlank();
                return;
            }
            if (v instanceof Float8Vector d) {
                cell.setCellValue(d.get(row));
            } else if (v instanceof Float4Vector f) {
                cell.setCellValue(f.get(row));
            } else if (v instanceof IntVector iv) {
                cell.setCellValue(iv.get(row));
            } else if (v instanceof BigIntVector bv) {
                cell.setCellValue((double) bv.get(row));
            } else if (v instanceof SmallIntVector sv) {
                cell.setCellValue(sv.get(row));
            } else if (v instanceof TinyIntVector tv) {
                cell.setCellValue(tv.get(row));
            } else if (v instanceof BitVector bit) {
                cell.setCellValue(bit.get(row) != 0);
            } else if (v instanceof DateDayVector dd) {
                cell.setCellValue(java.time.LocalDate.ofEpochDay(dd.get(row)));
                cell.setCellStyle(dateStyle);
            } else if (v instanceof DateMilliVector dm) {
                cell.setCellValue(new java.util.Date(dm.get(row)));
                cell.setCellStyle(dateStyle);
            } else if (v instanceof TimeStampVector ts) {
                // TimeStamp* store an integer count of the vector's time unit since
                // epoch; normalise to millis via the vector's own getObject when
                // possible, else fall back to a string.
                Object obj = ts.getObject(row);
                if (obj instanceof java.time.LocalDateTime ldt) {
                    cell.setCellValue(ldt);
                    cell.setCellStyle(dateStyle);
                } else {
                    cell.setCellValue(String.valueOf(obj));
                }
            } else if (v instanceof VarCharVector s) {
                Object o = s.getObject(row);
                cell.setCellValue(o instanceof Text t ? t.toString() : String.valueOf(o));
            } else if (v instanceof VarBinaryVector ignored) {
                // Don't dump raw bytes into a cell; record the length instead.
                cell.setCellValue("<binary>");
            } else {
                Object o = v.getObject(row);
                cell.setCellValue(o == null ? "" : o.toString());
            }
        }
    }
}
