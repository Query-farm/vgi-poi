package farm.query.vgi.poi;

import org.apache.arrow.vector.types.pojo.ArrowType;
import org.apache.arrow.vector.util.Text;

import java.nio.file.Path;

/**
 * Resolves the polymorphic first argument of every reader: a {@code VARCHAR}
 * path (the worker opens the file) <em>or</em> a {@code BLOB}/{@code BINARY}
 * column whose bytes travelled over Arrow.
 */
public record DocInput(byte[] bytes, Path path) {

    /** Construct from a constant scalar argument value + its declared Arrow type. */
    public static DocInput fromArgument(Object value, ArrowType type) {
        if (value == null) {
            return new DocInput(null, null);
        }
        if (value instanceof byte[] b) {
            return new DocInput(b, null);
        }
        if (value instanceof Text t) {
            return new DocInput(null, Path.of(t.toString()));
        }
        if (value instanceof String s) {
            // Binary/blob types may arrive as a String for some transports —
            // honour the declared Arrow type to disambiguate path vs. bytes.
            if (isBinary(type)) {
                return new DocInput(s.getBytes(java.nio.charset.StandardCharsets.ISO_8859_1), null);
            }
            return new DocInput(null, Path.of(s));
        }
        // Fallback: stringify and treat as a path.
        return new DocInput(null, Path.of(value.toString()));
    }

    public boolean isPath() {
        return path != null;
    }

    /** True when neither a path nor bytes were supplied (a NULL argument). */
    public boolean isEmpty() {
        return path == null && (bytes == null || bytes.length == 0);
    }

    private static boolean isBinary(ArrowType type) {
        return type instanceof ArrowType.Binary || type instanceof ArrowType.LargeBinary;
    }
}
