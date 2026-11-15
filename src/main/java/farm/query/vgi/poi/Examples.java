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
 * <p>{@link #XLSX_B64} is a hand-minimised one-sheet {@code .xlsx} workbook (sheet
 * {@code "Numbers"}: A1=2, B1=3, C1=A1+B1 (cached value 5), A2="hello"; only the
 * five required OOXML parts with one-letter part names, inline strings, no
 * styles), base64-encoded. At ~1.7k characters a composed example can spell it
 * twice (excel_sheets JOIN read_excel) and stay under the 4,000 characters the
 * agent simulator shows per example; every tag stays under vgi-lint's
 * agent-context limits (VGI417). Base64 has no backslashes or quotes, so the same
 * SQL is valid inside a JSON tag and a {@code withExamples} entry.
 *
 * <p>Examples never name a local file: a path like {@code 'book.xlsx'} or
 * {@code 'test/sql/data/sample.xlsx'} exists only in this repo, so for every user
 * the readers return no rows (VGI516/VGI913). Files read with DuckDB's
 * {@code read_blob()} use URLs pinned to a release tag instead.
 */
final class Examples {

    private Examples() {}

    /** Base64 of a minimal one-sheet .xlsx workbook with a formula cell. */
    static final String XLSX_B64 =
            "UEsDBBQAAAAIAAAAIVxqtbuDxgAAAKEBAAATAAAAW0NvbnRlbnRfVHlwZXNdLnhtbK2Ru27CQBBFf8Xa"
            + "FnkHUVBEtptACxT5gdF6jFfsSzsDJn+ftRMaCiSkVPO4d+4ppvn6TsTV3bvArRpF0gcAm5E8so6JQlGG"
            + "mD1KGfMZEpoLngk26/UWTAxCQWqZM1TX7GjAq5Nqfy9rtjG0KpNjVX3+GmdWqzAlZw1K0eEW+idK/UfQ"
            + "5XLx8GgTr4pBQdccb5Sz7ak6YZYD+hIHk57FtxhxGKyhPpqrLyeaUybseSQS7/RStUcbXlHxn6lTzJel"
            + "f0Bh+Uz3A1BLAwQUAAAACAAAACFcOhZJ7n8AAADjAAAACwAAAF9yZWxzLy5yZWxzjc89CgIxEAXgq4Q5"
            + "wM5iYSFJKhtb8QJDnGyCmx+SEfX2plzFwvLxHh88feaVJJbcQ6xdPdOau4EgUg+I3QVO1KdSOY/Gl5ZI"
            + "RmwLVnI3Whh387zHtjXA6q2pTlcDDdTlVfkfuHgfHR+LuyfO8sP/WgyZ2sJi4DENCtBq/Phk31BLAwQU"
            + "AAAACAAAACFcOZz2P5EAAADiAAAABQAAAHcueG1sjY8xDoMwDEWvEvkANe3QAQFTly69QwDTRMVxZAe1"
            + "xy+Csney///y+3LzFn31Ii/34TlZC6GUXCPaEIi9nSRTWpNJlH1ZpT7RspIfLRAVnvFSVVdkHxPshFr/"
            + "Ycg0xYFuMixMqewQpdmXKMlCzAZdszXYb7rkmVp4LNyTGrjNvI8tnMFpHdfFA3YNHkd4/NV9AVBLAwQU"
            + "AAAACAAAACFcIdiTl4MAAADeAAAAEAAAAF9yZWxzL3cueG1sLnJlbHONz7sKAjEQheFXCXmAncXCQrKp"
            + "bGzFFxji5MJuLmRG1Lc35QoWlocfPjjmShtKqoVjaqxeeSu86CjSTgDsImXkqTYqo/jaM8qYPUBDt2Ig"
            + "OMzzEfre0NbsTXW5Lxq1ur0b/QNX75Ojc3WPTEV++PCsfeVIJAPFHkiGPw1FgzXwdcd+AFBLAwQUAAAA"
            + "CAAAACFc9/D39agAAAARAQAABQAAAGEueG1sVZBdCsIwEISvUvIquDaiD5IutHoDTxBqaor5KcnSenyT"
            + "KlHfZr+dGZYViw+PqJWi6mmNiw3TRNMJIPZaWRm3flIubQYfrKQ0hjvEKSh5W0PWAN/tjmDl6BiKlV0k"
            + "SRTBL1VoWJ1on0Wb1YxcwIwC+g/t3nT/T8+ZDtjWm64WMGTHoTggNZd6Xuo5q6hhozOjU1cKiY8RBaFW"
            + "xngBlIIZfBvg51goX8AXUEsBAhQDFAAAAAgAAAAhXGq1u4PGAAAAoQEAABMAAAAAAAAAAAAAAIABAAAA"
            + "AFtDb250ZW50X1R5cGVzXS54bWxQSwECFAMUAAAACAAAACFcOhZJ7n8AAADjAAAACwAAAAAAAAAAAAAA"
            + "gAH3AAAAX3JlbHMvLnJlbHNQSwECFAMUAAAACAAAACFcOZz2P5EAAADiAAAABQAAAAAAAAAAAAAAgAGf"
            + "AQAAdy54bWxQSwECFAMUAAAACAAAACFcIdiTl4MAAADeAAAAEAAAAAAAAAAAAAAAgAFTAgAAX3JlbHMv"
            + "dy54bWwucmVsc1BLAQIUAxQAAAAIAAAAIVz38Pf1qAAAABEBAAAFAAAAAAAAAAAAAACAAQQDAABhLnht"
            + "bFBLBQYAAAAABQAFAB4BAADPAwAAAAA=";

    /** {@link #XLSX_B64} as a DuckDB {@code BLOB} expression for example SQL. */
    static final String XLSX = "from_base64('" + XLSX_B64 + "')";

    /** The committed test fixtures, served from GitHub at the v0.4.0 release tag. */
    static final String FIXTURES_URL =
            "https://raw.githubusercontent.com/Query-farm/vgi-poi/v0.4.0/test/sql/data/";

    // ---- Composed examples: several of this worker's objects in one query. ----
    // Each is defined once here and referenced from the schema-level list and the
    // example list of the object that drives it.

    /** excel_sheets LEFT JOIN read_excel: per-sheet dimensions vs. cell/formula counts. */
    static final FunctionExample SHEET_AUDIT = Meta.example(
            "SELECT s.idx, s.name, s.rows, s.cols, count(c.cell_ref) AS cells, "
                    + "count(c.formula) AS formulas "
                    + "FROM excel.main.excel_sheets(" + XLSX + ") AS s "
                    + "LEFT JOIN excel.main.read_excel(" + XLSX + ") AS c ON c.sheet = s.name "
                    + "GROUP BY s.idx, s.name, s.rows, s.cols ORDER BY s.idx;",
            "Audit a workbook sheet by sheet: join the worksheet directory to its cells to "
                    + "report each sheet's used-range size next to how many non-blank cells and "
                    + "formula cells it holds (the inline workbook: Numbers 2x3 with 4 cells / "
                    + "1 formula; a blank sheet would show 0 cells).");

    /** read_blob + detect_excel + the formats view: classify uploads by content. */
    static final FunctionExample UPLOAD_TRIAGE = Meta.example(
            "SELECT parse_filename(b.filename) AS file, "
                    + "excel.main.detect_excel(b.content) AS fmt, f.readable "
                    + "FROM read_blob(['" + FIXTURES_URL + "sample.xls', '"
                    + FIXTURES_URL + "sample.xlsx', '" + FIXTURES_URL + "garbage.bin']) AS b "
                    + "LEFT JOIN excel.main.formats AS f "
                    + "ON f.format = excel.main.detect_excel(b.content) ORDER BY file;",
            "Triage a batch of files by content, not extension: detect each file's Excel "
                    + "format and look it up in the formats view to see whether read_excel can "
                    + "read it (sample.xls -> xls, sample.xlsx -> xlsx, both readable; the "
                    + "non-Excel garbage.bin gets NULL). For a local folder, read_blob takes a "
                    + "glob pattern.");

    /** read_excel feeding write_xlsx, checked with detect_excel: formula audit export. */
    static final FunctionExample FORMULA_EXPORT = Meta.example(
            "SELECT octet_length(xlsx) > 0 AS produced, excel.main.detect_excel(xlsx) AS fmt "
                    + "FROM excel.main.write_xlsx((SELECT sheet, cell_ref, formula, value "
                    + "FROM excel.main.read_excel(" + XLSX + ") "
                    + "WHERE formula IS NOT NULL ORDER BY sheet, cell_ref), "
                    + "sheet_name := 'Formulas', header := true);",
            "Build a formula-audit deliverable: read a workbook's formula cells "
                    + "(sheet, cell, formula text, evaluated value) and export them to a new "
                    + ".xlsx workbook with a header row, confirming the output is detected as "
                    + "'xlsx'.");
}
