package farm.query.vgi.poi;

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
}
