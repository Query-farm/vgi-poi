package farm.query.vgi.poi;

import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Writes the committed SQL E2E fixtures under {@code test/sql/data/} using the
 * same in-memory POI builders the JUnit tests use, so the fixtures are
 * reproducible from source rather than opaque committed binaries that can drift.
 *
 * <p>Usage: {@code SqlFixtureGenerator <output-dir>} (defaults to test/sql/data).
 */
public final class SqlFixtureGenerator {

    private SqlFixtureGenerator() {}

    public static void main(String[] args) throws Exception {
        Path dir = Path.of(args.length > 0 ? args[0] : "test/sql/data");
        Files.createDirectories(dir);

        // sample.xlsx / sample.xls — two sheets, typed cells, a formula (=A1+B1).
        Files.write(dir.resolve("sample.xlsx"), Fixtures.xlsx());
        Files.write(dir.resolve("sample.xls"), Fixtures.xls());

        // garbage.bin — bytes that are neither .xls nor .xlsx (NULL/no-rows case).
        Files.write(dir.resolve("garbage.bin"), Fixtures.garbage());

        System.out.println("Wrote SQL fixtures to " + dir.toAbsolutePath());
    }
}
