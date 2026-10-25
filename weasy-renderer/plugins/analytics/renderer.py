"""Print aggregate insights without accepting remote resources or executable HTML."""
from datetime import date, datetime, timezone
from html import escape
from typing import Literal

from pydantic import BaseModel, Field, model_validator


class Selection(BaseModel):
    resourceId: str = Field(min_length=1, max_length=32)
    filter: Literal['ALL', 'CAMPAIGN', 'PAGE']
    selector: str = Field(default='', max_length=500)


class Metric(BaseModel):
    key: str = Field(max_length=80)
    label: str = Field(max_length=100)
    value: float | None = Field(default=None, allow_inf_nan=False, ge=-1e18, le=1e18)
    unit: Literal['NUMBER', 'PERCENT', 'CURRENCY', 'BYTES'] = 'NUMBER'


class Snapshot(BaseModel):
    from_: date = Field(alias='from')
    to: date
    metrics: list[Metric] = Field(min_length=1, max_length=12)
    currency: str | None = Field(default=None, pattern=r'^[A-Z]{3}$')
    notes: list[str] = Field(default_factory=list, max_length=8)
    syncedAt: datetime

    @model_validator(mode='after')
    def bounded(self):
        if self.to < self.from_ or (self.to - self.from_).days > 30 or any(len(note) > 800 for note in self.notes):
            raise ValueError('Use bounded reporting dates and notes')
        return self


class Link(BaseModel):
    label: str = Field(min_length=1, max_length=120)
    selection: Selection
    snapshot: Snapshot


class AnalyticsPdf(BaseModel):
    title: str = Field(max_length=150)
    organization: str = Field(max_length=200)
    contextName: str = Field(max_length=200)
    contextKind: Literal['PROJECT', 'CLIENT']
    provider: str = Field(max_length=100)
    generatedAt: datetime
    links: list[Link] = Field(min_length=1, max_length=30)


def number(value):
    return f'{value:,.2f}'.rstrip('0').rstrip('.')


def metric_value(metric, currency):
    if metric.value is None:
        return 'Unavailable'
    text = number(metric.value)
    if metric.unit == 'PERCENT':
        return text + '%'
    if metric.unit == 'CURRENCY':
        return text + ' ' + (currency or '(currency unavailable)')
    if metric.unit == 'BYTES':
        return text + ' bytes'
    return text


def report_html(value):
    report = AnalyticsPdf.model_validate(value)
    generated = report.generatedAt.astimezone(timezone.utc).strftime('%Y-%m-%d %H:%M UTC')
    pages = []
    for index, link in enumerate(report.links):
        snapshot = link.snapshot
        scope = 'Entire resource' if link.selection.filter == 'ALL' else ('Campaign: ' if link.selection.filter == 'CAMPAIGN' else 'Page path: ') + link.selection.selector
        rows = ''.join(f'<tr><th scope="row">{escape(metric.label)}</th><td>{escape(metric_value(metric, snapshot.currency))}</td></tr>' for metric in snapshot.metrics)
        notes = ''.join(f'<li>{escape(note)}</li>' for note in snapshot.notes)
        pages.append(f'''<section class="snapshot" id="analytics-snapshot-{index}">
            <div class="masthead">{escape(report.organization)}<span>ANALYTICS / {escape(report.contextKind)}</span></div>
            <p class="eyebrow">{escape(report.provider)} · {index + 1} of {len(report.links)} linked snapshots</p>
            <h1>{escape(report.title)}</h1><p class="context">{escape(report.contextName)}</p>
            <div class="scope"><h2>{escape(link.label)}</h2><p><b>Resource</b> {escape(link.selection.resourceId)}</p><p><b>Scope</b> {escape(scope)}</p></div>
            <div class="period"><strong>{snapshot.from_} — {snapshot.to}</strong><span>Refreshed {snapshot.syncedAt.astimezone(timezone.utc).strftime('%Y-%m-%d %H:%M UTC')}</span></div>
            <table><thead><tr><th>Metric</th><th>Value</th></tr></thead><tbody>{rows}</tbody></table>
            <aside><h3>Source and reporting notes</h3><ul>{notes}</ul><p>Saved snapshot export. No new provider query was made. Providers, overlapping scopes and different currencies are not combined. Unavailable values are not treated as zero.</p></aside>
            <p class="exported">Exported {escape(generated)} · Internal client/project insights</p>
        </section>''')
    return '''<!doctype html><html><head><meta charset="utf-8"><style>
        @page {size:A4;margin:18mm 20mm 20mm;@bottom-left{content:"Internal analytics insights";font:8pt "Liberation Sans";color:#617186}@bottom-right{content:"Page " counter(page) " of " counter(pages);font:8pt "Liberation Sans";color:#617186}}
        body{font:10pt "Liberation Sans",sans-serif;color:#172d48;line-height:1.5}*{box-sizing:border-box}
        .snapshot{break-before:page}.snapshot:first-child{break-before:auto}.masthead{font-size:10pt;font-weight:bold;color:#1767bd;border-bottom:2px solid #1767bd;padding-bottom:4mm}.masthead span{float:right;font-size:8pt;color:#617186}
        .eyebrow{font-size:8pt;color:#617186;text-transform:uppercase;letter-spacing:.6pt;margin:8mm 0 2mm}h1{font-size:25pt;line-height:1.2;margin:0}h2{font-size:14pt;margin:0 0 3mm}h3{font-size:10pt;margin:0 0 2mm}.context{font-size:12pt;margin:3mm 0 6mm}
        .scope{background:#eff5fc;border-left:3px solid #1767bd;padding:4mm 5mm}.scope p{margin:1mm 0;overflow-wrap:anywhere}.scope b{display:inline-block;width:18mm}.period{margin:5mm 0 3mm}.period span{display:block;color:#617186;font-size:8pt;margin-top:1mm}
        table{width:100%;border-collapse:collapse;table-layout:fixed}thead{display:table-header-group}th,td{text-align:left;border-bottom:1px solid #dce5ef;padding:3mm 4mm;overflow-wrap:anywhere}thead th{background:#172d48;color:white;font-size:9pt}tbody th{font-weight:normal}td:last-child,thead th:last-child{text-align:right;font-weight:bold}tr{break-inside:avoid}tbody tr:nth-child(even){background:#f6f9fc}
        aside{margin-top:6mm;color:#52657a;font-size:8pt}aside ul{padding-left:4mm;margin:0}aside li{margin-bottom:2mm}.exported{font-size:7.5pt;color:#617186;margin-top:6mm}
    </style></head><body>''' + ''.join(pages) + '</body></html>'


def render(value):
    from resources import inline_only, MAX_PDF_BYTES
    from weasyprint import HTML
    document = HTML(string=report_html(value), url_fetcher=inline_only).render()
    if len(document.pages) > 200:
        raise ValueError('Export exceeds 200 pages')
    result = document.write_pdf()
    if len(result) > MAX_PDF_BYTES:
        raise ValueError('Export exceeds 10 MB')
    return result
