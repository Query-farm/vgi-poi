package farm.query.vgi.poi;

import farm.query.vgi.protocol.FunctionExample;

/**
 * Self-contained, runnable example payloads.
 *
 * <p>The {@code vgi-lint} strict profile <em>executes</em> every per-function
 * example and every {@code vgi.executable_examples} entry against the attached
 * worker. The reader functions ({@code read_excel}, {@code excel_sheets}) take
 * only constant arguments, so a real, data-returning example must embed a
 * workbook inline rather than a file path or a subquery.
 *
 * <p>{@link #XLSX_B64} is a hand-minimised two-row {@code .xlsx} workbook (sheet
 * {@code "Data"}: A1=1, B1="alpha", A2=2, B2="beta"; only the five required OOXML
 * parts, inline strings, no styles), base64-encoded. At ~2.1k characters it keeps
 * a tag that carries one inline-workbook example under vgi-lint's 4,000-character
 * agent-context limit (VGI417); the old POI-written workbook spelled as
 * {@code \x..} hex was ~11k characters. Base64 has no backslashes or quotes, so
 * the same SQL is valid inside a JSON tag and a {@code withExamples} entry.
 */
final class Examples {

    private Examples() {}

    /** Base64 of a minimal two-row .xlsx workbook. */
    static final String XLSX_B64 =
            "UEsDBBQAAAAIAAAAIVxuYbgN/gAAAC0CAAATAAAAW0NvbnRlbnRfVHlwZXNdLnhtbK2RzU7DMBCEX8Xy"
            + "tYqdckAIJe2BnyNwKA+w2JvEiv/kdUv69jhp4YAKXDit7JnZb2Q328lZdsBEJviWr0XNGXoVtPF9y193"
            + "j9UNZ5TBa7DBY8uPSHy7aXbHiMRK1lPLh5zjrZSkBnRAIkT0RelCcpDLMfUyghqhR3lV19dSBZ/R5yrP"
            + "O/imuccO9jazh6lcn3oktMTZ3ck4s1oOMVqjIBddHrz+RqnOBFGSi4cGE2lVDFxeJMzKz4Bz7rk8TDIa"
            + "2Quk/ASuuORk5XtI41sIo/h9yYWWoeuMQh3U3pWIoJgQNA2I2VmxTOHA+NXf/MVMchnrfy7ytf+zh1y+"
            + "e/MBUEsDBBQAAAAIAAAAIVyY2uuLrgAAACcBAAALAAAAX3JlbHMvLnJlbHONz8EOgjAMBuBXWXqXgQdj"
            + "DIOLMeFq8AHmVgYB1mWbCm/vjmI8eGz69/vTsl7miT3Rh4GsgCLLgaFVpAdrBNzay+4ILERptZzIooAV"
            + "A9RVecVJxnQS+sEFlgwbBPQxuhPnQfU4y5CRQ5s2HflZxjR6w51UozTI93l+4P7TgK3JGi3AN7oA1q4O"
            + "/7Gp6waFZ1KPGW38UfGVSLL0BqOAZeIv8uOdaMwSCrwq+ebB6g1QSwMEFAAAAAgAAAAhXIpWKgC6AAAA"
            + "GQEAAA8AAAB4bC93b3JrYm9vay54bWyNj01uwkAMha8y8r5MYIFQlIRNhMQeDuBmHDIiY0f2AO3tmZay"
            + "78p/ep/fa/ZfaXZ3UovCLaxXFTjiQULkSwvn0+FjB84ycsBZmFr4JoN91zxEr58iV1fkbC1MOS+19zZM"
            + "lNBWshCXyyiaMJdRL94WJQw2EeU0+01VbX3CyPAi1PofhoxjHKiX4ZaI8wuiNGMu5m2Ki0HX/H6wv+oY"
            + "UzHdY8YS42dzDCUlOK1jafQY1uC7xr9F/p2rewJQSwMEFAAAAAgAAAAhXFr9gmuxAAAAKAEAABoAAAB4"
            + "bC9fcmVscy93b3JrYm9vay54bWwucmVsc43PyQrCQAwG4FcZcrdpPYhIp15E6FXqAwzTdKGdhcm49O0d"
            + "PIgFD55C8pMvpDw+zSzuFHh0VkKR5SDIateOtpdwbc6bPQiOyrZqdpYkLMRwrMoLzSqmFR5GzyIZliUM"
            + "MfoDIuuBjOLMebIp6VwwKqY29OiVnlRPuM3zHYZvA9amqFsJoW4LEM3i6R/bdd2o6eT0zZCNP07gw4WJ"
            + "B6KYUBV6ihI+I8Z3KbKkAlYlrj6sXlBLAwQUAAAACAAAACFcl6oyWswAAABWAQAAGAAAAHhsL3dvcmtz"
            + "aGVldHMvc2hlZXQxLnhtbHWQXU7EMAyErxL5nXWbhxVCSVYgtBcADmBas43IT5VYXbg9aYUqhOBtPJ75"
            + "ZNmcPmJQC5fqc7LQHzpQnIY8+nSx8PJ8vrkFVYXSSCEntvDJFU7OXHN5rxOzqNZP1cIkMt8h1mHiSPWQ"
            + "Z05t85ZLJGljuWCdC9O4lWJA3XVHjOQTOLN5jyTkTMlXVdodzR1Wcb+qxfUGF2dw+HYfelBiwafgEz9J"
            + "aRlfnRFHYZ7IoLTsaqwFbMidq3eu3rj6F1f/w31l+ROLP07H/SfuC1BLAQIUAxQAAAAIAAAAIVxuYbgN"
            + "/gAAAC0CAAATAAAAAAAAAAAAAACAAQAAAABbQ29udGVudF9UeXBlc10ueG1sUEsBAhQDFAAAAAgAAAAh"
            + "XJja64uuAAAAJwEAAAsAAAAAAAAAAAAAAIABLwEAAF9yZWxzLy5yZWxzUEsBAhQDFAAAAAgAAAAhXIpW"
            + "KgC6AAAAGQEAAA8AAAAAAAAAAAAAAIABBgIAAHhsL3dvcmtib29rLnhtbFBLAQIUAxQAAAAIAAAAIVxa"
            + "/YJrsQAAACgBAAAaAAAAAAAAAAAAAACAAe0CAAB4bC9fcmVscy93b3JrYm9vay54bWwucmVsc1BLAQIU"
            + "AxQAAAAIAAAAIVyXqjJazAAAAFYBAAAYAAAAAAAAAAAAAACAAdYDAAB4bC93b3Jrc2hlZXRzL3NoZWV0"
            + "MS54bWxQSwUGAAAAAAUABQBFAQAA2AQAAAAA";

    /** {@link #XLSX_B64} as a DuckDB {@code BLOB} expression for example SQL. */
    static final String XLSX = "from_base64('" + XLSX_B64 + "')";

    /** The committed two-sheet fixture (Numbers: 6 cells incl. formula C1=A1+B1; Other: 1 cell). */
    static final String SAMPLE_XLSX = "'test/sql/data/sample.xlsx'";

    // ---- Composed examples: several of this worker's objects in one query. ----
    // Each is defined once here and referenced from the schema-level list and the
    // example list of the object that drives it. They use the repo's committed
    // fixtures (paths resolve against the worker's CWD = repo root), because
    // inline workbook bytes would have to be spelled twice per query.

    /** excel_sheets LEFT JOIN read_excel: per-sheet dimensions vs. cell/formula counts. */
    static final FunctionExample SHEET_AUDIT = Meta.example(
            "SELECT s.idx, s.name, s.rows, s.cols, count(c.cell_ref) AS cells, "
                    + "count(c.formula) AS formulas "
                    + "FROM excel.main.excel_sheets(" + SAMPLE_XLSX + ") AS s "
                    + "LEFT JOIN excel.main.read_excel(" + SAMPLE_XLSX + ") AS c ON c.sheet = s.name "
                    + "GROUP BY s.idx, s.name, s.rows, s.cols ORDER BY s.idx;",
            "Audit a workbook sheet by sheet: join the worksheet directory to its cells to "
                    + "report each sheet's used-range size next to how many non-blank cells and "
                    + "formula cells it holds (sample.xlsx: Numbers 2x3 with 6 cells / 1 formula, "
                    + "Other 1x1 with 1 cell / 0 formulas).");

    /** read_blob + detect_excel + the formats view: classify uploads by content. */
    static final FunctionExample UPLOAD_TRIAGE = Meta.example(
            "SELECT parse_filename(b.filename) AS file, "
                    + "excel.main.detect_excel(b.content) AS fmt, f.readable "
                    + "FROM read_blob('test/sql/data/*') AS b "
                    + "LEFT JOIN excel.main.formats AS f "
                    + "ON f.format = excel.main.detect_excel(b.content) ORDER BY file;",
            "Triage a directory of files by content, not extension: detect each file's Excel "
                    + "format and look it up in the formats view to see whether read_excel can "
                    + "read it (sample.xls -> xls, sample.xlsx -> xlsx, both readable; the "
                    + "non-Excel garbage.bin gets NULL).");

    /** read_excel feeding write_xlsx, checked with detect_excel: legacy .xls formula audit. */
    static final FunctionExample FORMULA_EXPORT = Meta.example(
            "SELECT octet_length(xlsx) > 0 AS produced, excel.main.detect_excel(xlsx) AS fmt "
                    + "FROM excel.main.write_xlsx((SELECT sheet, cell_ref, formula, value "
                    + "FROM excel.main.read_excel('test/sql/data/sample.xls') "
                    + "WHERE formula IS NOT NULL ORDER BY sheet, cell_ref), "
                    + "sheet_name := 'Formulas', header := true);",
            "Build a formula-audit deliverable from a legacy .xls: read its formula cells "
                    + "(sheet, cell, formula text, evaluated value) and export them to a new "
                    + ".xlsx workbook with a header row, confirming the output is detected as "
                    + "'xlsx'.");
}
