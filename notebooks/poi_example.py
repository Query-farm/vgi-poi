"""Spreadsheet presentation; extraction, types and arithmetic stay in SQL."""

import matplotlib.pyplot as plt
from matplotlib.ticker import FuncFormatter
import numpy as np
from vgi_notebooks import finish

SOURCE = "Authored order workbook · VGI Excel v0.4.0 · Synthetic known amounts; pending quantity excluded"


def plot_grain(reconciliation):
    r = reconciliation.iloc[0]
    fig, ax = plt.subplots(figsize=(11, 6.5))
    values = (
        np.array([r.all_numeric_amount_cells, r.known_line_amount, r.grand_total_cell], dtype=float)
        / 100
    )
    bars = ax.barh(
        ["Naive sum of numeric F cells", "Known valid line amounts", "Workbook grand-total cell"],
        values,
        color=["#c57b40", "#267358", "#456fba"],
        height=0.55,
    )
    ax.bar_label(bars, labels=[f"{v:,.2f}" for v in values], padding=6, fontsize=12)
    ax.invert_yaxis()
    ax.set_xlim(0, max(values) * 1.25)
    ax.xaxis.set_major_formatter(FuncFormatter(lambda v, _: f"{v:,.0f}"))
    ax.set_xlabel("Synthetic amount (USD; stored and aggregated as integer cents)")
    ax.grid(axis="y", visible=False)
    finish(
        fig,
        "Summaries inflate a naive column sum",
        "Full worksheet audit · Returns included · Pending amount remains unknown",
        SOURCE,
        name="amount-grain",
    )


def plot_selection(lines, INCLUDE_RETURNS):
    fig, ax = plt.subplots(figsize=(11, 7))
    values = lines.amount_cents.to_numpy(dtype=float) / 100
    labels = [f"{x.order_id} · {x.sku}" for x in lines.itertuples()]
    colors = ["#267358" if v >= 0 else "#c57b40" for v in values]
    bars = ax.barh(labels, values, color=colors, height=0.55)
    for bar, selected in zip(bars, lines.selected, strict=True):
        if not selected:
            bar.set_facecolor("#e9ece9")
            bar.set_edgecolor("#7a847d")
            bar.set_hatch("///")
    for i, (v, selected) in enumerate(zip(values, lines.selected, strict=True)):
        ax.annotate(
            f"{v:,.2f}" + (" · excluded" if not selected else ""),
            (v, i),
            xytext=(7 if v >= 0 else -7, 0),
            textcoords="offset points",
            ha="left" if v >= 0 else "right",
            va="center",
            fontsize=11,
        )
    ax.axvline(0, color="#65766a", lw=1)
    ax.set_xlim(min(values.min(), 0) - 35, max(values.max(), 0) + 18)
    ax.invert_yaxis()
    ax.set_xlabel("Known line amount (USD)")
    ax.grid(axis="y", visible=False)
    finish(
        fig,
        "A return changes the selected amount",
        f"Include returns: {INCLUDE_RETURNS} · Green = nonnegative · Orange = return · Hatching = excluded",
        SOURCE,
        name=f"selected-lines-{INCLUDE_RETURNS}",
    )
