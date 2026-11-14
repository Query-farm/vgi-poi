# Excel Workbooks

[01 · Separate spreadsheet details from totals](01_separate_spreadsheet_details_from_totals.ipynb)
reads an authored order workbook through the Apache POI worker. Visible SQL retains
cell types and formula results, separates detail rows from totals, and exports a
selected relation to XLSX before reading it back. Two SVG charts explain duplicate
counting and the effect of including a return. Pending quantities stay unknown;
numeric zero stays zero.

Open the notebook's **Open in Colab** link, or install [uv](https://docs.astral.sh/uv/)
and run locally:

```sh
cd notebooks
uv sync --locked
uv run --locked vgi-notebooks
```

A notebook opened on its own retrieves verified fixtures, chart code and the release
launcher. The visible connection uses:

```sql
ATTACH 'excel' (TYPE vgi,
  LOCATION 'uv run --frozen --python 3.13 --script poi_release.py v0.4.0');
```

The launcher downloads the public 53 MB JAR and verifies its SHA-256 before each
launch. Its script lock pins an isolated `jdk4py==21.0.8.2` runtime and verifies the
actual Java 21.0.8 binary. No system Java, Gradle build or worker checkout is needed.
Initial setup requires network access; all workbook processing is local. The
launcher supports macOS/Linux, including Colab's Linux environment.

The Colab base package preserves compatible installed kernel and plotting libraries.
The local environment additionally includes the runner; select
`notebooks/.venv/bin/python` in an editor. A fresh preloaded local kernel checks the
standalone bootstrap; a manual hosted-Colab run is not claimed. GitHub shows saved
results and SVG charts, and does not honor executable-cell folding metadata.

## Change the selection policy

The saved default sets `INCLUDE_RETURNS=True`. Change it to `False`, restart and run
all, or preserve the saved notebook with:

```sh
uv run --locked vgi-notebooks --parameters '{"INCLUDE_RETURNS":false}' \
  --output-dir artifacts/no-returns
```

The worksheet audit, formula evaluation and unresolved line stay fixed. Omitting
the negative return increases the selected known amount and changes the exported
rows; it does not create new sales or resolve the pending quantity.

## Fixture and interpretation

[assets/provenance.json](assets/provenance.json) records the synthetic input hashes
and generator. `build_fixture.py` authors the two-sheet OOXML workbook with fixed
ZIP timestamps; no business records or external-workbook links are involved.
Formula caches deliberately contain 999999 so the notebook can verify that POI
actually reevaluates the local formulas. A pending text quantity produces a formula
error instead of zero. The blank row30 demonstrates why a worksheet extent is not
a count of data records.

The `formats` reference explains the difference between an XLSX with a missing sheet
and unrecognized bytes when both readers return no rows. Capability flags describe
supported formats, not proof of file completeness. The example does not demonstrate
legacy XLS reads or external-link fallback.

The export writes only the ignored `artifacts/export/selected-lines.xlsx`. It
contains values rather than the original formulas, styles or notes. The worker
emits a workbook-so-far per input batch; this tiny selection is checked to return
one BLOB, and the save step refuses ambiguous multiple outputs. Thirteen checks
reconcile the row populations, known cents, pending/zero distinctions and reread
values/types. Small integer cents are used; arbitrary large integers and unsupported
types need their own fidelity checks.

All **5/5 named SQL surfaces**, including the writer and `formats` view, are
covered. Complete catalogs, source review, successful cell receipts and narrative
mappings are in [coverage.json](coverage.json) and [coverage/](coverage/). Current
source changes are metadata/examples only; runtime results use public v0.4.0, with
no claim of current-main binary execution. Green source CI is reused for these
notebook-only edits.

The shared [notebook standard](https://github.com/Query-farm/vgi-example-notebooks/blob/main/docs/standard.md)
owns setup, branding, execution and SVG publication.
