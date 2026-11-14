# VGI POI notebooks

[01 · Separate spreadsheet details from totals](01_separate_spreadsheet_details_from_totals.ipynb) uses the real Apache POI worker to read a synthetic Excel workbook, retain cell types and formulas, separate line items from summary rows, and verify a typed XLSX export by reading it back.

## Run

Install [uv](https://docs.astral.sh/uv/) and Java 21+ on `PATH`. On macOS with Homebrew Java, add `/opt/homebrew/opt/openjdk@21/bin` if needed. The release helper uses POSIX file locks on macOS/Linux. Initial setup needs network access for the Python environment, VGI extension and approximately 53 MB public JAR. All worksheet processing is local.

From the repository root:

```sh
uv sync --project notebooks --frozen
uv run --project notebooks --frozen python notebooks/execute.py
```

Or copy the entire `notebooks/` directory elsewhere, then:

```sh
cd notebooks
uv sync --frozen
uv run --frozen python execute.py
```

In VS Code, select `notebooks/.venv/bin/python` and use the notebook directory as the kernel working directory. For other Jupyter editors, optionally register this environment:

```sh
uv run --project notebooks --frozen python -m ipykernel install --user --name vgi-poi-notebooks --display-name 'Python (vgi-poi notebooks)'
```

Restart and run all after changing the visible parameter. Imports, plotting, validation and cleanup source are folded. Connection and analytical SQL are expanded. The saved notebook embeds both charts and all useful outputs.

## Experiment

```sh
uv run --project notebooks --frozen python notebooks/execute.py \
  --parameters '{"INCLUDE_RETURNS":false}' \
  --output-dir notebooks/validation/no-returns
```

Excluding the negative line changes the selected known amount and exported row count; the original worksheet audit stays fixed. The pending quantity remains unknown, separate from the legitimate zero. Reset `INCLUDE_RETURNS=True` for the saved default.

The runner rejects unknown parameters and requires a separate destination for overrides. It saves outputs only after a successful fresh-kernel run. Experiment copies include their relative assets and runtime companions. Each run overwrites only its own `validation/export/selected-lines.xlsx`; this generated workbook and review PNGs are ignored. To inspect the exported XLSX, run the notebook; it is not a committed asset. The notebook refuses an ambiguous multi-BLOB export.

## Release and fixtures

The visible `ATTACH` uses the [public v0.4.0 JAR](https://github.com/Query-farm/vgi-poi/releases/tag/v0.4.0), SHA-256 `aed3febbcd965485b12aa28e255abff1ff2e8fdf877d1be9bc75f0e088f7ee98`. `release_jar.py` verifies the digest on download and every cached launch, serializes downloads with a bounded lock, publishes atomically, then executes Java. Its cache is `${XDG_CACHE_HOME:-~/.cache}/vgi-notebook-jars/`. No Gradle, worker checkout or sibling repository is required. Upgrade the literal release and digest together, then rerun both parameter settings. The separate notebook environment pins Haybarn 2.0.0rc11.

[build_fixture.py](build_fixture.py) deterministically creates [synthetic-orders.xlsx](assets/synthetic-orders.xlsx) using only Python's standard library. Run `uv run --project notebooks --frozen python notebooks/build_fixture.py` to reproduce it. The fixture has Orders and Notes sheets; six declared line rows, subtotal/grand-total formulas, a pending text quantity, a numeric zero, a blank row and a blank cell at row 30. Every formula cache deliberately contains 999999. POI evaluates the local formulas; the pending quantity produces `#VALUE!`. There are no external workbook links. [not-a-workbook.txt](assets/not-a-workbook.txt) is a deliberate plain-text invalid input.

Row/column coordinates are zero-based while A1 cell references are one-based. Sheet row extent is not a record count. SQL maps this known header/schema explicitly; it is not a general spreadsheet-layout detector. IDs remain text, units and cents are explicitly typed integers, and the approval flag is boolean. A formula's type remains `FORMULA` even when its result is an error. Missing/failed values are not converted to zero.

## Interpretation and export limits

The source grand total covers known lines and excludes the unresolved pending amount. Adding summary cells to detail duplicates amounts. The return switch changes the selected scope and does not establish new activity or resolve unknown data. All amounts and identities are synthetic.

The export contains selected values, not the original formulas, formatting or notes. It verifies exact small integers, leading-zero IDs and booleans. The worker converts numeric Arrow values into Excel numbers; large integers and unsupported types require their own checks. This is not a general lossless serialization claim. The writer emits a workbook-so-far per input batch; this small fixture yields one BLOB, which is explicitly verified. Do not select an arbitrary BLOB from a multi-batch result. The reader needs literal document arguments, so the notebook saves the bytes locally and rereads a literal path.

A missing sheet and invalid input can both return zero cell rows. Format detection and the sheet inventory distinguish these cases. External workbook links are not resolved by the worker; this example tests only local formulas. It does not benchmark arbitrary formula support, XLS legacy files, scale or workbook round-trip fidelity.

Validation covers both parameter settings, a detached directory with an empty release cache, four reviewed charts, thirteen notebook consistency checks and runner guards. Source checks passed 14 JUnit tests and 75 stdio / 81 HTTP / 75 Unix SQL assertions across three cases per transport, with zero skips. Executed metadata checks scored 100/100 L2 with zero findings. Default execution took 17.0 seconds and the detached no-returns experiment with an empty release cache took 6.3 seconds. The first chart is byte-identical across settings; selected rows change from five to four and known amounts from 99.00 to 111.50 USD. Notebook execution exclusively uses the published JAR. Original worker source, build files and committed SQL fixture bytes are preserved.

Official Query.Farm Strata Sun and VGI artwork is copied unchanged from the approved notebook assets and linked at the top and bottom.
