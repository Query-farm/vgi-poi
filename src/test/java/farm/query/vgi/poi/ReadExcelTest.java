package farm.query.vgi.poi;

import farm.query.vgi.function.Arguments;
import org.apache.arrow.vector.types.pojo.ArrowType;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Read-side coverage: PoiEngine, read_excel, excel_sheets, detect_excel — over .xlsx and .xls. */
class ReadExcelTest {

    private static Arguments bytesArg(byte[] doc) {
        return new Arguments(List.of(doc), Map.of(), List.of(new ArrowType.Binary()));
    }

    private static Arguments bytesArg(byte[] doc, Map<String, Object> named) {
        return new Arguments(List.of(doc), named, List.of(new ArrowType.Binary()));
    }

    // ---- PoiEngine core ----------------------------------------------------

    @Test void readsCellsTypesAndFormulaFromXlsx() throws Exception {
        assertReadCells(Fixtures.xlsx());
    }

    @Test void readsCellsTypesAndFormulaFromXls() throws Exception {
        assertReadCells(Fixtures.xls());
    }

    private void assertReadCells(byte[] book) throws Exception {
        List<PoiEngine.CellRow> cells = PoiEngine.readCells(book, null, null);

        // A1 numeric == 2, B1 numeric == 3.
        PoiEngine.CellRow a1 = cellAt(cells, "Numbers", "A1");
        assertEquals("NUMERIC", a1.type());
        assertEquals("2", a1.value());

        // C1 is a formula =A1+B1 that evaluates to 5.
        PoiEngine.CellRow c1 = cellAt(cells, "Numbers", "C1");
        assertEquals("FORMULA", c1.type());
        assertEquals("A1+B1", c1.formula());
        assertEquals("5", c1.value());

        // A2 string, B2 boolean.
        assertEquals("STRING", cellAt(cells, "Numbers", "A2").type());
        assertEquals("hello", cellAt(cells, "Numbers", "A2").value());
        assertEquals("BOOLEAN", cellAt(cells, "Numbers", "B2").type());
        assertEquals("true", cellAt(cells, "Numbers", "B2").value());

        // C2 is a date-formatted numeric cell -> ISO-8601 string.
        PoiEngine.CellRow c2 = cellAt(cells, "Numbers", "C2");
        assertEquals("NUMERIC", c2.type());
        // Epoch (1970-01-01 UTC) renders in the worker's local timezone, which may
        // shift it to 1969-12-31; assert the ISO date shape rather than an exact day.
        assertTrue(c2.value().matches("^(1969|1970)-\\d{2}-\\d{2}T.*"), "date value: " + c2.value());

        // Second sheet's cell is present.
        assertEquals("second sheet", cellAt(cells, "Other", "A1").value());
    }

    @Test void sheetFilterRestrictsToOneSheet() throws Exception {
        List<PoiEngine.CellRow> all = PoiEngine.readCells(Fixtures.xlsx(), null, null);
        List<PoiEngine.CellRow> only = PoiEngine.readCells(Fixtures.xlsx(), null, "Other");
        assertTrue(all.size() > only.size());
        assertTrue(only.stream().allMatch(c -> c.sheet().equals("Other")));
    }

    @Test void listsSheets() throws Exception {
        List<PoiEngine.SheetInfo> sheets = PoiEngine.sheets(Fixtures.xlsx(), null);
        assertEquals(2, sheets.size());
        assertEquals("Numbers", sheets.get(0).name());
        assertEquals(0, sheets.get(0).idx());
        assertEquals(2, sheets.get(0).rows());      // 2 data rows
        assertTrue(sheets.get(0).cols() >= 3);      // A,B,C
        assertEquals("Other", sheets.get(1).name());
    }

    @Test void detectsFormats() {
        assertEquals("xlsx", PoiEngine.detect(Fixtures.xlsx()));
        assertEquals("xls", PoiEngine.detect(Fixtures.xls()));
        assertNull(PoiEngine.detect(Fixtures.garbage()));
        assertNull(PoiEngine.detect(null));
        assertNull(PoiEngine.detect(new byte[] {1, 2, 3}));
    }

    // ---- hostile input -----------------------------------------------------

    @Test void corruptInputThrowsCleanlyInEngine() {
        // The engine surfaces a parse error; the table function turns it into
        // zero rows (asserted below). Here we just confirm it does not hang/crash.
        assertThrowsOrEmpty(Fixtures.truncatedXlsx());
        assertThrowsOrEmpty(Fixtures.garbage());
    }

    private void assertThrowsOrEmpty(byte[] bad) {
        try {
            List<PoiEngine.CellRow> cells = PoiEngine.readCells(bad, null, null);
            // Some garbage may parse to an empty workbook; either way no crash.
            assertNotNull(cells);
        } catch (Exception expected) {
            // Expected: a clean exception, not a JVM crash.
            assertNotNull(expected);
        }
    }

    // ---- read_excel table function ----------------------------------------

    @Test void readExcelFunctionEmitsCellRows() {
        var result = TestSupport.invoke(new ReadExcelFunction(), bytesArg(Fixtures.xlsx()));
        assertEquals(PoiSchemas.READ_EXCEL_SCHEMA, result.schema);
        assertTrue(result.totalRows() >= 6);
        // formula row is present with evaluated value 5.
        assertTrue(result.rows().stream().anyMatch(r ->
                "C1".equals(r.get("cell_ref")) && "5".equals(r.get("value")) && "FORMULA".equals(r.get("type"))));
    }

    @Test void readExcelFunctionSheetArgFilters() {
        var result = TestSupport.invoke(new ReadExcelFunction(),
                bytesArg(Fixtures.xlsx(), Map.of("sheet", "Other")));
        assertTrue(result.rows().stream().allMatch(r -> "Other".equals(r.get("sheet"))));
    }

    @Test void readExcelFunctionGarbageYieldsNoRowsNotCrash() {
        var result = TestSupport.invoke(new ReadExcelFunction(), bytesArg(Fixtures.truncatedXlsx()));
        assertEquals(0, result.totalRows());
    }

    @Test void readExcelFunctionNullInputYieldsNoRows() {
        Arguments nullArg = new Arguments(java.util.Arrays.asList((Object) null), Map.of(),
                List.of(new ArrowType.Binary()));
        var result = TestSupport.invoke(new ReadExcelFunction(), nullArg);
        assertEquals(0, result.totalRows());
    }

    // ---- excel_sheets table function --------------------------------------

    @Test void excelSheetsFunctionListsSheets() {
        var result = TestSupport.invoke(new ExcelSheetsFunction(), bytesArg(Fixtures.xls()));
        assertEquals(PoiSchemas.SHEETS_SCHEMA, result.schema);
        assertEquals(2, result.totalRows());
        assertEquals("Numbers", result.rows().get(0).get("name"));
    }

    // ---- detect_excel scalar (engine-level; the @Vector wiring is exercised E2E) ----

    @Test void detectExcelEngineLevel() {
        assertEquals("xlsx", PoiEngine.detect(Fixtures.xlsx()));
        assertEquals("xls", PoiEngine.detect(Fixtures.xls()));
    }

    // helpers
    private static PoiEngine.CellRow cellAt(List<PoiEngine.CellRow> cells, String sheet, String ref) {
        return cells.stream()
                .filter(c -> c.sheet().equals(sheet) && c.cellRef().equals(ref))
                .findFirst()
                .orElseThrow(() -> new AssertionError("no cell " + sheet + "!" + ref));
    }
}
