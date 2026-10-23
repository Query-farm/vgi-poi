package farm.query.vgi.poi;

import farm.query.vgi.function.ArgSpec;
import farm.query.vgi.function.Arguments;
import farm.query.vgi.function.FunctionMetadata;
import farm.query.vgi.internal.SchemaUtil;
import farm.query.vgi.protocol.BindResponse;
import farm.query.vgi.protocol.FunctionExample;
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

    /** Shared by withExamples and the generated vgi.example_queries tag (VGI515). */
    private static final List<FunctionExample> EXAMPLES = Meta.examples(
            Meta.example(
                    "SELECT sheet, cell_ref, value, type FROM excel.main.read_excel("
                            + Examples.XLSX + ") ORDER BY cell_ref;",
                    "Read every non-blank cell of a workbook (passed as inline bytes) "
                            + "as one row per cell."));

    @Override public FunctionMetadata metadata() {
        return FunctionMetadata.describe(
                        "Read every non-blank cell of an Excel workbook (.xls or .xlsx) as rows with "
                                + "type, A1 reference, evaluated value, and formula text (Apache POI).")
                .withCategories("spreadsheet", "excel", "poi")
                .withTag("vgi.example_queries", Meta.exampleQueriesJson(EXAMPLES))
                .withExamples(EXAMPLES)
                .withTag("vgi.result_columns_schema",
                        "[{\"name\":\"sheet\",\"type\":\"VARCHAR\",\"description\":\"Worksheet name the "
                                + "cell belongs to.\"},"
                                + "{\"name\":\"row\",\"type\":\"INTEGER\",\"description\":\"0-based row "
                                + "index of the cell.\"},"
                                + "{\"name\":\"col\",\"type\":\"INTEGER\",\"description\":\"0-based column "
                                + "index of the cell.\"},"
                                + "{\"name\":\"cell_ref\",\"type\":\"VARCHAR\",\"description\":\"A1-style "
                                + "cell reference, e.g. B3.\"},"
                                + "{\"name\":\"value\",\"type\":\"VARCHAR\",\"description\":\"Cell value "
                                + "rendered as text (numbers without a trailing .0, dates as ISO-8601, "
                                + "booleans as true/false); for a formula cell, the evaluated result. "
                                + "Cast to compute on it.\"},"
                                + "{\"name\":\"type\",\"type\":\"VARCHAR\",\"description\":\"Cell type: "
                                + "NUMERIC, STRING, BOOLEAN, FORMULA, or ERROR (blank cells are "
                                + "skipped).\"},"
                                + "{\"name\":\"formula\",\"type\":\"VARCHAR\",\"description\":\"Formula text "
                                + "for FORMULA cells (without a leading =), else NULL.\"}]")
                .withTags(Meta.objectTags(
                        "Read Excel Workbook Cells",
                        "## read_excel\n\n"
                                + "Reads an Excel workbook into SQL as **one row per non-blank cell**. "
                                + "Accepts a `.xlsx` (OOXML/XSSF) or legacy `.xls` (OLE2/HSSF) file "
                                + "**path** *or* the raw workbook **bytes** as a `BLOB`. The optional "
                                + "named argument `sheet := '<name>'` restricts output to a single "
                                + "worksheet. The whole workbook is parsed into memory and returned as "
                                + "a single result set (it buffers rather than incrementally streams), "
                                + "so cardinality scales with the workbook size.\n\n"
                                + "Each row carries the worksheet name, 0-based `row`/`col`, the "
                                + "A1-style `cell_ref` (e.g. `B3`), the cell `value`, the cell `type`, "
                                + "and the raw `formula` text for formula cells. `type` is one of "
                                + "`NUMERIC`, `STRING`, `BOOLEAN`, `FORMULA`, or `ERROR` — **blank "
                                + "cells are skipped entirely**, so `BLANK` never appears.\n\n"
                                + "**`value` is always `VARCHAR` text**, so cast it to work with it "
                                + "numerically or temporally. The rendering rules are: numbers with no "
                                + "fraction drop the trailing `.0` (`3.0` -> `3`); date-formatted "
                                + "numeric cells render as ISO-8601 `yyyy-MM-ddTHH:mm:ss`; booleans "
                                + "render as `true`/`false`; error cells render as the Excel error "
                                + "string (e.g. `#DIV/0!`). For a `FORMULA` cell, `formula` holds the "
                                + "expression text (without a leading `=`) and `value` holds the "
                                + "**evaluated** result rendered by these same rules; external-workbook "
                                + "links are never resolved (the cached value or an error is used).\n\n"
                                + "Use it to ingest spreadsheet data, audit formulas, or flatten a "
                                + "workbook for further SQL. A corrupt or non-spreadsheet input yields "
                                + "**zero rows** (logged to stderr) rather than an error, and a NULL "
                                + "document yields no rows. Table-function arguments must be "
                                + "**literals** — pass bytes as a `BLOB` literal, not a subquery.",
                        "Reads a Microsoft Excel workbook (`.xls` or `.xlsx`) and returns one row "
                                + "for every non-blank cell, including its worksheet, position, "
                                + "A1 reference, evaluated value, type, and (for formula cells) the "
                                + "formula text. `value` is always `VARCHAR` — numbers render without a "
                                + "trailing `.0`, dates as ISO-8601, booleans as `true`/`false` — so "
                                + "cast it (`value::DOUBLE`, `value::TIMESTAMP`) to compute on it. The "
                                + "`type` column is one of `NUMERIC`, `STRING`, `BOOLEAN`, `FORMULA`, "
                                + "or `ERROR`; blank cells are omitted rather than returned.\n\n"
                                + "**Usage**: point it at a filesystem path to an `.xls`/`.xlsx` file, "
                                + "add the named `sheet` argument to restrict output to one worksheet, "
                                + "or hand it the raw workbook bytes as a `BLOB` literal. Runnable, "
                                + "fully-qualified examples are attached to this object's metadata.\n\n"
                                + "**Notes**: formulas are evaluated and the result lands in `value`; "
                                + "both `.xls` (HSSF) and `.xlsx` (XSSF) are supported; a hostile or "
                                + "corrupt input returns no rows instead of failing the scan.",
                        "read excel, read xlsx, read xls, spreadsheet, workbook, cells, "
                                + "worksheet, formula, A1 reference, apache poi, import excel, "
                                + "parse spreadsheet, ingest xlsx",
                        "ReadExcelFunction.java"))
                .withTag("vgi.category", "Reading");
    }

    @Override public List<ArgSpec> argumentSpecs() {
        // Polymorphic doc arg: an any-typed positional so DuckDB binds both a
        // VARCHAR path and a BLOB/BINARY of bytes; disambiguated via DocInput.
        // ArgSpec canonical ctor: (name, position, arrowType, doc, isConst,
        // hasDefault, defaultValue, typeBound, varargs, anyType, tableInput).
        return List.of(
                new ArgSpec(
                        "doc", 0, new ArrowType.Null(),
                        "The Excel workbook to read, given as either a filesystem path to an "
                                + ".xls/.xlsx file or the raw workbook bytes. Must be a literal "
                                + "value; a NULL or empty document yields no rows.",
                        false, false, "", List.of(), false, true, false),
                new ArgSpec(
                        "sheet", -1, Schemas.UTF8,
                        "Optional worksheet name to restrict the read to a single sheet; when "
                                + "omitted or NULL, cells from every worksheet are returned.",
                        true, true, "", List.of(), false, false, false));
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
