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
import farm.query.vgirpc.CallContext;
import farm.query.vgirpc.OutputCollector;
import farm.query.vgirpc.wire.Allocators;
import org.apache.arrow.vector.VectorSchemaRoot;
import org.apache.arrow.vector.types.pojo.ArrowType;

import java.nio.file.Path;
import java.util.List;

/**
 * {@code excel.excel_sheets(path | bytes)} — list the worksheets of a workbook
 * with their ordinal, name, row count, and column count. One row per sheet.
 */
public final class ExcelSheetsFunction implements TableFunction {

    @Override public String name() { return "excel_sheets"; }

    @Override public FunctionMetadata metadata() {
        return FunctionMetadata.describe(
                        "List the worksheets of an Excel workbook (.xls or .xlsx) with index, name, "
                                + "row count, and column count (Apache POI).")
                .withCategories("spreadsheet", "excel", "poi")
                .withTag("vgi.example_queries",
                        "[{\"sql\":\"SELECT idx, name, rows, cols FROM excel.main.excel_sheets('"
                                + Examples.XLSX_BLOB + "'::BLOB) ORDER BY idx;\",\"description\":\"List "
                                + "every worksheet of a workbook (inline bytes) with its dimensions.\"}]")
                .withExamples(Meta.examples(
                        Meta.example(
                                "SELECT idx, name, rows, cols FROM excel.main.excel_sheets('"
                                        + Examples.XLSX_BLOB + "'::BLOB) ORDER BY idx;",
                                "List every worksheet of a workbook (passed as inline bytes) with "
                                        + "its dimensions.")))
                .withTag("vgi.result_columns_schema",
                        "[{\"name\":\"idx\",\"type\":\"INTEGER\",\"description\":\"0-based sheet ordinal "
                                + "within the workbook.\"},"
                                + "{\"name\":\"name\",\"type\":\"VARCHAR\",\"description\":\"Worksheet "
                                + "name.\"},"
                                + "{\"name\":\"rows\",\"type\":\"INTEGER\",\"description\":\"Number of rows "
                                + "(last row index + 1).\"},"
                                + "{\"name\":\"cols\",\"type\":\"INTEGER\",\"description\":\"Maximum column "
                                + "count across the sheet's rows.\"}]")
                .withTags(Meta.objectTags(
                        "List Excel Worksheets",
                        "## excel_sheets\n\n"
                                + "Returns the **worksheet directory** of an Excel workbook — one row "
                                + "per sheet — without reading any cell data. Accepts a `.xlsx` or "
                                + "legacy `.xls` file **path** *or* the raw workbook **bytes** as a "
                                + "`BLOB`.\n\n"
                                + "Each row gives the sheet's 0-based ordinal (`idx`), its `name`, the "
                                + "`rows` count (last row index + 1) and the maximum `cols` count "
                                + "across the sheet. Use it to discover sheet names before calling "
                                + "`read_excel(..., sheet := '<name>')`, or to gauge a workbook's size "
                                + "and shape cheaply.\n\n"
                                + "A corrupt or non-spreadsheet input yields **zero rows** (logged to "
                                + "stderr) rather than an error. Table-function arguments must be "
                                + "**literals** — pass bytes as a `BLOB` literal, not a subquery.",
                        "Lists the worksheets of a Microsoft Excel workbook (`.xls` or `.xlsx`), "
                                + "returning each sheet's ordinal, name, row count, and column "
                                + "count.\n\n"
                                + "**Usage**: give it a filesystem path to an `.xls`/`.xlsx` file, or "
                                + "the raw workbook bytes as a `BLOB` literal, and it returns one row "
                                + "per worksheet ordered by ordinal. Runnable, fully-qualified examples "
                                + "are attached to this object's metadata.\n\n"
                                + "**Notes**: a lightweight companion to `read_excel` for discovering "
                                + "sheet names and dimensions; supports both `.xls` (HSSF) and `.xlsx` "
                                + "(XSSF); inputs may be a file path or raw bytes.",
                        "excel sheets, worksheets, list sheets, sheet names, workbook directory, "
                                + "tabs, spreadsheet, xlsx, xls, apache poi, sheet count, "
                                + "sheet dimensions",
                        "ExcelSheetsFunction.java"))
                .withTag("vgi.category", "Reading");
    }

    @Override public List<ArgSpec> argumentSpecs() {
        // ArgSpec canonical ctor: (name, position, arrowType, doc, isConst,
        // hasDefault, defaultValue, typeBound, varargs, anyType, tableInput).
        return List.of(
                new ArgSpec(
                        "doc", 0, new ArrowType.Null(),
                        "The Excel workbook whose worksheets to list, given as either a "
                                + "filesystem path to an .xls/.xlsx file or the raw workbook "
                                + "bytes. Must be a literal value; cell data is not read.",
                        false, false, "", List.of(), false, true, false));
    }

    @Override public BindResponse onBind(TableBindParams p) {
        return BindResponse.forSchema(SchemaUtil.serializeSchema(PoiSchemas.SHEETS_SCHEMA));
    }

    @Override public long cardinality(TableBindParams p) { return 16L; }

    @Override public TableProducerState createProducer(TableInitParams params) {
        Arguments a = params.arguments();
        Object docValue = a.positionalAt(0);
        ArrowType docType = a.positionalTypeAt(0);
        return new State(DocInput.fromArgument(docValue, docType));
    }

    public static final class State extends TableProducerState {
        public byte[] bytes;
        public String path;
        public boolean done;

        public State() {}

        State(DocInput input) {
            this.bytes = input.bytes();
            this.path = input.path() == null ? null : input.path().toString();
        }

        @Override public void produceTick(OutputCollector out, CallContext ctx) {
            if (done) { out.finish(); return; }
            done = true;

            if ((bytes == null || bytes.length == 0) && path == null) {
                out.finish();
                return;
            }

            Path p = path == null ? null : Path.of(path);
            List<PoiEngine.SheetInfo> sheets;
            try {
                sheets = PoiEngine.sheets(bytes, p);
            } catch (Throwable t) {
                System.err.println("excel_sheets: failed to parse workbook: " + t);
                out.finish();
                return;
            }

            VectorSchemaRoot root = PoiSchemas.sheetsBatch(Allocators.root(), sheets);
            out.emit(root);
            out.finish();
        }
    }
}
