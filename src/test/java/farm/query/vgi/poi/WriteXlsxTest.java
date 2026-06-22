package farm.query.vgi.poi;

import farm.query.vgi.function.Arguments;
import farm.query.vgirpc.AnnotatedBatch;
import farm.query.vgirpc.OutputCollector;
import farm.query.vgirpc.wire.Allocators;
import org.apache.arrow.vector.BitVector;
import org.apache.arrow.vector.Float8Vector;
import org.apache.arrow.vector.VarCharVector;
import org.apache.arrow.vector.VectorSchemaRoot;
import org.apache.arrow.vector.types.pojo.ArrowType;
import org.apache.arrow.vector.types.pojo.Field;
import org.apache.arrow.vector.types.pojo.FieldType;
import org.apache.arrow.vector.types.pojo.Schema;
import org.apache.arrow.vector.util.Text;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellType;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.ss.usermodel.WorkbookFactory;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** write_xlsx round-trip: build a workbook from an input relation, read it back with POI. */
class WriteXlsxTest {

    private static Field f(String name, ArrowType type) {
        return new Field(name, new FieldType(true, type, null), null);
    }

    @Test void roundTripsTypedCellsWithHeader() throws Exception {
        Schema in = new Schema(List.of(
                f("name", new ArrowType.Utf8()),
                f("score", new ArrowType.FloatingPoint(org.apache.arrow.vector.types.FloatingPointPrecision.DOUBLE)),
                f("active", new ArrowType.Bool())));

        byte[] xlsx = runWrite(in, Map.of("sheet_name", "Results", "header", true));
        assertNotNull(xlsx);
        assertTrue(xlsx.length > 0);

        try (Workbook wb = WorkbookFactory.create(new ByteArrayInputStream(xlsx))) {
            assertEquals("Results", wb.getSheetName(0));
            Sheet s = wb.getSheetAt(0);

            // Row 0 = header.
            Row h = s.getRow(0);
            assertEquals("name", h.getCell(0).getStringCellValue());
            assertEquals("score", h.getCell(1).getStringCellValue());
            assertEquals("active", h.getCell(2).getStringCellValue());
            int fontIdx = h.getCell(0).getCellStyle().getFontIndex();
            assertTrue(wb.getFontAt(fontIdx).getBold());

            // Row 1 = ("alice", 9.5, true).
            Row r1 = s.getRow(1);
            assertEquals(CellType.STRING, r1.getCell(0).getCellType());
            assertEquals("alice", r1.getCell(0).getStringCellValue());
            assertEquals(CellType.NUMERIC, r1.getCell(1).getCellType());
            assertEquals(9.5, r1.getCell(1).getNumericCellValue(), 1e-9);
            assertEquals(CellType.BOOLEAN, r1.getCell(2).getCellType());
            assertTrue(r1.getCell(2).getBooleanCellValue());

            // Row 2 = ("bob", 7.0, false).
            Row r2 = s.getRow(2);
            assertEquals("bob", r2.getCell(0).getStringCellValue());
            assertEquals(7.0, r2.getCell(1).getNumericCellValue(), 1e-9);
            assertEquals(false, r2.getCell(2).getBooleanCellValue());
        }
    }

    @Test void noHeaderStartsAtFirstDataRow() throws Exception {
        Schema in = new Schema(List.of(f("name", new ArrowType.Utf8())));
        byte[] xlsx = runWrite(in, Map.of("header", false));
        try (Workbook wb = WorkbookFactory.create(new ByteArrayInputStream(xlsx))) {
            Sheet s = wb.getSheetAt(0);
            assertEquals("alice", s.getRow(0).getCell(0).getStringCellValue());
        }
    }

    /**
     * Drive write_xlsx: one data batch followed by the terminal empty batch
     * (DuckDB's in-out flush signal). Returns the emitted BLOB.
     */
    private byte[] runWrite(Schema inputSchema, Map<String, Object> named) {
        WriteXlsxFunction fn = new WriteXlsxFunction();
        Arguments args = new Arguments(List.of(), named, List.of());
        var init = new farm.query.vgi.tableinout.TableInOutInitParams(
                fn.name(), args, inputSchema, PoiSchemas.WRITE_XLSX_SCHEMA,
                Map.of(), Allocators.root(), null);
        var state = fn.createExchange(init);
        OutputCollector collector = new OutputCollector(PoiSchemas.WRITE_XLSX_SCHEMA, "test", true);

        // A single data batch of two rows: write_xlsx emits the full workbook
        // after each input batch (DuckDB's vgi in-out delivers no terminal empty
        // batch, so the common single-batch input yields exactly one BLOB row).
        try (VectorSchemaRoot data = VectorSchemaRoot.create(inputSchema, Allocators.root())) {
            data.allocateNew();
            fillData(data, inputSchema);
            try (AnnotatedBatch b = new AnnotatedBatch(data, Map.of())) {
                state.onInputBatch(b, collector, null);
            }
        }

        byte[] result = null;
        for (OutputCollector.Entry e : collector.entries()) {
            if (e.isData()) {
                VectorSchemaRoot r = e.root();
                if (r.getRowCount() > 0) {
                    var v = (org.apache.arrow.vector.VarBinaryVector) r.getVector("xlsx");
                    result = v.get(0);
                }
                r.close();
            }
        }
        return result;
    }

    private void fillData(VectorSchemaRoot data, Schema schema) {
        int rows = 2;
        String[] names = {"alice", "bob"};
        double[] scores = {9.5, 7.0};
        boolean[] active = {true, false};
        for (Field field : schema.getFields()) {
            var v = data.getVector(field.getName());
            switch (field.getName()) {
                case "name" -> {
                    VarCharVector cv = (VarCharVector) v;
                    for (int i = 0; i < rows; i++) cv.setSafe(i, new Text(names[i]));
                }
                case "score" -> {
                    Float8Vector cv = (Float8Vector) v;
                    for (int i = 0; i < rows; i++) cv.setSafe(i, scores[i]);
                }
                case "active" -> {
                    BitVector cv = (BitVector) v;
                    for (int i = 0; i < rows; i++) cv.setSafe(i, active[i] ? 1 : 0);
                }
                default -> { /* ignore */ }
            }
        }
        data.setRowCount(rows);
    }
}
