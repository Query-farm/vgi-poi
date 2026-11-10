<p align="center">
  <img src="https://raw.githubusercontent.com/Query-farm/vgi/main/docs/vgi-logo.png" alt="Vector Gateway Interface (VGI)" width="320">
</p>

<p align="center"><em>A <a href="https://query.farm">Query.Farm</a> VGI worker for DuckDB.</em></p>

# Read & Write Excel Workbooks (Formulas, Types) in DuckDB

> **vgi-poi** · a [Query.Farm](https://query.farm) VGI worker · powered by Apache POI

[![test](https://github.com/Query-farm/vgi-poi/actions/workflows/test.yml/badge.svg)](https://github.com/Query-farm/vgi-poi/actions/workflows/test.yml)

A [VGI](https://query.farm) worker that brings [Apache POI](https://poi.apache.org/)
into DuckDB/SQL: **read** rich Excel data (every cell's value, type, and formula,
across multiple sheets, for both `.xls` and `.xlsx`) and **write** a query result
back out as an `.xlsx` workbook BLOB — all as SQL table and scalar functions.

Written in **Java** because Apache POI is the reference spreadsheet stack with no
equal in any other language: it reads the legacy OLE2 `.xls` (HSSF) and the OOXML
`.xlsx` (XSSF) formats, evaluates formulas, and writes typed workbooks.

```sql
INSTALL vgi FROM community; LOAD vgi;
ATTACH 'excel' (TYPE vgi, LOCATION 'java -jar vgi-poi-v0.3.0.jar');

-- every non-blank cell of a workbook, with type + evaluated formula value
SELECT sheet, cell_ref, value, type, formula
FROM excel.read_excel('/data/book.xlsx');

-- the worksheet directory
SELECT * FROM excel.excel_sheets('/data/book.xlsx');

-- serialize a query result into an .xlsx BLOB
SELECT octet_length(xlsx)
FROM excel.write_xlsx((SELECT id, name, score FROM results));
```

## Quick start

Every [GitHub release](https://github.com/Query-farm/vgi-poi/releases) ships a self-contained worker jar. It needs Java 21 or newer. Download it:

```sh
curl -LO https://github.com/Query-farm/vgi-poi/releases/download/v0.3.0/vgi-poi-v0.3.0.jar
```

then attach it from DuckDB (run from the directory holding the jar, or use its absolute path):

```sql
INSTALL vgi FROM community;
LOAD vgi;

ATTACH 'excel' AS excel (TYPE vgi, LOCATION 'java -jar vgi-poi-v0.3.0.jar');

-- Read a workbook's non-blank cells (passed as inline bytes).
SELECT sheet, cell_ref, value, type FROM excel.main.read_excel(from_base64('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')) ORDER BY cell_ref;

-- Export a query result to an .xlsx workbook BLOB and confirm bytes were produced.
SELECT octet_length(xlsx) > 0 AS produced FROM excel.main.write_xlsx((SELECT 1 AS id, 'Ada' AS name), sheet_name := 'People');
```

### Other ways to run it

- **Docker** (the image bundles the worker and a Java runtime):

  ```sql
  ATTACH 'excel' AS excel (TYPE vgi, LOCATION 'docker://ghcr.io/query-farm/vgi-poi:0.3.0');
  ```

### Build from source

To build the worker jar yourself, see [Build](#build).

## How POI maps onto SQL

| Area | SQL surface | VGI primitive |
| --- | --- | --- |
| **Read cells** | `SELECT * FROM excel.read_excel(path \| bytes, sheet := NULL)` | table function (1 doc → N cell rows) |
| **Sheet directory** | `SELECT * FROM excel.excel_sheets(path \| bytes)` | table function (1 doc → N sheet rows) |
| **Write a workbook** | `SELECT xlsx FROM excel.write_xlsx((SELECT ...))` | table-in-out (a relation → one BLOB row) |
| **Detect format** | `excel.detect_excel(bytes)` | scalar (`'xlsx'` / `'xls'` / NULL) |

### Conventions

- **`path` vs `bytes`** — the readers (`read_excel`, `excel_sheets`) are overloaded:
  pass a `VARCHAR` path (the worker opens the file) **or** a `BLOB`/`BINARY` value
  (bytes travel over Arrow). Prefer `bytes` when the data already lives in a table
  or came from `read_blob()`.
- **Per-row robustness** — a corrupt / non-spreadsheet input yields **no rows**
  (the parse error is logged to stderr) rather than failing the query. A NULL
  document argument yields no rows.

## Function catalog

### `read_excel(doc BLOB|VARCHAR, sheet := NULL)`

One row per **non-blank** cell:

```
(sheet VARCHAR, row INT, col INT, cell_ref VARCHAR,
 value VARCHAR, type VARCHAR, formula VARCHAR)
```

- `type` ∈ `{NUMERIC, STRING, BOOLEAN, FORMULA, BLANK, ERROR}`.
- Formula cells carry the formula text in `formula` (without the leading `=`), and
  their `value` is the **evaluated** result (POI `FormulaEvaluator`). External
  workbook links are never resolved — the evaluator falls back to the cached value.
- `row` / `col` are 0-based; `cell_ref` is the A1-style reference (e.g. `B3`).
- Numeric date-formatted cells render as ISO-8601; other numbers drop a trailing
  `.0` (so `3.0` reads as `3`, `3.5` stays `3.5`).
- The `sheet := 'Name'` named argument restricts output to one worksheet.

### `excel_sheets(doc BLOB|VARCHAR)`

The worksheet directory, one row per sheet:

```
(idx INT, name VARCHAR, rows INT, cols INT)
```

### `write_xlsx(TABLE, sheet_name := 'Sheet1', header := true)`

A buffering table-in-out: consume the input relation and emit **one BLOB row**:

```
(xlsx BLOB)
```

Cells are typed from the Arrow column type — numbers → numeric cells, booleans →
boolean cells, date/timestamp columns → date-formatted cells, everything else →
string cells. With `header := true` a bold first row carries the input column
names.

```sql
-- write, then store the workbook
CREATE TABLE export AS
SELECT xlsx FROM excel.write_xlsx((SELECT id, name, created_at FROM users));
```

### `detect_excel(bytes BLOB) -> VARCHAR`

Sniffs a BLOB's magic bytes (and confirms it actually opens) → `'xlsx'`, `'xls'`,
or NULL.

## Build

```sh
./gradlew test          # JUnit tests (real .xls/.xlsx fixtures built in-test via POI)
./gradlew shadowJar     # -> build/libs/vgi-poi-<version>-all.jar  (a runnable fat JAR)
```

Run the worker directly (a VGI `LOCATION` is just a launcher command):

```sh
java -jar build/libs/vgi-poi-*-all.jar                       # stdio transport (default)
java -jar build/libs/vgi-poi-*-all.jar --http --port 8000    # HTTP transport
```

### The VGI Java SDK

The build depends on the VGI Java SDK — `farm.query:vgi` (the worker/catalog API;
pulls in `farm.query:vgirpc` transitively) — which is **published to Maven
Central**. A clean checkout builds and tests with no sibling repos, no
`mavenLocal`, and no composite build. The SDK version is pinned in
`build.gradle.kts`; keep it aligned with the `vgi` DuckDB extension version you
run against.

## Security — spreadsheets are untrusted input

POI has a CVE history, and `.xlsx` is a ZIP container vulnerable to zip-bomb /
"billion laughs" decompression attacks. This worker:

- **Hardens POI's ZIP defenses** at startup (`ZipSecureFile` min-inflate-ratio,
  max entry size, max file count) and caps POI's largest single allocation
  (`IOUtils.setByteArrayMaxOverride`) — see `PoiEngine.hardenZipDefenses()`.
- **Never resolves external workbook links** in formulas (`setIgnoreMissingWorkbooks`).
- **Catches every parse failure per call** → no rows / a logged error, never a
  worker crash.
- **Routes all logging to stderr** (POI fronts on the Log4j 2 API; we bridge
  Log4j → SLF4J → `slf4j-simple` so nothing ever writes to stdout, which is the
  Arrow-IPC channel for a stdio worker).

## Dependencies & licensing

- `org.apache.poi:poi` (HSSF/`.xls`) + `org.apache.poi:poi-ooxml` (XSSF/`.xlsx`) —
  **Apache-2.0** (pulls in xmlbeans, commons-compress, etc., all permissive).
- Worker code is under the [MIT License](LICENSE); all bundled deps are permissive.

### Fat-JAR packaging note (SPI merge)

`poi` (which registers `HSSFWorkbookFactory`) and `poi-ooxml` (which registers
`XSSFWorkbookFactory`) **each** ship a `META-INF/services/...WorkbookProvider`
service file. Shadow's `mergeServiceFiles()` alone collapsed them to a single
entry (only the XSSF factory survived), which silently made every `.xls` read
fail with *"InputStream was neither an OLE2 stream, nor an OOXML stream"*. The
`generateMergedSpi` Gradle task pre-concatenates all service files (stripping the
license-header comment lines that confuse the merger) so **both** workbook
providers register. The manifest also sets `Add-Opens: java.base/java.nio` so a
bare `java -jar` works without the caller passing `--add-opens` (Arrow needs it).

## Testing

```sh
make test        # JUnit (./gradlew test) + SQL E2E
make test-unit   # JUnit only
make test-sql    # fat JAR + regenerate fixtures + haybarn-unittest over test/sql/*
```

The SQL E2E suite (`test/sql/*.test`) runs the real functions inside DuckDB via
[`haybarn-unittest`](https://pypi.org/project/haybarn-unittest/) with the fat JAR
as the VGI `LOCATION`. Install the runner (haybarn **2.0**; this worker speaks VGI protocol `vgi.v3` / 3.2) once with `uv tool install --prerelease allow 'haybarn-unittest>=2.0.0rc11,<2.1'`
and put `~/.local/bin` on `PATH`. Fixtures under `test/sql/data/` (a two-sheet
`.xlsx` and `.xls` with a formula, plus a garbage blob) are reproducible from the
JUnit POI builders via `make fixtures` (Gradle `generateSqlFixtures`).

> Note: under `haybarn-unittest`, `require vgi` *skips* the file — the `.test`
> files use an explicit `LOAD vgi;` instead.

## Limitations

- **`write_xlsx` streaming.** The VGI table-in-out transport delivers input
  batches until the input stream closes; DuckDB's `vgi` extension provides no
  separate finalize callback and no guaranteed terminal empty batch. So
  `write_xlsx` emits the workbook-so-far after each input batch: a **single-batch**
  input (the common case) produces exactly one BLOB row of the whole relation; an
  input large enough to span multiple Arrow batches (> ~2048 rows) produces one
  row per batch, the last of which is the complete workbook.
- **No SQL write→read round trip.** The VGI `read_excel` table function only
  accepts a *literal* document argument (DuckDB forbids subqueries / lateral
  column params in a table-function argument), so you cannot pipe a `write_xlsx`
  BLOB straight back into `read_excel` in one statement. The byte-level round trip
  is covered by the JUnit suite.

---

## Authorship & License

Written by [Query.Farm](https://query.farm).

Copyright 2026 Query Farm LLC - https://query.farm

