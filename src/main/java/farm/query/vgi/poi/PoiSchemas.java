package farm.query.vgi.poi;

import farm.query.vgi.types.Schemas;
import org.apache.arrow.memory.BufferAllocator;
import org.apache.arrow.vector.IntVector;
import org.apache.arrow.vector.VarCharVector;
import org.apache.arrow.vector.VectorSchemaRoot;
import org.apache.arrow.vector.types.pojo.ArrowType;
import org.apache.arrow.vector.types.pojo.Field;
import org.apache.arrow.vector.types.pojo.FieldType;
import org.apache.arrow.vector.types.pojo.Schema;
import org.apache.arrow.vector.util.Text;

import java.util.List;
import java.util.Map;

/**
 * Shared Arrow schemas + batch-builders for the reader functions
 * ({@code read_excel}, {@code excel_sheets}).
 */
public final class PoiSchemas {

    private PoiSchemas() {}

    /** read_excel output: one row per non-blank cell. */
    public static final Schema READ_EXCEL_SCHEMA = new Schema(List.of(
            commented("sheet", Schemas.UTF8, "Worksheet name the cell belongs to."),
            commented("row", Schemas.INT32, "0-based row index of the cell."),
            commented("col", Schemas.INT32, "0-based column index of the cell."),
            commented("cell_ref", Schemas.UTF8, "A1-style cell reference (e.g. B3)."),
            commented("value", Schemas.UTF8, "Cell value as text (numbers without a trailing .0, dates "
                    + "ISO-8601, booleans true/false); for formulas, the evaluated result."),
            commented("type", Schemas.UTF8, "Cell type: NUMERIC, STRING, BOOLEAN, FORMULA, or ERROR "
                    + "(blank cells are skipped)."),
            commented("formula", Schemas.UTF8, "Formula text for FORMULA cells (without '='), else NULL.")));

    /** excel_sheets output: one row per worksheet. */
    public static final Schema SHEETS_SCHEMA = new Schema(List.of(
            commented("idx", Schemas.INT32, "0-based sheet ordinal within the workbook."),
            commented("name", Schemas.UTF8, "Worksheet name."),
            commented("rows", Schemas.INT32, "Number of rows (last row index + 1)."),
            commented("cols", Schemas.INT32, "Maximum column count across the sheet's rows.")));

    /** write_xlsx output: a single BLOB row carrying the workbook bytes. */
    public static final Schema WRITE_XLSX_SCHEMA = new Schema(List.of(
            commented("xlsx", Schemas.BINARY, "The generated .xlsx workbook as a BLOB.")));

    static Field commented(String name, ArrowType type, String comment) {
        return new Field(name, new FieldType(true, type, null, Map.of("comment", comment)), null);
    }

    // ---- batch builders ----------------------------------------------------

    static VectorSchemaRoot cellsBatch(BufferAllocator alloc, List<PoiEngine.CellRow> cells) {
        VectorSchemaRoot root = VectorSchemaRoot.create(READ_EXCEL_SCHEMA, alloc);
        root.allocateNew();
        for (int i = 0; i < cells.size(); i++) {
            PoiEngine.CellRow c = cells.get(i);
            setUtf8(root, "sheet", i, c.sheet());
            setInt(root, "row", i, c.row());
            setInt(root, "col", i, c.col());
            setUtf8(root, "cell_ref", i, c.cellRef());
            setUtf8(root, "value", i, c.value());
            setUtf8(root, "type", i, c.type());
            setUtf8(root, "formula", i, c.formula());
        }
        root.setRowCount(cells.size());
        return root;
    }

    static VectorSchemaRoot sheetsBatch(BufferAllocator alloc, List<PoiEngine.SheetInfo> sheets) {
        VectorSchemaRoot root = VectorSchemaRoot.create(SHEETS_SCHEMA, alloc);
        root.allocateNew();
        for (int i = 0; i < sheets.size(); i++) {
            PoiEngine.SheetInfo s = sheets.get(i);
            setInt(root, "idx", i, s.idx());
            setUtf8(root, "name", i, s.name());
            setInt(root, "rows", i, s.rows());
            setInt(root, "cols", i, s.cols());
        }
        root.setRowCount(sheets.size());
        return root;
    }

    static void setUtf8(VectorSchemaRoot root, String col, int row, String value) {
        VarCharVector v = (VarCharVector) root.getVector(col);
        if (value == null) {
            v.setNull(row);
        } else {
            v.setSafe(row, new Text(value));
        }
    }

    static void setInt(VectorSchemaRoot root, String col, int row, Integer value) {
        IntVector v = (IntVector) root.getVector(col);
        if (value == null) {
            v.setNull(row);
        } else {
            v.setSafe(row, value);
        }
    }
}
