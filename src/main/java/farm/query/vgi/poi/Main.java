package farm.query.vgi.poi;

import farm.query.vgi.Worker;

/**
 * VGI worker entry point for Apache POI spreadsheet read/write.
 *
 * <p>Attach from DuckDB with:
 * <pre>{@code
 * ATTACH 'excel' (TYPE vgi, LOCATION 'java -jar vgi-poi-all.jar');
 * SELECT * FROM excel.read_excel('/data/book.xlsx');
 * SELECT length(xlsx) FROM excel.write_xlsx((SELECT * FROM my_table));
 * }</pre>
 */
public final class Main {

    private Main() {}

    public static final String GIT_COMMIT =
            System.getenv("VGI_POI_GIT_COMMIT") != null
                    ? System.getenv("VGI_POI_GIT_COMMIT") : "unknown";

    private static final String REPO = "https://github.com/Query-farm/vgi-poi";

    public static Worker buildWorker() {
        // Harden POI's zip reader against zip-bomb / decompression-ratio attacks
        // before any untrusted .xlsx is opened (see PoiEngine.hardenZipDefenses).
        PoiEngine.hardenZipDefenses();
        return Worker.builder()
                .catalogName("excel")
                .implementationVersion(GIT_COMMIT)
                .catalogComment(
                        "Read and write Microsoft Excel spreadsheets (.xls / .xlsx) as DuckDB SQL "
                                + "functions, backed by Apache POI.")
                .catalogTags(catalogTags())
                .sourceUrl(REPO)
                .schemaComment("main", "Excel spreadsheet read/write functions backed by Apache POI.")
                .schemaTags("main", schemaTags())
                .registerTable(new ReadExcelFunction())
                .registerTable(new ExcelSheetsFunction())
                .registerTableInOut(new WriteXlsxFunction())
                .registerScalar(new DetectExcelFunction());
    }

    private static java.util.Map<String, String> catalogTags() {
        var t = new java.util.LinkedHashMap<String, String>();
        t.put("vgi.title", "Excel Spreadsheet Read & Write");
        t.put(
                "vgi.keywords",
                "[\"excel\",\"xlsx\",\"xls\",\"spreadsheet\",\"workbook\",\"apache poi\","
                        + "\"read excel\",\"write excel\",\"worksheet\",\"cells\",\"formula\","
                        + "\"ooxml\",\"ole2\",\"import\",\"export\",\"blob\"]");
        t.put(
                "vgi.doc_llm",
                "Read and write Microsoft Excel workbooks from SQL via Apache POI. Open a .xls "
                        + "(legacy OLE2/HSSF) or .xlsx (OOXML/XSSF) file or BLOB and stream every "
                        + "non-blank cell with its sheet, A1 reference, evaluated value, type, and "
                        + "formula text; list a workbook's worksheets with their row/column counts; "
                        + "serialize a query result into a downloadable .xlsx BLOB; and sniff bytes "
                        + "to tell whether they are an .xls, .xlsx, or neither. Use for ingesting "
                        + "spreadsheet data, inspecting workbook structure, and exporting query "
                        + "results to Excel.");
        t.put(
                "vgi.doc_md",
                "# Excel Spreadsheet Read & Write in SQL\n\n"
                        + "![Apache POI logo](https://poi.apache.org/images/project-header.png)\n\n"
                        + "**Query Microsoft Excel workbooks (`.xls` and `.xlsx`) directly from "
                        + "DuckDB SQL — read every cell with its value, type, and formula, and "
                        + "export any query result to a downloadable `.xlsx` file — powered by "
                        + "[Apache POI](https://poi.apache.org/).**\n\n"
                        + "This extension turns spreadsheets into first-class SQL data sources. "
                        + "It is built for data engineers, analysts, and application developers who "
                        + "need to ingest Excel files, inspect workbook structure, or hand results "
                        + "back to spreadsheet users — without leaving SQL or writing glue code in "
                        + "Python or a BI tool. Both the legacy binary `.xls` format (OLE2 / HSSF) "
                        + "and the modern Office Open XML `.xlsx` format (OOXML / XSSF) are "
                        + "supported, and inputs may be supplied either as a file path or as raw "
                        + "`BLOB` bytes already loaded into DuckDB.\n\n"
                        + "Under the hood the worker embeds [Apache POI](https://github.com/apache/poi), "
                        + "the mature, battle-tested Java library for reading and writing Microsoft "
                        + "Office file formats. POI parses the workbook, evaluates formulas, and "
                        + "preserves cell typing (numbers, dates, booleans, strings, and errors), "
                        + "while the worker streams the results back to DuckDB over Apache Arrow. "
                        + "Untrusted spreadsheets are opened behind zip-bomb and decompression-ratio "
                        + "defenses, and external workbook links are never resolved, so importing "
                        + "arbitrary `.xlsx` files stays safe. See the "
                        + "[Apache POI spreadsheet documentation](https://poi.apache.org/components/spreadsheet/) "
                        + "and the [HSSF/XSSF quick guide](https://poi.apache.org/components/spreadsheet/quick-guide.html) "
                        + "for format details.\n\n"
                        + "Four SQL functions make up the surface area:\n\n"
                        + "- **`read_excel(doc, sheet := NULL)`** — a table function returning one "
                        + "row per non-blank cell, with its sheet name, A1-style cell reference, "
                        + "evaluated value, data type, and original formula text. Pass an optional "
                        + "`sheet` name to read a single worksheet.\n"
                        + "- **`excel_sheets(doc)`** — a table function that lists a workbook's "
                        + "worksheet directory, including each sheet's index, name, and row/column "
                        + "extents, for quick structural inspection.\n"
                        + "- **`write_xlsx(TABLE, sheet_name := ..., header := ...)`** — a "
                        + "table-in-out function that serializes any relation into an `.xlsx` "
                        + "workbook returned as a `BLOB` you can write to disk or stream to a "
                        + "client.\n"
                        + "- **`detect_excel(bytes)`** — a scalar function that sniffs raw bytes "
                        + "and returns `'xls'`, `'xlsx'`, or `NULL` for content-type detection.\n\n"
                        + "Typical use cases include loading Excel exports into analytical "
                        + "pipelines, auditing the sheets and formulas inside a workbook, and "
                        + "generating polished `.xlsx` deliverables straight from a query. For "
                        + "example: `SELECT sheet, cell_ref, value, type FROM "
                        + "excel.main.read_excel('book.xlsx')` or `SELECT octet_length(xlsx) FROM "
                        + "excel.main.write_xlsx((SELECT * FROM report), sheet_name := 'Report')`.");
        t.put("vgi.author", "Query.Farm");
        t.put("vgi.copyright", "Copyright 2026 Query Farm LLC - https://query.farm");
        t.put("vgi.license", "MIT");
        t.put("vgi.support_contact", REPO + "/issues");
        t.put("vgi.support_policy_url", REPO + "/blob/main/README.md");
        t.put("vgi.executable_examples", executableExamples());
        return t;
    }

    /**
     * VGI509: a JSON list of guaranteed-runnable, self-contained examples the
     * linter executes against the attached worker. Every reference is
     * catalog-qualified and uses inline BLOB literals (reader functions take
     * only literal arguments), so each example runs as written.
     */
    private static String executableExamples() {
        // The BLOB literal contains backslash-x hex escapes; inside a JSON string
        // each backslash must itself be escaped (\\x...) to be valid JSON.
        String blob = Examples.XLSX_BLOB.replace("\\", "\\\\");
        return "["
                + "{\"description\":\"Read a workbook's non-blank cells (passed as inline bytes).\","
                + "\"sql\":\"SELECT sheet, cell_ref, value, type FROM excel.main.read_excel('"
                + blob + "'::BLOB) ORDER BY cell_ref;\"},"
                + "{\"description\":\"List the worksheets of a workbook.\","
                + "\"sql\":\"SELECT idx, name, rows, cols FROM excel.main.excel_sheets('"
                + blob + "'::BLOB) ORDER BY idx;\"},"
                + "{\"description\":\"Detect that the bytes are an .xlsx workbook.\","
                + "\"sql\":\"SELECT excel.main.detect_excel('" + blob + "'::BLOB) AS fmt;\"},"
                + "{\"description\":\"Export a query result to an .xlsx workbook BLOB and confirm "
                + "bytes were produced.\",\"sql\":\"SELECT octet_length(xlsx) > 0 AS produced FROM "
                + "excel.main.write_xlsx((SELECT 1 AS id, 'Ada' AS name), sheet_name := 'People');\"}"
                + "]";
    }

    private static java.util.Map<String, String> schemaTags() {
        var t = new java.util.LinkedHashMap<String, String>();
        t.put("vgi.title", "Excel — main");
        t.put(
                "vgi.keywords",
                "[\"excel\",\"xlsx\",\"xls\",\"read_excel\",\"excel_sheets\",\"write_xlsx\","
                        + "\"detect_excel\",\"spreadsheet\",\"workbook\",\"worksheet\",\"cells\","
                        + "\"formula\",\"apache poi\"]");
        // VGI123 classifying tags — BARE keys (not vgi.-namespaced).
        t.put("domain", "spreadsheet");
        t.put("category", "file-io");
        t.put("topic", "excel-read-write");
        t.put(
                "vgi.doc_llm",
                "Excel spreadsheet functions: read a workbook's cells (read_excel), list its "
                        + "worksheets (excel_sheets), write a relation to an .xlsx BLOB "
                        + "(write_xlsx), and detect whether bytes are an Excel file (detect_excel). "
                        + "Handles both .xls and .xlsx via Apache POI.");
        t.put(
                "vgi.doc_md",
                "Excel read/write functions over Apache POI — cell-level reads, worksheet "
                        + "listing, .xlsx export, and format detection for `.xls` and `.xlsx`. "
                        + "Reader inputs may be a file path or raw `BLOB` bytes.");
        // VGI506: representative catalog-qualified SQL (plain string).
        t.put(
                "vgi.example_queries",
                "SELECT * FROM excel.main.read_excel('book.xlsx');\n"
                        + "SELECT idx, name, rows, cols FROM excel.main.excel_sheets('book.xlsx');\n"
                        + "SELECT octet_length(xlsx) FROM excel.main.write_xlsx("
                        + "(SELECT 1 AS id, 'Ada' AS name), sheet_name := 'People');\n"
                        + "SELECT excel.main.detect_excel((SELECT content FROM read_blob('book.xlsx')));");
        return t;
    }

    public static void main(String[] args) {
        String stderrPath = System.getenv("VGI_WORKER_STDERR");
        if (stderrPath != null && !stderrPath.isEmpty()) {
            try {
                java.io.PrintStream ps = new java.io.PrintStream(
                        new java.io.FileOutputStream(stderrPath, true), true);
                System.setErr(ps);
            } catch (Exception ignore) {
                // best-effort stderr redirect
            }
        }
        buildWorker().runFromArgs(args);
    }
}
