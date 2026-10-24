"""Fixed-layout, escaped project reports; no caller-provided HTML, CSS, URLs, or assets."""
from datetime import datetime, timezone
from html import escape
from pydantic import BaseModel, Field, model_validator


class ReportSection(BaseModel):
    title: str = Field(min_length=1, max_length=200)
    columns: list[str] = Field(min_length=1, max_length=8)
    rows: list[list[str]] = Field(default_factory=list, max_length=2000)

    @model_validator(mode="after")
    def bounded_cells(self):
        if any(len(column) > 100 for column in self.columns):
            raise ValueError("Report column too long")
        if any(len(row) != len(self.columns) or any(len(cell) > 4000 for cell in row) for row in self.rows):
            raise ValueError("Invalid report row")
        return self


class ReportDocument(BaseModel):
    title: str = Field(min_length=1, max_length=200)
    organization: str = Field(min_length=1, max_length=100)
    subtitle: str = Field(default="", max_length=500)
    period: str = Field(max_length=100)
    summary: list[str] = Field(default_factory=list, max_length=20)
    sections: list[ReportSection] = Field(min_length=1, max_length=30)

    @model_validator(mode="after")
    def bounded_report(self):
        if sum(len(section.rows) for section in self.sections) > 2000 or any(len(line) > 1000 for line in self.summary):
            raise ValueError("Report exceeds its limits")
        return self


def report_html(value):
    report = ReportDocument.model_validate(value)
    sections = []
    for section in report.sections:
        heading = "".join(f"<th>{escape(column)}</th>" for column in section.columns)
        rows = "".join("<tr>" + "".join(f"<td>{escape(cell)}</td>" for cell in row) + "</tr>" for row in section.rows)
        sections.append(f"<section><h2>{escape(section.title)}</h2><table><thead><tr>{heading}</tr></thead><tbody>{rows}</tbody></table></section>")
    generated = datetime.now(timezone.utc).strftime("%Y-%m-%d %H:%M UTC")
    summary = "".join(f"<li>{escape(line)}</li>" for line in report.summary)
    return f"""<!doctype html><html lang="en"><head><meta charset="utf-8"><style>
    @page {{size:A4; margin:20mm 15mm 20mm; @bottom-left {{content:'Internal project report';font-size:8pt;color:#666}}
      @bottom-right {{content:counter(page) ' / ' counter(pages);font-size:8pt;color:#666}}}}
    body {{font:10pt 'DejaVu Sans',sans-serif;color:#17202a;line-height:1.5}}
    .cover {{break-after:page;padding-top:35mm}} .eyebrow {{font-size:12pt;color:#52616b}}
    h1 {{font-size:30pt;line-height:1.15;margin:12mm 0 6mm}} h2 {{font-size:16pt;break-after:avoid;margin-top:12mm}}
    .subtitle {{font-size:14pt}} .muted {{color:#666}} ul {{padding-left:5mm}} li {{margin:3mm 0}}
    table {{width:100%;border-collapse:collapse;table-layout:fixed;font-size:8pt}} thead {{display:table-header-group}}
    th {{text-align:left;background:#eef1f3}} th,td {{padding:3mm 2mm;vertical-align:top;overflow-wrap:anywhere;white-space:pre-wrap}}
    tr {{break-inside:avoid}} tbody tr:nth-child(even) {{background:#f7f8f9}}
    </style></head><body><div class="cover"><p class="eyebrow">{escape(report.organization)}</p><h1>{escape(report.title)}</h1>
    <p class="subtitle">{escape(report.subtitle)}</p><p>{escape(report.period)}</p><ul>{summary}</ul>
    <p class="muted">Generated {generated}<br>Current task snapshot with activity and open work matched to the selected period.</p></div>{''.join(sections)}</body></html>"""


def render_report(value):
    from weasyprint import HTML
    from worker import inline_only, MAX_PDF_BYTES
    document = HTML(string=report_html(value), url_fetcher=inline_only).render()
    if not 2 <= len(document.pages) <= 200:
        raise ValueError("Report must have a cover and 1–199 content pages")
    result = document.write_pdf()
    if len(result) > MAX_PDF_BYTES:
        raise ValueError("Report exceeds 10 MB")
    return result
