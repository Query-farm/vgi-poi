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
                        + "## Key concepts\n\n"
                        + "- **Rich, cell-level reads.** A workbook is surfaced not as opaque rows "
                        + "but cell by cell, each with its worksheet, position, A1 reference, "
                        + "evaluated value, data type, and (for formula cells) the underlying "
                        + "formula text — so numbers, dates, booleans, strings, errors, and "
                        + "computed values are all preserved and distinguishable.\n"
                        + "- **Structure before data.** You can inspect a workbook's worksheet "
                        + "directory — names and dimensions — cheaply, without materializing every "
                        + "cell, then drill into a single sheet.\n"
                        + "- **Path or bytes.** Reader inputs may be a filesystem path the worker "
                        + "opens, or raw `BLOB` bytes already loaded into DuckDB, so the same "
                        + "function works on files on disk and on content that arrived over the "
                        + "wire.\n"
                        + "- **Round-tripping to Excel.** Any query result can be serialized back "
                        + "into a downloadable `.xlsx` workbook, and raw bytes can be sniffed to "
                        + "confirm their format.\n\n"
                        + "## When to use it\n\n"
                        + "Reach for this worker when you need to ingest spreadsheet exports into an "
                        + "analytical pipeline, audit the sheets and formulas inside a workbook, "
                        + "validate untrusted uploads by content rather than file extension, or hand "
                        + "polished Excel deliverables back to spreadsheet users — all without "
                        + "leaving SQL or writing glue code in Python or a BI tool. List the schema "
                        + "to discover the exact functions and their arguments.");
        t.put("vgi.author", "Query.Farm");
        t.put("vgi.copyright", "Copyright 2026 Query Farm LLC - https://query.farm");
        t.put("vgi.license", "MIT");
        t.put("vgi.support_contact", REPO + "/issues");
        t.put("vgi.support_policy_url", REPO + "/blob/main/README.md");
        t.put("vgi.executable_examples", executableExamples());
        t.put("vgi.agent_test_tasks", agentTestTasksJson());
        return t;
    }

    /**
     * VGI152/VGI920: a JSON array of analyst tasks {@code vgi-lint simulate} runs
     * against the attached worker to measure how well an agent can actually use it.
     *
     * <p>Each task gives the analyst only a natural-language {@code prompt} that
     * names the committed fixture workbook by its repo-relative path
     * ({@code test/sql/data/sample.xlsx}, which the worker opens with CWD = repo
     * root — the same path the SQL E2E suite uses). Grading compares the analyst's
     * result to a deterministic {@code reference_sql} over that fixture,
     * value-only (column names and row order ignored), so the suite is stable.
     *
     * <p>The fixture's Numbers sheet holds A1=2, B1=3, C1=A1+B1 (evaluates to 5),
     * A2="hello", B2=TRUE, C2=a date (6 non-blank cells); the Other sheet holds
     * A1="second sheet". These known values drive the references below.
     */
    private static String agentTestTasksJson() {
        String path = "test/sql/data/sample.xlsx";
        String has = "You are working with a VGI worker that reads and writes Excel spreadsheets. "
                + "An Excel workbook is available to the worker at the repo-relative path `"
                + path + "`.\n\n";
        var tasks = new java.util.ArrayList<java.util.Map<String, Object>>();
        tasks.add(agentTask(
                "list_sheet_names",
                has + "List the names of every worksheet in that workbook. Return one name per row.",
                "SELECT name FROM excel.main.excel_sheets('" + path + "') ORDER BY idx",
                "The workbook has two worksheets, named 'Numbers' and 'Other'."));
        tasks.add(agentTask(
                "evaluate_formula",
                has + "Cell C1 of the 'Numbers' worksheet contains a formula. Return the single "
                        + "value that formula evaluates to.",
                "SELECT value FROM excel.main.read_excel('" + path + "', sheet := 'Numbers') "
                        + "WHERE cell_ref = 'C1'",
                "C1 is =A1+B1 over A1=2 and B1=3, so it evaluates to 5."));
        tasks.add(agentTask(
                "formula_text",
                has + "Return the raw formula text (without a leading '=') stored in cell C1 of "
                        + "the 'Numbers' worksheet.",
                "SELECT formula FROM excel.main.read_excel('" + path + "', sheet := 'Numbers') "
                        + "WHERE cell_ref = 'C1'",
                "The formula text of C1 is 'A1+B1'."));
        tasks.add(agentTask(
                "count_cells_on_sheet",
                has + "Count how many non-blank cells the 'Numbers' worksheet contains. Return the "
                        + "single count.",
                "SELECT count(*) FROM excel.main.read_excel('" + path + "', sheet := 'Numbers')",
                "The Numbers sheet has 6 non-blank cells (A1, B1, C1, A2, B2, C2)."));
        tasks.add(agentTask(
                "detect_format",
                has + "Determine the file format of that workbook's bytes — is it an .xlsx, an "
                        + ".xls, or neither? Return the single format string.",
                "SELECT excel.main.detect_excel((SELECT content FROM read_blob('" + path + "')))",
                "The bytes are a modern OOXML workbook, so detection returns 'xlsx'."));
        tasks.add(agentTask(
                "export_and_verify",
                has + "Separately, export the one-row relation SELECT 1 AS id, 'Ada' AS name to an "
                        + ".xlsx workbook using this worker, then confirm the produced bytes are a "
                        + "valid Excel workbook. Return the single detected format string.",
                "SELECT excel.main.detect_excel(xlsx) FROM excel.main.write_xlsx("
                        + "(SELECT 1 AS id, 'Ada' AS name))",
                "write_xlsx produces a valid OOXML workbook, so detect_excel returns 'xlsx'."));
        return tasksToJson(tasks);
    }

    private static java.util.Map<String, Object> agentTask(
            String name, String prompt, String referenceSql, String successCriteria) {
        var t = new java.util.LinkedHashMap<String, Object>();
        t.put("name", name);
        t.put("prompt", prompt);
        t.put("reference_sql", referenceSql);
        t.put("success_criteria", successCriteria);
        // Every reference returns a single column, so grade on VALUES only:
        // ignore the analyst's column alias and row order.
        t.put("ignore_column_names", true);
        t.put("unordered", true);
        return t;
    }

    /** Minimal JSON serialization for the agent-test-task list (strings + booleans). */
    private static String tasksToJson(java.util.List<java.util.Map<String, Object>> tasks) {
        var sb = new StringBuilder("[");
        for (int i = 0; i < tasks.size(); i++) {
            if (i > 0) {
                sb.append(',');
            }
            sb.append('{');
            boolean firstKey = true;
            for (var e : tasks.get(i).entrySet()) {
                if (!firstKey) {
                    sb.append(',');
                }
                firstKey = false;
                sb.append('"').append(e.getKey()).append("\":");
                Object v = e.getValue();
                if (v instanceof Boolean) {
                    sb.append(v.toString());
                } else {
                    sb.append('"').append(jsonEscape(v.toString())).append('"');
                }
            }
            sb.append('}');
        }
        return sb.append(']').toString();
    }

    /** Escape a Java string for embedding as a JSON string literal. */
    private static String jsonEscape(String s) {
        var sb = new StringBuilder(s.length() + 16);
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            switch (c) {
                case '"' -> sb.append("\\\"");
                case '\\' -> sb.append("\\\\");
                case '\n' -> sb.append("\\n");
                case '\r' -> sb.append("\\r");
                case '\t' -> sb.append("\\t");
                default -> {
                    if (c < 0x20) {
                        sb.append(String.format("\\u%04x", (int) c));
                    } else {
                        sb.append(c);
                    }
                }
            }
        }
        return sb.toString();
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
                "# Excel functions\n\n"
                        + "Read and write Microsoft Excel workbooks (`.xls` and `.xlsx`) directly "
                        + "in SQL over Apache Arrow, powered by "
                        + "[Apache POI](https://poi.apache.org/).\n\n"
                        + "The functions in this schema cover three areas:\n\n"
                        + "- **Reading** — stream a workbook's non-blank cells as rows (with type, "
                        + "A1 reference, evaluated value, and formula text), and list its "
                        + "worksheet directory with per-sheet dimensions.\n"
                        + "- **Writing** — serialize any SQL relation into a downloadable `.xlsx` "
                        + "workbook returned as a `BLOB`.\n"
                        + "- **Detection** — sniff raw bytes to classify them as `.xlsx`, `.xls`, "
                        + "or neither, without trusting a file name.\n\n"
                        + "Reader inputs are polymorphic: pass a filesystem path the worker opens, "
                        + "or a raw `BLOB` of workbook bytes already loaded into DuckDB. Formula "
                        + "cells are evaluated, cell typing is preserved, and a corrupt or "
                        + "non-spreadsheet input yields no rows (or `NULL`) rather than failing the "
                        + "query.");
        // VGI413: an ordered category registry for the schema; each function
        // carries a matching vgi.category tag so the listing renders as navigable
        // sections (Reading / Writing / Detection).
        t.put(
                "vgi.categories",
                "[{\"name\": \"Reading\", \"description\": \"Read an Excel workbook's cells as "
                        + "rows and list its worksheets, from a file path or raw BLOB bytes.\"},"
                        + "{\"name\": \"Writing\", \"description\": \"Serialize a SQL relation into "
                        + "a downloadable .xlsx workbook BLOB.\"},"
                        + "{\"name\": \"Detection\", \"description\": \"Identify whether raw bytes "
                        + "are an .xlsx, .xls, or non-Excel payload by content.\"}]");
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
