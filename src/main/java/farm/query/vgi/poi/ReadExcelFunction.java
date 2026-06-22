package farm.query.vgi.poi;

import farm.query.vgi.function.ArgSpec;
import farm.query.vgi.function.Arguments;
import farm.query.vgi.function.FunctionMetadata;
import farm.query.vgi.internal.SchemaUtil;
import farm.query.vgi.protocol.BindResponse;
import farm.query.vgi.table.TableBindParams;
import farm.query.vgi.table.TableFunction;
import farm.query.vgi.table.TableInitParams;
import farm.query.vgi.table.TableProducerState;
import farm.query.vgi.types.Schemas;
import farm.query.vgirpc.CallContext;
import farm.query.vgirpc.OutputCollector;
import farm.query.vgirpc.wire.Allocators;
import org.apache.arrow.vector.VectorSchemaRoot;
import org.apache.arrow.vector.types.pojo.ArrowType;

import java.nio.file.Path;
import java.util.List;

/**
 * {@code excel.read_excel(path | bytes, sheet := NULL)} — rich, cell-level read
 * of an .xls or .xlsx workbook. Emits one row per non-blank cell with its type,
 * A1 reference, evaluated value, and (for formula cells) the formula text.
 *
 * <p>A corrupt / non-spreadsheet input yields zero rows (the error is logged to
 * stderr) rather than crashing the worker. A NULL document argument yields no
 * rows.
 */
public final class ReadExcelFunction implements TableFunction {

    @Override public String name() { return "read_excel"; }

    @Override public FunctionMetadata metadata() {
        return FunctionMetadata.describe(
                        "Read every non-blank cell of an Excel workbook (.xls or .xlsx) as rows with "
                                + "type, A1 reference, evaluated value, and formula text (Apache POI).")
                .withCategories("spreadsheet", "excel", "poi");
    }

    @Override public List<ArgSpec> argumentSpecs() {
        // Polymorphic doc arg: an any-typed positional so DuckDB binds both a
        // VARCHAR path and a BLOB/BINARY of bytes; disambiguated via DocInput.
        return List.of(
                ArgSpec.any("doc", 0, List.of()),
                ArgSpec.named("sheet", Schemas.UTF8, ""));
    }

    @Override public BindResponse onBind(TableBindParams p) {
        return BindResponse.forSchema(SchemaUtil.serializeSchema(PoiSchemas.READ_EXCEL_SCHEMA));
    }

    @Override public long cardinality(TableBindParams p) { return 1024L; }

    @Override public TableProducerState createProducer(TableInitParams params) {
        Arguments a = params.arguments();
        Object docValue = a.positionalAt(0);
        ArrowType docType = a.positionalTypeAt(0);
        DocInput input = DocInput.fromArgument(docValue, docType);
        String sheet = a.namedString("sheet", "");
        return new State(input, sheet == null || sheet.isEmpty() ? null : sheet);
    }

    public static final class State extends TableProducerState {
        public byte[] bytes;
        public String path;
        public String sheet;
        public boolean done;

        public State() {}

        State(DocInput input, String sheet) {
            this.bytes = input.bytes();
            this.path = input.path() == null ? null : input.path().toString();
            this.sheet = sheet;
        }

        @Override public void produceTick(OutputCollector out, CallContext ctx) {
            if (done) { out.finish(); return; }
            done = true;

            // NULL input -> no rows.
            if ((bytes == null || bytes.length == 0) && path == null) {
                out.finish();
                return;
            }

            Path p = path == null ? null : Path.of(path);
            List<PoiEngine.CellRow> cells;
            try {
                cells = PoiEngine.readCells(bytes, p, sheet);
            } catch (Throwable t) {
                // Hostile / corrupt spreadsheet: emit nothing, log to stderr.
                System.err.println("read_excel: failed to parse workbook: " + t);
                out.finish();
                return;
            }

            VectorSchemaRoot root = PoiSchemas.cellsBatch(Allocators.root(), cells);
            out.emit(root);
            out.finish();
        }
    }
}
