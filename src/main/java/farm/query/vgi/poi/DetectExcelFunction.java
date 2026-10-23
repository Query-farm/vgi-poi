package farm.query.vgi.poi;

import farm.query.vgi.function.Arguments;
import farm.query.vgi.function.FunctionMetadata;
import farm.query.vgi.protocol.FunctionExample;
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
import java.util.List;

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

    /** Shared by withExamples and the generated vgi.example_queries tag (VGI515). */
    private static final List<FunctionExample> EXAMPLES = Meta.examples(
            Meta.example(
                    "SELECT excel.main.detect_excel(" + Examples.XLSX + ") AS fmt;",
                    "Detect the format of a workbook's bytes — returns 'xlsx' for an "
                            + "OOXML workbook."),
            Meta.example(
                    "SELECT excel.main.detect_excel('not a spreadsheet'::BLOB) IS NULL "
                            + "AS not_excel;",
                    "Non-Excel bytes return NULL, so a NULL check tells you the BLOB is "
                            + "not a workbook."));

    @Override public FunctionMetadata metadata() {
        return FunctionMetadata.describe(description())
                .withCategories("spreadsheet", "detection", "poi")
                .withTag("vgi.example_queries", Meta.exampleQueriesJson(EXAMPLES))
                .withExamples(EXAMPLES)
                .withTags(Meta.objectTags(
                        "Detect Excel File Format",
                        "## detect_excel\n\n"
                                + "A scalar that classifies a `BLOB` as `'xlsx'` (a modern OOXML/XSSF "
                                + "workbook), `'xls'` (a legacy OLE2/HSSF workbook), or `NULL`. It is a "
                                + "**two-stage** check, not merely a magic-byte peek: it first matches "
                                + "the leading signature (`PK\\x03\\x04` for OOXML, the OLE2 header "
                                + "`D0 CF 11 E0` for `.xls`), then **actually opens the bytes with "
                                + "Apache POI** to confirm they parse as a spreadsheet. That second "
                                + "stage is why a plain `.zip`, a `.docx`, or a `.pptx` — all of which "
                                + "share the `PK` prefix — correctly return `NULL` instead of a false "
                                + "`'xlsx'`.\n\n"
                                + "`NULL` is returned for every non-workbook input, and the distinct "
                                + "paths that produce it are: the input is `NULL`; the `BLOB` is "
                                + "shorter than the 8-byte minimum; the leading signature matches no "
                                + "known format; or the signature matches but POI fails to open the "
                                + "bytes as a workbook (truncated, corrupt, or a different `PK`/OLE2 "
                                + "format). All of these collapse to a single `NULL` result — the "
                                + "function never raises. Detection is **content-based** and ignores "
                                + "any file name or extension, so it works on bytes that arrived over "
                                + "the wire or from another function. Use it to validate untrusted "
                                + "uploads before calling `read_excel`, to branch on workbook format, "
                                + "or to confirm that `write_xlsx` produced a valid workbook.",
                        "Identifies whether a `BLOB` is a Microsoft Excel workbook by matching its "
                                + "leading signature and then opening it with Apache POI to confirm, "
                                + "returning `'xlsx'`, `'xls'`, or `NULL`. Because it opens the bytes "
                                + "(rather than trusting the `PK`/OLE2 prefix alone), a plain `.zip` or "
                                + "a `.docx` returns `NULL`, not a false `'xlsx'`.\n\n"
                                + "**Usage**: pass it any `BLOB` — a file's bytes loaded with "
                                + "`read_blob`, an upload, or the output of `write_xlsx` — and it "
                                + "classifies the content, so a non-NULL result guards a workbook before "
                                + "you read it. Runnable, fully-qualified examples are attached to this "
                                + "object's metadata.\n\n"
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
                    doc = "The candidate workbook content to inspect. Its leading signature is "
                            + "matched and then the bytes are opened with POI to confirm, "
                            + "classifying the content as an OOXML/.xlsx or legacy-OLE2/.xls "
                            + "workbook; anything else (including NULL or bytes too short to "
                            + "classify) yields NULL.")
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
