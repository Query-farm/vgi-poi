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
                                + "NULL, so a NULL check tells you the BLOB is not a workbook.\"}]")
                .withExamples(Meta.examples(
                        Meta.example(
                                "SELECT excel.main.detect_excel('" + Examples.XLSX_BLOB
                                        + "'::BLOB) AS fmt;",
                                "Detect the format of a workbook's bytes — returns 'xlsx' for an "
                                        + "OOXML workbook."),
                        Meta.example(
                                "SELECT excel.main.detect_excel('not a spreadsheet'::BLOB) IS NULL "
                                        + "AS not_excel;",
                                "Non-Excel bytes return NULL, so a NULL check tells you the BLOB is "
                                        + "not a workbook.")))
                .withTags(Meta.objectTags(
                        "Detect Excel File Format",
                        "## detect_excel\n\n"
                                + "A scalar that **sniffs the magic bytes** of a `BLOB` and returns "
                                + "`'xlsx'` for a modern OOXML workbook (the ZIP/`PK` container), "
                                + "`'xls'` for a legacy OLE2/HSSF workbook, or `NULL` when the bytes "
                                + "are neither. `NULL` input returns `NULL`.\n\n"
                                + "Detection is **content-based** — it does not look at a file name or "
                                + "extension — so it works on bytes that arrived over the wire or from "
                                + "another function. Use it to validate untrusted uploads before "
                                + "calling `read_excel`, to branch on workbook format, or to confirm "
                                + "that `write_xlsx` produced a valid workbook. A non-Excel BLOB is "
                                + "reported as `NULL` rather than raising an error.",
                        "Identifies whether a `BLOB` is a Microsoft Excel workbook by inspecting its "
                                + "magic bytes, returning `'xlsx'`, `'xls'`, or `NULL`.\n\n"
                                + "**Usage**\n\n"
                                + "```sql\n"
                                + "-- format of a file's bytes\n"
                                + "SELECT excel.main.detect_excel((SELECT content FROM read_blob('book.xlsx')));\n"
                                + "-- guard before reading\n"
                                + "SELECT excel.main.detect_excel(my_blob) IS NOT NULL AS is_excel;\n"
                                + "```\n\n"
                                + "**Notes**: detection is by content, not extension; legacy `.xls` "
                                + "(OLE2) and modern `.xlsx` (OOXML) are distinguished; anything else "
                                + "yields `NULL`.",
                        "detect excel, sniff format, magic bytes, file type, xlsx, xls, "
                                + "ole2, ooxml, is excel, content detection, validate workbook, "
                                + "apache poi",
                        "DetectExcelFunction.java"))
                .withTag("vgi.category", "Detection");
    }

    /** Always VARCHAR, regardless of the (any-typed) input. */
    @Override protected ArrowType outputType(Schema inputSchema, Arguments args) {
        return Schemas.UTF8;
    }

    public void compute(
            @Vector(value = "doc", any = true,
                    doc = "The candidate workbook content to inspect. Its leading magic bytes "
                            + "are sniffed to classify the content as an OOXML/.xlsx or "
                            + "legacy-OLE2/.xls workbook; NULL input yields NULL.")
            FieldVector in,
            VarCharVector out) {
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
