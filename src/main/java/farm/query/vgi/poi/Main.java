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
                "excel, xlsx, xls, spreadsheet, workbook, apache poi, read excel, write excel, "
                        + "worksheet, cells, formula, ooxml, ole2, import, export, blob");
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
                "# excel\n\nRead and write Microsoft Excel spreadsheets (.xls / .xlsx) from SQL, "
                        + "backed by [Apache POI](https://poi.apache.org/).\n\n"
                        + "- Table `read_excel(doc, sheet := NULL)` — one row per non-blank cell "
                        + "(sheet, A1 ref, value, type, formula).\n"
                        + "- Table `excel_sheets(doc)` — the workbook's worksheet directory.\n"
                        + "- Table-in-out `write_xlsx(TABLE, ...)` — serialize a relation to an "
                        + ".xlsx BLOB.\n"
                        + "- Scalar `detect_excel(bytes)` — `'xls'`, `'xlsx'`, or NULL.\n\n"
                        + "Both legacy `.xls` (HSSF) and modern `.xlsx` (XSSF) formats are "
                        + "supported; inputs may be a file path or raw bytes.");
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
                "excel, xlsx, xls, read_excel, excel_sheets, write_xlsx, detect_excel, "
                        + "spreadsheet, workbook, worksheet, cells, formula, apache poi");
        // VGI123 classifying tags — BARE keys (not vgi.-namespaced).
        t.put("domain", "spreadsheet");
        t.put("category", "file-io");
        t.put("topic", "excel-read-write");
        t.put("vgi.source_url", Meta.sourceUrl("Main.java"));
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
