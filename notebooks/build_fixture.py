"""Build the deterministic, synthetic OOXML workbook used by the notebook."""

from pathlib import Path
from xml.sax.saxutils import escape
from zipfile import ZipFile, ZipInfo, ZIP_DEFLATED


def build(destination: Path) -> None:
    """Write authored cells, formulas and deliberately stale cached results."""

    def string(ref: str, value: str) -> str:
        return f'<c r="{ref}" t="inlineStr"><is><t>{escape(value)}</t></is></c>'

    def number(ref: str, value: int) -> str:
        return f'<c r="{ref}"><v>{value}</v></c>'

    def formula(ref: str, value: str) -> str:
        # Every cache is deliberately wrong. POI should evaluate these local formulas.
        return f'<c r="{ref}"><f>{escape(value)}</f><v>999999</v></c>'

    rows = [
        '<row r="1">'
        + "".join(
            string(f"{col}1", name)
            for col, name in zip(
                "ABCDEFG",
                ["kind", "order_id", "sku", "units", "unit_cents", "amount_cents", "approved"],
                strict=True,
            )
        )
        + "</row>"
    ]
    for row, ident, sku, units, cents in [
        (2, "0001", "MUG", 3, 1250),
        (3, "0002", "TEE", 2, 2200),
        (5, "0003", "MUG", -1, 1250),
        (6, "0004", "POSTER", 4, 750),
        (7, "0005", "SAMPLE", 0, 500),
        (8, "0006", "TEE", "pending", 2200),
    ]:
        qty = number(f"D{row}", units) if isinstance(units, int) else string(f"D{row}", units)
        rows.append(
            f'<row r="{row}">'
            + string(f"A{row}", "line")
            + string(f"B{row}", ident)
            + string(f"C{row}", sku)
            + qty
            + number(f"E{row}", cents)
            + formula(f"F{row}", f"D{row}*E{row}")
            + f'<c r="G{row}" t="b"><v>1</v></c></row>'
        )
    rows.extend(
        [
            '<row r="4">' + string("A4", "subtotal") + formula("F4", "SUM(F2:F3)") + "</row>",
            '<row r="9"><c r="A9"/></row>',
            '<row r="10">'
            + string("A10", "grand_total")
            + formula("F10", "SUM(F2:F3,F5:F7)")
            + "</row>",
            '<row r="11">'
            + string("A11", "note")
            + string("B11", "Synthetic known amounts only; pending quantity excluded.")
            + "</row>",
            '<row r="30"><c r="A30"/></row>',
        ]
    )
    rows.sort(key=lambda r: int(r.split('r="')[1].split('"')[0]))
    ns = "http://schemas.openxmlformats.org/spreadsheetml/2006/main"
    parts = {
        "[Content_Types].xml": '<Types xmlns="http://schemas.openxmlformats.org/package/2006/content-types"><Default Extension="rels" ContentType="application/vnd.openxmlformats-package.relationships+xml"/><Default Extension="xml" ContentType="application/xml"/><Override PartName="/xl/workbook.xml" ContentType="application/vnd.openxmlformats-officedocument.spreadsheetml.sheet.main+xml"/><Override PartName="/xl/worksheets/sheet1.xml" ContentType="application/vnd.openxmlformats-officedocument.spreadsheetml.worksheet+xml"/><Override PartName="/xl/worksheets/sheet2.xml" ContentType="application/vnd.openxmlformats-officedocument.spreadsheetml.worksheet+xml"/></Types>',
        "_rels/.rels": '<Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships"><Relationship Id="rId1" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/officeDocument" Target="xl/workbook.xml"/></Relationships>',
        "xl/workbook.xml": f'<workbook xmlns="{ns}" xmlns:r="http://schemas.openxmlformats.org/officeDocument/2006/relationships"><sheets><sheet name="Orders" sheetId="1" r:id="rId1"/><sheet name="Notes" sheetId="2" r:id="rId2"/></sheets></workbook>',
        "xl/_rels/workbook.xml.rels": '<Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships"><Relationship Id="rId1" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/worksheet" Target="worksheets/sheet1.xml"/><Relationship Id="rId2" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/worksheet" Target="worksheets/sheet2.xml"/></Relationships>',
        "xl/worksheets/sheet1.xml": f'<worksheet xmlns="{ns}"><dimension ref="A1:G30"/><sheetData>'
        + "".join(rows)
        + "</sheetData></worksheet>",
        "xl/worksheets/sheet2.xml": f'<worksheet xmlns="{ns}"><sheetData><row r="1">'
        + string(
            "A1",
            "Authored notebook fixture; cents are integers; cached formula results are deliberately stale.",
        )
        + "</row></sheetData></worksheet>",
    }
    with ZipFile(destination, "w") as z:
        for name, value in parts.items():
            info = ZipInfo(name, date_time=(2026, 1, 1, 0, 0, 0))
            info.compress_type = ZIP_DEFLATED
            z.writestr(info, value)


if __name__ == "__main__":
    build(Path(__file__).resolve().parent / "assets/synthetic-orders.xlsx")
