"""Structured invoice PDFs. All text is escaped; no external assets or caller HTML."""
from decimal import Decimal
from html import escape
from pydantic import BaseModel, Field, model_validator


class BillingLine(BaseModel):
    description: str = Field(min_length=1, max_length=500)
    quantity: str = Field(max_length=40)
    unit: str = Field(max_length=40)
    net: str = Field(max_length=40)
    tax: str = Field(max_length=40)
    total: str = Field(max_length=40)


class BillingPdf(BaseModel):
    number: str = Field(min_length=1, max_length=100)
    kind: str = Field(pattern="^(INVOICE|QUOTE|PROVISIONAL)$")
    issuer: str = Field(min_length=1, max_length=200)
    issuerAddress: str = Field(max_length=2000)
    vatNumber: str = Field(max_length=100)
    client: str = Field(min_length=1, max_length=200)
    billingAddress: str = Field(max_length=2000)
    billingEmail: str = Field(max_length=254)
    project: str = Field(max_length=200)
    period: str = Field(max_length=100)
    issued: str = Field(max_length=40)
    due: str = Field(max_length=40)
    currency: str = Field(pattern="^[A-Z]{3}$")
    taxPercent: str = Field(max_length=40)
    net: str = Field(max_length=40)
    tax: str = Field(max_length=40)
    total: str = Field(max_length=40)
    lines: list[BillingLine] = Field(min_length=1, max_length=500)

    @model_validator(mode="after")
    def numeric(self):
        values = [self.net, self.tax, self.total, self.taxPercent]
        values += [v for row in self.lines for v in [row.quantity, row.unit, row.net, row.tax, row.total]]
        if any(not Decimal(v).is_finite() for v in values):
            raise ValueError("Invalid amount")
        return self


def billing_html(value):
    pdf = BillingPdf.model_validate(value)
    e = escape
    title = {"INVOICE": "Invoice", "QUOTE": "Quote", "PROVISIONAL": "Provisional statement — not payable"}[pdf.kind]
    rows = "".join(f"<tr><td>{e(row.description)}</td><td>{e(row.quantity)}</td><td>{e(row.unit)}</td><td>{e(row.net)}</td><td>{e(row.tax)}</td><td>{e(row.total)}</td></tr>" for row in pdf.lines)
    return f"""<!doctype html><html lang="en"><head><meta charset="utf-8"><style>
    @page {{size:A4;margin:18mm 14mm;@bottom-right {{content:counter(page) ' / ' counter(pages);font-size:8pt}}}}
    body {{font:10pt 'Liberation Sans',sans-serif;color:#17202a;line-height:1.5}} h1 {{font-size:24pt}} h2 {{font-size:13pt}}
    .address {{white-space:pre-wrap}} table {{border-collapse:collapse;width:100%;font-size:8pt}} thead {{display:table-header-group}}
    th,td {{padding:3mm 1mm;text-align:right;vertical-align:top;overflow-wrap:anywhere}} th:first-child,td:first-child {{text-align:left;width:38%}}
    th {{background:#eee}} tr {{break-inside:avoid}} .totals {{text-align:right;break-inside:avoid}} .muted {{color:#555}}
    </style></head><body><h1>{e(title)}</h1><p>{e(pdf.number)} · {e(pdf.currency)}</p>
    <h2>{e(pdf.issuer)}</h2><p class="address">{e(pdf.issuerAddress)}</p><p>{'VAT registration: '+e(pdf.vatNumber) if pdf.vatNumber else ''}</p>
    <h2>Bill to: {e(pdf.client)}</h2><p class="address">{e(pdf.billingAddress)}</p><p>{e(pdf.billingEmail)}</p>
    <p>Project: {e(pdf.project)}<br>Service period: {e(pdf.period)}<br>Issued: {e(pdf.issued)} · Due: {e(pdf.due)}</p>
    <table><thead><tr><th>Description</th><th>Quantity</th><th>Unit excl. tax</th><th>Net</th><th>VAT</th><th>Total</th></tr></thead><tbody>{rows}</tbody></table>
    <div class="totals"><p>Subtotal excluding tax: {e(pdf.currency)} {e(pdf.net)}<br>VAT ({e(pdf.taxPercent)}%): {e(pdf.tax)}<br><strong>Total: {e(pdf.currency)} {e(pdf.total)}</strong></p></div>
    <p class="muted">{'For review only. This document is not a request for payment and does not reserve billable work.' if pdf.kind != 'INVOICE' else 'Reply to the billing email with the reference in its subject for billing queries.'}</p></body></html>"""


def render(value):
    from weasyprint import HTML
    from resources import inline_only, MAX_PDF_BYTES
    document = HTML(string=billing_html(value), url_fetcher=inline_only).render()
    if not 1 <= len(document.pages) <= 100:
        raise ValueError("Billing document exceeds page limit")
    result = document.write_pdf()
    if len(result) > MAX_PDF_BYTES:
        raise ValueError("Billing document exceeds 10 MB")
    return result
