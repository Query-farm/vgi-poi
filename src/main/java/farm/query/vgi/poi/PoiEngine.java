package farm.query.vgi.poi;

import org.apache.poi.openxml4j.util.ZipSecureFile;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellType;
import org.apache.poi.ss.usermodel.CellValue;
import org.apache.poi.ss.usermodel.DateUtil;
import org.apache.poi.ss.usermodel.FormulaError;
import org.apache.poi.ss.usermodel.FormulaEvaluator;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.ss.usermodel.WorkbookFactory;
import org.apache.poi.ss.util.CellReference;
import org.apache.poi.util.IOUtils;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.List;

/**
 * Apache POI integration: open a workbook (.xls via HSSF or .xlsx via XSSF) from
 * bytes or a path, walk cells with their types + formulas, list sheets, and
 * detect the format. All public entry points are static and stateless — POI's
 * {@link WorkbookFactory} infers the format from the stream's magic bytes.
 *
 * <p><b>Security.</b> Spreadsheets are untrusted input (POI has a CVE history and
 * .xlsx is a zip, so it is vulnerable to zip-bomb / "billion laughs" decompression
 * attacks). {@link #hardenZipDefenses()} caps POI's allowed inflation ratio,
 * total expanded entry size, and entry count; {@code IOUtils.setByteArrayMaxOverride}
 * caps any single allocation POI attempts. External-workbook formula links are
 * never resolved (the evaluator returns the cached value / an error instead).
 */
public final class PoiEngine {

    private PoiEngine() {}

    /** Cell types we surface in the {@code type} column. */
    public enum CellKind { NUMERIC, STRING, BOOLEAN, FORMULA, BLANK, ERROR }

    private static volatile boolean hardened = false;

    /**
     * Install POI's zip-bomb defenses. Idempotent; call once at worker startup
     * (and defensively before each open in case a future POI release resets the
     * static state). Conservative limits that still admit large legitimate books.
     */
    public static synchronized void hardenZipDefenses() {
        if (hardened) return;
        // Reject archives whose decompressed size exceeds 100x the compressed
        // size (the classic zip-bomb signature) once they pass a small grace size.
        ZipSecureFile.setMinInflateRatio(0.001d); // 1:1000 max expansion
        // Cap total expanded bytes for a single zip entry (defends a single huge
        // member). 256 MiB is generous for a spreadsheet's largest XML part.
        ZipSecureFile.setMaxEntrySize(256L * 1024 * 1024);
        // Cap the number of entries (members) in the archive.
        ZipSecureFile.setMaxFileCount(50_000);
        // Cap the largest single byte[] POI will allocate while parsing (defends
        // OOXML / HSSF allocation-size attacks). 256 MiB.
        IOUtils.setByteArrayMaxOverride(256 * 1024 * 1024);
        hardened = true;
    }

    /** A single non-blank cell as projected into the read_excel output schema. */
    public record CellRow(
            String sheet, int row, int col, String cellRef,
            String value, String type, String formula) {}

    /** One sheet's directory entry for excel_sheets. */
    public record SheetInfo(int idx, String name, int rows, int cols) {}

    // ---- open --------------------------------------------------------------

    /**
     * Open a workbook from bytes or a path. The caller owns closing it via
     * try-with-resources. Throws on a corrupt / non-spreadsheet input.
     */
    static Workbook open(byte[] bytes, Path path) throws IOException {
        hardenZipDefenses();
        if (path != null) {
            // Always read fully into memory then hand POI a stream: this keeps the
            // path and bytes code-paths identical and avoids POIFS file locks.
            byte[] data = Files.readAllBytes(path);
            return openStream(data);
        }
        if (bytes == null) {
            throw new IOException("no spreadsheet bytes or path supplied");
        }
        return openStream(bytes);
    }

    private static Workbook openStream(byte[] data) throws IOException {
        try (InputStream in = new ByteArrayInputStream(data)) {
            // WorkbookFactory sniffs the magic bytes: OLE2 header -> HSSF (.xls),
            // ZIP/OOXML header -> XSSF (.xlsx). Anything else throws.
            return WorkbookFactory.create(in);
        }
    }

    // ---- read_excel --------------------------------------------------------

    /**
     * Walk every non-blank cell of the workbook (optionally a single sheet),
     * evaluating formulas to fill {@code value}. {@code sheetFilter} == null means
     * all sheets; otherwise only the sheet of that name.
     */
    static List<CellRow> readCells(byte[] bytes, Path path, String sheetFilter) throws IOException {
        List<CellRow> out = new ArrayList<>();
        try (Workbook wb = open(bytes, path)) {
            FormulaEvaluator evaluator = wb.getCreationHelper().createFormulaEvaluator();
            // Never throw on a formula referencing an external (linked) workbook —
            // and never try to resolve it. Fall back to the cached/last value.
            evaluator.setIgnoreMissingWorkbooks(true);

            for (int s = 0; s < wb.getNumberOfSheets(); s++) {
                Sheet sheet = wb.getSheetAt(s);
                if (sheetFilter != null && !sheet.getSheetName().equals(sheetFilter)) {
                    continue;
                }
                String sheetName = sheet.getSheetName();
                for (Row row : sheet) {
                    for (Cell cell : row) {
                        CellRow cr = projectCell(sheetName, cell, evaluator);
                        if (cr != null) out.add(cr);
                    }
                }
            }
        }
        return out;
    }

    /** Project one POI cell into a CellRow, or null to skip a truly blank cell. */
    private static CellRow projectCell(String sheetName, Cell cell, FormulaEvaluator evaluator) {
        CellType type = cell.getCellType();
        if (type == CellType.BLANK) {
            return null; // blank cells are not emitted as rows
        }
        int r = cell.getRowIndex();
        int c = cell.getColumnIndex();
        String ref = new CellReference(r, c).formatAsString();

        String formula = null;
        String value;
        CellKind kind;

        if (type == CellType.FORMULA) {
            kind = CellKind.FORMULA;
            try {
                formula = cell.getCellFormula();
            } catch (Exception e) {
                formula = null;
            }
            // Evaluate to fill `value`. Guard per-cell: a single bad formula must
            // not abort the whole read.
            try {
                CellValue cv = evaluator.evaluate(cell);
                value = formatCellValue(cv);
            } catch (Exception e) {
                value = null;
            }
        } else {
            kind = kindOf(type);
            value = formatRawCell(cell, type);
        }

        return new CellRow(sheetName, r, c, ref, value, kind.name(), formula);
    }

    private static CellKind kindOf(CellType t) {
        return switch (t) {
            case NUMERIC -> CellKind.NUMERIC;
            case STRING -> CellKind.STRING;
            case BOOLEAN -> CellKind.BOOLEAN;
            case ERROR -> CellKind.ERROR;
            case FORMULA -> CellKind.FORMULA;
            default -> CellKind.BLANK;
        };
    }

    /** Render a non-formula cell's value as a string. */
    private static String formatRawCell(Cell cell, CellType type) {
        return switch (type) {
            case STRING -> cell.getStringCellValue();
            case BOOLEAN -> Boolean.toString(cell.getBooleanCellValue());
            case NUMERIC -> formatNumeric(cell);
            case ERROR -> errorString(cell.getErrorCellValue());
            default -> null;
        };
    }

    /** Render an evaluated formula result (a {@link CellValue}) as a string. */
    private static String formatCellValue(CellValue cv) {
        if (cv == null) return null;
        return switch (cv.getCellType()) {
            case STRING -> cv.getStringValue();
            case BOOLEAN -> Boolean.toString(cv.getBooleanValue());
            case NUMERIC -> formatNumber(cv.getNumberValue());
            case ERROR -> errorString(cv.getErrorValue());
            default -> null;
        };
    }

    /** Numeric cells: render dates in ISO-8601, plain numbers without trailing .0. */
    private static String formatNumeric(Cell cell) {
        double d = cell.getNumericCellValue();
        if (DateUtil.isCellDateFormatted(cell)) {
            try {
                SimpleDateFormat fmt = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss");
                return fmt.format(DateUtil.getJavaDate(d));
            } catch (Exception e) {
                return formatNumber(d);
            }
        }
        return formatNumber(d);
    }

    /** Drop a trailing ".0" so 3.0 reads back as "3" but 3.5 stays "3.5". */
    private static String formatNumber(double d) {
        if (d == Math.rint(d) && !Double.isInfinite(d)) {
            return Long.toString((long) d);
        }
        return Double.toString(d);
    }

    private static String errorString(byte errCode) {
        try {
            return FormulaError.forInt(errCode).getString();
        } catch (Exception e) {
            return "#ERR?";
        }
    }

    // ---- excel_sheets ------------------------------------------------------

    static List<SheetInfo> sheets(byte[] bytes, Path path) throws IOException {
        List<SheetInfo> out = new ArrayList<>();
        try (Workbook wb = open(bytes, path)) {
            for (int s = 0; s < wb.getNumberOfSheets(); s++) {
                Sheet sheet = wb.getSheetAt(s);
                int rows = sheet.getLastRowNum() + 1; // 0-based last row index
                int cols = 0;
                for (Row row : sheet) {
                    cols = Math.max(cols, row.getLastCellNum()); // 1-based count
                }
                out.add(new SheetInfo(s, sheet.getSheetName(), Math.max(rows, 0), Math.max(cols, 0)));
            }
        }
        return out;
    }

    // ---- detect_excel ------------------------------------------------------

    /** Return "xlsx", "xls", or null for bytes that are neither. */
    static String detect(byte[] bytes) {
        if (bytes == null || bytes.length < 8) return null;
        // OOXML (.xlsx) is a ZIP: starts with "PK\003\004".
        if (bytes[0] == 'P' && bytes[1] == 'K'
                && bytes[2] == 3 && bytes[3] == 4) {
            // Confirm it actually opens as a spreadsheet (a plain .zip / .docx
            // should not report xlsx).
            return opensAs(bytes) == Format.XLSX ? "xlsx" : null;
        }
        // OLE2 / CFB (.xls) magic: D0 CF 11 E0 A1 B1 1A E1.
        if ((bytes[0] & 0xFF) == 0xD0 && (bytes[1] & 0xFF) == 0xCF
                && (bytes[2] & 0xFF) == 0x11 && (bytes[3] & 0xFF) == 0xE0) {
            return opensAs(bytes) == Format.XLS ? "xls" : null;
        }
        return null;
    }

    private enum Format { XLS, XLSX, NONE }

    private static Format opensAs(byte[] bytes) {
        hardenZipDefenses();
        try (Workbook wb = openStream(bytes)) {
            String cls = wb.getClass().getName();
            if (cls.contains("XSSF")) return Format.XLSX;
            if (cls.contains("HSSF")) return Format.XLS;
            return Format.NONE;
        } catch (Exception | Error e) {
            return Format.NONE;
        }
    }
}
