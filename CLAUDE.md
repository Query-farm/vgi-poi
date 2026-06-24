# CLAUDE.md — vgi-poi

Contributor/agent notes. User-facing docs live in `README.md`; this is the
"how it's built and where the sharp edges are" companion.

## What this is

A [VGI](https://query.farm) worker (Java) wrapping **Apache POI** to read rich
Excel data (cells, types, formulas, multiple sheets — both `.xls`/HSSF and
`.xlsx`/XSSF) and write a query result to an `.xlsx` BLOB, as DuckDB SQL
functions. Modeled on `vgi-tika`; built with Gradle (Kotlin DSL, JDK 21) into a
shaded fat JAR. Catalog name `excel` (single `main` schema).

## Layout

```
build.gradle.kts / settings.gradle.kts / gradle.properties   Gradle, shadow plugin (com.gradleup.shadow 9.4.2)
src/main/java/farm/query/vgi/poi/
  Main.java                  Worker.builder().catalogName("excel")...registerTable/registerTableInOut/registerScalar
  ReadExcelFunction.java     table fn: read_excel(path|bytes, sheet := NULL) — one row per non-blank cell
  ExcelSheetsFunction.java   table fn: excel_sheets(path|bytes) — worksheet directory
  WriteXlsxFunction.java     table-in-out: write_xlsx(TABLE, sheet_name, header) -> (xlsx BLOB)
  DetectExcelFunction.java   scalar: detect_excel(bytes) -> 'xlsx' | 'xls' | NULL
  PoiEngine.java             the POI integration (open, readCells, sheets, detect, zip-bomb hardening)
  PoiSchemas.java / DocInput.java   Arrow schemas + path-vs-bytes input dispatch
src/test/java/...            JUnit: ReadExcelTest, WriteXlsxTest + Fixtures.java (POI builders) + SqlFixtureGenerator
test/sql/*.test + data/      haybarn-unittest E2E + committed generated fixtures
Makefile                     build / fixtures / test-unit / test-sql / test / clean
```

## Sharp edges (learned the hard way — these were real shipping bugs)

1. **Fat-JAR SPI merge (the big one).** `poi` ships
   `META-INF/services/org.apache.poi.ss.usermodel.WorkbookProvider` listing
   `HSSFWorkbookFactory`; `poi-ooxml` ships the same file listing
   `XSSFWorkbookFactory`. Shadow's `mergeServiceFiles()` collapsed them to a
   single entry (only XSSF survived), so **every `.xls` read failed** at runtime
   with *"InputStream was neither an OLE2 stream, nor an OOXML stream"* — while
   `.xlsx` worked, so it looked fine. Fixed with a `generateMergedSpi` task that
   concatenates all service files (stripping the Apache license-header comment
   lines that confuse the merger). If `.xls` reads return nothing over RPC,
   suspect the SPI merge first. **This passed JUnit and was caught only by E2E.**
2. **`write_xlsx` has no finalize signal.** The VGI table-in-out
   (`TableInOutExchangeState.onInputBatch`) is called per input batch and the
   worker tick loop just ends when the input stream closes — there is **no**
   finalize callback, and DuckDB's `vgi` extension sends **no terminal empty
   batch** (verified: a 2-row input delivers exactly one `n=2` batch and stops; a
   5000-row input delivers 2048+2048+904 and stops). Calling `out.finish()` early
   **deadlocks** when more batches are coming. So `write_xlsx` emits the
   workbook-so-far after *each* batch: single-batch input → exactly one BLOB row;
   multi-batch input → one row per batch, last is complete (documented limitation).
3. **`read_excel` only takes LITERAL args.** DuckDB rejects subqueries / lateral
   column params in a table-function argument (*"Table function ... only supports
   literals as parameters"*), so you cannot pipe a `write_xlsx` BLOB back into
   `read_excel` in SQL. The byte-level round trip lives in the JUnit suite; the
   SQL E2E asserts `write_xlsx` validity via `detect_excel` instead.
4. **Log4j corrupted the Arrow stdio stream (inherited from vgi-tika).** POI
   fronts on the Log4j 2 API; with no provider, Log4j's StatusLogger writes to
   **stdout**, the Arrow-IPC channel for a stdio worker → protocol corruption.
   Fixed by `log4j-to-slf4j` + `slf4j-simple` (stderr). Any dependency that writes
   to stdout breaks a stdio VGI worker.
5. **Zip-bomb / CVE hardening.** Spreadsheets are untrusted; `PoiEngine.hardenZipDefenses()`
   sets `ZipSecureFile` min-inflate-ratio / max-entry-size / max-file-count and
   caps POI's largest allocation. Formula evaluation uses `setIgnoreMissingWorkbooks`
   so external links are never resolved. Every parse is per-call try/caught →
   no rows + stderr log, never a crash.
6. **`Add-Opens: java.base/java.nio`** is baked into the manifest (Arrow needs it);
   the reader doc arg is `any`-typed so a VARCHAR path binds from SQL.
7. **`haybarn-unittest` skips `require vgi`** — `.test` files use explicit
   `LOAD vgi;`. BLOB args to a table function must be **literals** (e.g.
   `'\x50\x4b...'::BLOB`); `read_blob('x')` is a table function, so use
   `(SELECT content FROM read_blob('x'))` when feeding a *scalar* like detect_excel.

## SDK dependency & CI (self-contained via Maven Central)

Depends on `farm.query:vgi:0.5.0` (pulls in `farm.query:vgirpc:0.10.2` transitively;
vgirpc declared explicitly since the code imports `farm.query.vgirpc.*`). **On
Maven Central**, so the build is fully self-contained: no sibling checkout, no
`mavenLocal`, no composite build. `.github/workflows/test.yml` is a single
`build-and-test` job: JUnit + shadowJar + HTTP boot smoke test + `make test-sql`.

The in-process test drivers (`TestSupport`, `WriteXlsxTest`) construct vgi records
directly. vgi 0.4.0's `TableInitParams` ends with `atUnit, atValue, storage`
(append trailing nulls) and `TableInOutInitParams` ends with `storage`; get exact
signatures via `javap -cp <vgi jar> farm.query.vgi.table.TableInitParams`.

## Testing

```sh
./gradlew test    # JUnit (real in-memory .xls/.xlsx via POI: HSSF + XSSF)
make test-sql     # shadowJar + fixtures + haybarn-unittest over test/sql/*
make test         # both
```

`make test-sql` builds `build/libs/vgi-poi-*-all.jar`, sets
`VGI_POI_WORKER="java -jar <abs jar>"`, and runs `haybarn-unittest`. **The SQL
suite is authoritative** — the SPI-merge bug above passed JUnit and was caught
only by E2E. Fixtures are reproducible via `make fixtures` (Gradle
`generateSqlFixtures`).

## Packaging

~51 MB shaded JAR (POI 5.3.0 HSSF + XSSF + xmlbeans, all Apache-2.0).
