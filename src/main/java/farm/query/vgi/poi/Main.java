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
        t.put(
                "vgi.description_llm",
                "Read and write Microsoft Excel workbooks from SQL via Apache POI. Open a .xls "
                        + "(legacy OLE2/HSSF) or .xlsx (OOXML/XSSF) file or BLOB and stream every "
                        + "non-blank cell with its sheet, A1 reference, evaluated value, type, and "
                        + "formula text; list a workbook's worksheets with their row/column counts; "
                        + "serialize a query result into a downloadable .xlsx BLOB; and sniff bytes "
                        + "to tell whether they are an .xls, .xlsx, or neither. Use for ingesting "
                        + "spreadsheet data, inspecting workbook structure, and exporting query "
                        + "results to Excel.");
        t.put(
                "vgi.description_md",
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
        return t;
    }

    private static java.util.Map<String, String> schemaTags() {
        var t = new java.util.LinkedHashMap<String, String>();
        t.put(
                "vgi.description_llm",
                "Excel spreadsheet functions: read a workbook's cells (read_excel), list its "
                        + "worksheets (excel_sheets), write a relation to an .xlsx BLOB "
                        + "(write_xlsx), and detect whether bytes are an Excel file (detect_excel). "
                        + "Handles both .xls and .xlsx via Apache POI.");
        t.put(
                "vgi.description_md",
                "Excel read/write functions over Apache POI — cell-level reads, worksheet "
                        + "listing, .xlsx export, and format detection for `.xls` and `.xlsx`.");
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
