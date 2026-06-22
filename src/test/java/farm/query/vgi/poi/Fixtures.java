package farm.query.vgi.poi;

import org.apache.poi.hssf.usermodel.HSSFWorkbook;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;

import java.io.ByteArrayOutputStream;
import java.util.Date;

/**
 * Generates tiny, real .xlsx / .xls workbooks in-memory for the read/write tests.
 *
 * <p>Both builders lay down the SAME known cells so a single set of assertions
 * covers HSSF (.xls) and XSSF (.xlsx):
 * <ul>
 *   <li>Sheet "Numbers": A1=2 (numeric), B1=3 (numeric), C1==A1+B1 (formula -> 5),
 *       A2="hello" (string), B2=TRUE (boolean), C2=a date.</li>
 *   <li>Sheet "Other": A1="second sheet".</li>
 * </ul>
 */
final class Fixtures {

    private Fixtures() {}

    static byte[] xlsx() {
        return build(new XSSFWorkbook());
    }

    static byte[] xls() {
        return build(new HSSFWorkbook());
    }

    private static byte[] build(Workbook wb) {
        try (wb; ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            Sheet s1 = wb.createSheet("Numbers");
            Row r0 = s1.createRow(0);
            r0.createCell(0).setCellValue(2.0);     // A1 numeric
            r0.createCell(1).setCellValue(3.0);     // B1 numeric
            r0.createCell(2).setCellFormula("A1+B1"); // C1 formula -> 5

            Row r1 = s1.createRow(1);
            r1.createCell(0).setCellValue("hello"); // A2 string
            r1.createCell(1).setCellValue(true);    // B2 boolean
            Cell dateCell = r1.createCell(2);       // C2 date
            org.apache.poi.ss.usermodel.CellStyle dateStyle = wb.createCellStyle();
            dateStyle.setDataFormat(wb.getCreationHelper().createDataFormat().getFormat("yyyy-mm-dd"));
            dateCell.setCellValue(new Date(0L));    // 1970-01-01
            dateCell.setCellStyle(dateStyle);

            Sheet s2 = wb.createSheet("Other");
            s2.createRow(0).createCell(0).setCellValue("second sheet");

            wb.write(out);
            return out.toByteArray();
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    /** Random-looking bytes with no recognizable workbook signature. */
    static byte[] garbage() {
        byte[] b = new byte[512];
        for (int i = 0; i < b.length; i++) {
            b[i] = (byte) ((i * 37 + 11) & 0xFF);
        }
        return b;
    }

    /** A ZIP magic header followed by truncated garbage — looks like .xlsx, isn't. */
    static byte[] truncatedXlsx() {
        return new byte[] {'P', 'K', 3, 4, 0x14, 0, 0, 0, 8, 0, 'n', 'o', 'p', 'e'};
    }
}
