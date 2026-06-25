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
import org.apache.arrow.vector.types.pojo.ArrowType;
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
                .withCategories("spreadsheet", "excel", "poi")
                .withTag("vgi.example_queries",
                        "[{\"sql\":\"SELECT octet_length(xlsx) AS bytes FROM excel.main.write_xlsx("
                                + "(SELECT 1 AS id, 'Ada' AS name), sheet_name := "
                                + "'People');\",\"description\":\"Export a query result to an .xlsx "
                                + "workbook BLOB on a named sheet.\"}]")
                .withExamples(Meta.examples(
                        Meta.example(
                                "SELECT octet_length(xlsx) > 0 AS produced FROM excel.main.write_xlsx("
                                        + "(SELECT 1 AS id, 'Ada' AS name), sheet_name := 'People');",
                                "Export a one-row query result to an .xlsx workbook BLOB on a named "
                                        + "sheet and confirm bytes were produced."),
                        Meta.example(
                                "SELECT excel.main.detect_excel(xlsx) AS fmt FROM excel.main.write_xlsx("
                                        + "(SELECT 1 AS id, 'Ada' AS name));",
                                "Round-trip check: the BLOB written by write_xlsx is detected as an "
                                        + "'xlsx' workbook.")))
                .withTag("vgi.result_columns_md",
                        "| column | type | description |\n"
                                + "|---|---|---|\n"
                                + "| `xlsx` | BLOB | The generated `.xlsx` workbook bytes (one row per "
                                + "input batch; the last row is the complete workbook). |")
                .withTags(Meta.objectTags(
                        "Write Relation To Xlsx Workbook",
                        "## write_xlsx\n\n"
                                + "A **table-in-out** function that consumes an input relation and "
                                + "emits a single-column (`xlsx` `BLOB`) result carrying that relation "
                                + "serialized as an `.xlsx` (OOXML) workbook. Named arguments: "
                                + "`sheet_name := 'Sheet1'` sets the worksheet name and "
                                + "`header := true` writes a **bold first row** of the input column "
                                + "names.\n\n"
                                + "Cells are typed from the input columns: numeric columns become "
                                + "numeric cells, booleans become boolean cells, date/timestamp "
                                + "columns become date-formatted cells, and everything else is written "
                                + "as text. Because DuckDB rejects `length()` on a `BLOB`, use "
                                + "`octet_length(xlsx)` to size the result.\n\n"
                                + "**Streaming caveat**: the table-in-out transport delivers input "
                                + "batches with no separate finalize signal, so the workbook-so-far is "
                                + "emitted after each input batch. A typical single-batch input yields "
                                + "exactly one BLOB row containing the whole relation; an input large "
                                + "enough to span multiple Arrow batches yields one row per batch, the "
                                + "**last** of which is the complete workbook. Pair it with "
                                + "`detect_excel` to validate the output.",
                        "Serializes a SQL relation into a downloadable Microsoft Excel `.xlsx` "
                                + "workbook, returned as a `BLOB`, with typed cells and an optional "
                                + "bold header row.\n\n"
                                + "**Usage**\n\n"
                                + "```sql\n"
                                + "SELECT octet_length(xlsx) AS bytes\n"
                                + "FROM excel.main.write_xlsx(\n"
                                + "  (SELECT id, name FROM people),\n"
                                + "  sheet_name := 'People', header := true);\n"
                                + "```\n\n"
                                + "**Notes**: numeric/boolean/date columns keep their Excel cell "
                                + "types; use `octet_length` (not `length`) on the BLOB; for a "
                                + "multi-batch input the last emitted row is the complete workbook.",
                        "write xlsx, export excel, to excel, serialize spreadsheet, workbook blob, "
                                + "table to xlsx, save xlsx, generate excel, apache poi, "
                                + "header row, sheet name",
                        "WriteXlsxFunction.java"));
    }

    @Override public List<ArgSpec> argumentSpecs() {
        // ArgSpec canonical ctor: (name, position, arrowType, doc, isConst,
        // hasDefault, defaultValue, typeBound, varargs, anyType, tableInput).
        return List.of(
                new ArgSpec(
                        "input", 0, new ArrowType.Null(),
                        "The input relation (a subquery) whose rows are serialized into the "
                                + ".xlsx workbook; its column names become the optional header "
                                + "row and each column maps to a typed Excel cell.",
                        false, false, "", List.of(), false, false, true),
                new ArgSpec(
                        "sheet_name", -1, Schemas.UTF8,
                        "Name of the worksheet to write the rows into; defaults to 'Sheet1' "
                                + "when omitted.",
                        true, true, "Sheet1", List.of(), false, false, false),
                new ArgSpec(
                        "header", -1, Schemas.BOOL,
                        "Whether to write a bold first row containing the input column names; "
                                + "true (the default) emits the header, false omits it.",
                        true, true, "true", List.of(), false, false, false));
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

    /**
     * Per-exchange state for the buffering {@code write_xlsx} table-in-out.
     *
     * <p><b>HTTP-serializable.</b> Over the HTTP transport the worker is stateless
     * across exchanges, so the framework CBOR-serializes this state into a
     * continuation token between every input-batch exchange (StateSerializer skips
     * {@code static}/{@code transient}/{@code synthetic} fields). A live Apache POI
     * {@link XSSFWorkbook}/{@link Sheet}/{@link CellStyle} is NOT serializable, so
     * the previous design (which kept the workbook as state) made the http
     * {@code /init/exchange} return 500 "state serialize failed" — silently skipped
     * by the sqllogictest runner. subprocess/unix kept the live workbook in memory
     * and were unaffected.
     *
     * <p>The fix accumulates only serializable DATA — the column names plus the
     * cell values of every row seen so far, each cell a plain JSON/CBOR-friendly
     * value (Double/Long/Boolean/String + an ISO date/timestamp string tagged so it
     * is re-emitted as a date cell) — and (re)builds the POI workbook locally in
     * {@code emitWorkbook} from that accumulated data. The schema is persisted as
     * bytes; everything POI is local, never a state field.
     */
    public static final class State extends TableInOutExchangeState {
        // Persisted (serializable) fields.
        public byte[] inputSchemaBytes;
        public String sheetName;
        public boolean header;
        public java.util.List<String> columnNames = new java.util.ArrayList<>();
        // Accumulated rows; each cell is a CellValue (a tagged, serializable value).
        public java.util.List<java.util.List<CellValue>> rows = new java.util.ArrayList<>();

        public State() {}

        State(Schema inputSchema, String sheetName, boolean header) {
            this.inputSchemaBytes = inputSchema == null ? null : SchemaUtil.serializeSchema(inputSchema);
            this.sheetName = sheetName == null || sheetName.isEmpty() ? "Sheet1" : sheetName;
            this.header = header;
            if (inputSchema != null) {
                for (Field f : inputSchema.getFields()) {
                    columnNames.add(f.getName());
                }
            }
        }

        /** A serializable spreadsheet cell value: a kind tag plus its payload. */
        public static final class CellValue {
            public Kind kind;
            public double num;
            public boolean bool;
            public String str;     // string text OR ISO date/timestamp for DATE/DATETIME

            public enum Kind { BLANK, NUMBER, BOOL, STRING, DATE, DATETIME }

            public CellValue() {}

            private CellValue(Kind kind) { this.kind = kind; }

            static CellValue blank() { return new CellValue(Kind.BLANK); }
            static CellValue number(double d) { CellValue c = new CellValue(Kind.NUMBER); c.num = d; return c; }
            static CellValue bool(boolean b) { CellValue c = new CellValue(Kind.BOOL); c.bool = b; return c; }
            static CellValue string(String s) { CellValue c = new CellValue(Kind.STRING); c.str = s; return c; }
            static CellValue date(String iso) { CellValue c = new CellValue(Kind.DATE); c.str = iso; return c; }
            static CellValue datetime(String iso) { CellValue c = new CellValue(Kind.DATETIME); c.str = iso; return c; }
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

            // Capture column names from the first non-empty batch if we did not get
            // them from the bind input schema (defensive — http init may differ).
            if (columnNames.isEmpty()) {
                for (Field f : in.getSchema().getFields()) {
                    columnNames.add(f.getName());
                }
            }

            List<Field> fields = in.getSchema().getFields();
            for (int r = 0; r < n; r++) {
                java.util.List<CellValue> row = new java.util.ArrayList<>(fields.size());
                for (int c = 0; c < fields.size(); c++) {
                    row.add(cellValue(in.getVector(c), r));
                }
                rows.add(row);
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

        /** Build the POI workbook from the accumulated data and emit it as a BLOB row. */
        private void emitWorkbook(OutputCollector out) {
            byte[] bytes;
            try (XSSFWorkbook workbook = new XSSFWorkbook();
                 ByteArrayOutputStream bos = new ByteArrayOutputStream()) {
                Sheet sheet = workbook.createSheet(sheetName == null || sheetName.isEmpty() ? "Sheet1" : sheetName);

                Font bold = workbook.createFont();
                bold.setBold(true);
                CellStyle headerStyle = workbook.createCellStyle();
                headerStyle.setFont(bold);

                CreationHelper helper = workbook.getCreationHelper();
                CellStyle dateStyle = workbook.createCellStyle();
                dateStyle.setDataFormat(helper.createDataFormat().getFormat("yyyy-mm-dd hh:mm:ss"));

                int physRow = 0;
                if (header && !columnNames.isEmpty()) {
                    Row hr = sheet.createRow(physRow++);
                    for (int c = 0; c < columnNames.size(); c++) {
                        Cell cell = hr.createCell(c);
                        cell.setCellValue(columnNames.get(c));
                        cell.setCellStyle(headerStyle);
                    }
                }
                for (java.util.List<CellValue> dataRow : rows) {
                    Row row = sheet.createRow(physRow++);
                    for (int c = 0; c < dataRow.size(); c++) {
                        writeCell(row.createCell(c), dataRow.get(c), dateStyle);
                    }
                }

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

        /** Extract a serializable {@link CellValue} from an Arrow vector cell. */
        private static CellValue cellValue(FieldVector v, int row) {
            if (v.isNull(row)) {
                return CellValue.blank();
            }
            if (v instanceof Float8Vector d) {
                return CellValue.number(d.get(row));
            } else if (v instanceof Float4Vector f) {
                return CellValue.number(f.get(row));
            } else if (v instanceof IntVector iv) {
                return CellValue.number(iv.get(row));
            } else if (v instanceof BigIntVector bv) {
                return CellValue.number((double) bv.get(row));
            } else if (v instanceof SmallIntVector sv) {
                return CellValue.number(sv.get(row));
            } else if (v instanceof TinyIntVector tv) {
                return CellValue.number(tv.get(row));
            } else if (v instanceof BitVector bit) {
                return CellValue.bool(bit.get(row) != 0);
            } else if (v instanceof DateDayVector dd) {
                return CellValue.date(java.time.LocalDate.ofEpochDay(dd.get(row)).toString());
            } else if (v instanceof DateMilliVector dm) {
                java.time.LocalDateTime ldt = java.time.LocalDateTime.ofInstant(
                        java.time.Instant.ofEpochMilli(dm.get(row)), java.time.ZoneOffset.UTC);
                return CellValue.datetime(ldt.toString());
            } else if (v instanceof TimeStampVector ts) {
                Object obj = ts.getObject(row);
                if (obj instanceof java.time.LocalDateTime ldt) {
                    return CellValue.datetime(ldt.toString());
                }
                return CellValue.string(String.valueOf(obj));
            } else if (v instanceof VarCharVector s) {
                Object o = s.getObject(row);
                return CellValue.string(o instanceof Text t ? t.toString() : String.valueOf(o));
            } else if (v instanceof VarBinaryVector ignored) {
                return CellValue.string("<binary>");
            } else {
                Object o = v.getObject(row);
                return CellValue.string(o == null ? "" : o.toString());
            }
        }

        /** Write a {@link CellValue} into a POI cell, applying the date style as needed. */
        private static void writeCell(Cell cell, CellValue cv, CellStyle dateStyle) {
            if (cv == null || cv.kind == null) { cell.setBlank(); return; }
            switch (cv.kind) {
                case BLANK -> cell.setBlank();
                case NUMBER -> cell.setCellValue(cv.num);
                case BOOL -> cell.setCellValue(cv.bool);
                case DATE -> {
                    cell.setCellValue(java.time.LocalDate.parse(cv.str));
                    cell.setCellStyle(dateStyle);
                }
                case DATETIME -> {
                    cell.setCellValue(java.time.LocalDateTime.parse(cv.str));
                    cell.setCellStyle(dateStyle);
                }
                default -> cell.setCellValue(cv.str);
            }
        }
    }
}
