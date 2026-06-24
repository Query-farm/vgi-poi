package farm.query.vgi.poi;

import farm.query.vgi.function.Arguments;
import farm.query.vgi.function.FunctionMetadata;
import farm.query.vgi.scalar.ScalarFn;
import farm.query.vgi.scalar.Vector;
import farm.query.vgi.types.Schemas;
import org.apache.arrow.vector.FieldVector;
import org.apache.arrow.vector.VarBinaryVector;
import org.apache.arrow.vector.VarCharVector;
import org.apache.arrow.vector.types.pojo.ArrowType;
import org.apache.arrow.vector.types.pojo.Schema;
import org.apache.arrow.vector.util.Text;

import java.nio.charset.StandardCharsets;

/**
 * {@code excel.detect_excel(bytes) -> VARCHAR} — sniff a BLOB and return
 * {@code 'xlsx'}, {@code 'xls'}, or NULL when the bytes are neither an OOXML nor
 * a legacy-OLE2 Excel workbook.
 */
public final class DetectExcelFunction extends ScalarFn {

    @Override public String name() { return "detect_excel"; }

    @Override public String description() {
        return "Detect whether a BLOB is an Excel workbook: returns 'xlsx', 'xls', or NULL (Apache POI).";
    }

    @Override public FunctionMetadata metadata() {
        return FunctionMetadata.describe(description())
                .withCategories("spreadsheet", "detection", "poi")
                .withTag("vgi.example_queries",
                        "[{\"sql\":\"SELECT excel.main.detect_excel((SELECT content FROM "
                                + "read_blob('book.xlsx')));\",\"description\":\"Detect the format of "
                                + "a file's bytes — returns 'xlsx', 'xls', or NULL.\"},"
                                + "{\"sql\":\"SELECT excel.main.detect_excel('not a spreadsheet'::BLOB) "
                                + "IS NULL AS not_excel;\",\"description\":\"Non-Excel bytes return "
                                + "NULL, so a NULL check tells you the BLOB is not a workbook.\"}]");
    }

    /** Always VARCHAR, regardless of the (any-typed) input. */
    @Override protected ArrowType outputType(Schema inputSchema, Arguments args) {
        return Schemas.UTF8;
    }

    public void compute(@Vector(value = "doc", any = true) FieldVector in, VarCharVector out) {
        int n = in.getValueCount();
        for (int i = 0; i < n; i++) {
            if (in.isNull(i)) { out.setNull(i); continue; }
            byte[] bytes = bytesAt(in, i);
            String fmt;
            try {
                fmt = PoiEngine.detect(bytes);
            } catch (Throwable t) {
                fmt = null;
            }
            if (fmt == null) out.setNull(i);
            else out.setSafe(i, new Text(fmt));
        }
    }

    private static byte[] bytesAt(FieldVector in, int i) {
        if (in instanceof VarBinaryVector b) {
            return b.get(i);
        }
        if (in instanceof VarCharVector s) {
            // A VARCHAR arrived (some transports box BLOBs as strings) — take the
            // raw ISO-8859-1 bytes so magic-byte detection still works.
            return s.getObject(i).toString().getBytes(StandardCharsets.ISO_8859_1);
        }
        Object obj = in.getObject(i);
        return obj instanceof byte[] bb ? bb
                : String.valueOf(obj).getBytes(StandardCharsets.ISO_8859_1);
    }
}
