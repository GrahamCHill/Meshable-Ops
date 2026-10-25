"""Bounded project reports with isolated HTML covers and numbered analytical pages."""
from datetime import datetime, timezone
from html import escape
from pydantic import BaseModel, Field, model_validator


class Breakdown(BaseModel):
    id: str = Field(default="", max_length=200)
    label: str = Field(max_length=200)
    count: int = Field(ge=0, le=40000)
    completed: int = Field(default=0, ge=0, le=2000)


class KanbanSummary(BaseModel):
    total: int = Field(default=0, ge=0, le=2000)
    completed: int = Field(default=0, ge=0, le=2000)
    overdue: int = Field(default=0, ge=0, le=2000)
    storyPoints: float = Field(default=0, ge=0, le=2000000, allow_inf_nan=False)
    stages: list[Breakdown] = Field(default_factory=list, max_length=2000)
    assignees: list[Breakdown] = Field(default_factory=list, max_length=2000)
    epics: list[Breakdown] = Field(default_factory=list, max_length=2000)
    tags: list[Breakdown] = Field(default_factory=list, max_length=30000)


class InsightMetric(BaseModel):
    label: str = Field(max_length=120)
    count: int = Field(ge=0, le=2000)


class Insights(BaseModel):
    metrics: list[InsightMetric] = Field(default_factory=list, max_length=8)
    priorities: list[Breakdown] = Field(default_factory=list, max_length=8)
    types: list[Breakdown] = Field(default_factory=list, max_length=8)
    observations: list[str] = Field(default_factory=list, max_length=3)


class BoardLeadTime(BaseModel):
    board: str = Field(max_length=200)
    count: int = Field(ge=0, le=2000)
    averageDays: float = Field(ge=0, le=4000000, allow_inf_nan=False)


class LeadTime(BaseModel):
    averageDays: float | None = Field(default=None, ge=0, le=4000000, allow_inf_nan=False)
    medianDays: float | None = Field(default=None, ge=0, le=4000000, allow_inf_nan=False)
    fastestDays: int | None = Field(default=None, ge=0, le=4000000)
    slowestDays: int | None = Field(default=None, ge=0, le=4000000)
    sampleSize: int = Field(default=0, ge=0, le=2000)
    completedTickets: int = Field(default=0, ge=0, le=2000)
    missingStart: int = Field(default=0, ge=0, le=2000)
    missingCompletion: int = Field(default=0, ge=0, le=2000)
    invalidDates: int = Field(default=0, ge=0, le=2000)
    distribution: list[Breakdown] = Field(default_factory=list, max_length=5)
    boards: list[BoardLeadTime] = Field(default_factory=list, max_length=8)
    creationAverageDays: float | None = Field(default=None, ge=0, le=4000000, allow_inf_nan=False)
    creationSampleSize: int = Field(default=0, ge=0, le=2000)
    missingCreation: int = Field(default=0, ge=0, le=2000)
    creationInvalidDates: int = Field(default=0, ge=0, le=2000)

    @model_validator(mode='after')
    def coherent_samples(self):
        if self.sampleSize > self.completedTickets or self.creationSampleSize > self.completedTickets or self.distribution and sum(item.count for item in self.distribution) != self.sampleSize:
            raise ValueError('Duration distributions and measured sample counts must agree')
        return self


class SprintSummary(BaseModel):
    name: str = Field(max_length=100)
    board: str = Field(max_length=200)
    startDate: str = Field(max_length=10)
    endDate: str = Field(max_length=10)
    total: int = Field(ge=0, le=2000)
    completed: int = Field(ge=0, le=2000)
    completedAverageDays: float | None = Field(default=None, ge=0, le=4000000)
    openAverageDays: float | None = Field(default=None, ge=0, le=4000000)
    completedSampleSize: int = Field(default=0, ge=0, le=2000)
    openSampleSize: int = Field(default=0, ge=0, le=2000)


class SprintDuration(BaseModel):
    reference: str = Field(max_length=120)
    title: str = Field(max_length=160)
    board: str = Field(max_length=200)
    sprint: str = Field(max_length=100)
    sprintStart: str = Field(max_length=10)
    measuredThrough: str = Field(max_length=10)
    days: int = Field(ge=0, le=4000000)
    completed: bool


class SprintMonitor(BaseModel):
    assignedTickets: int = Field(default=0, ge=0, le=2000)
    completedAverageDays: float | None = Field(default=None, ge=0, le=4000000)
    completedSampleSize: int = Field(default=0, ge=0, le=2000)
    openAverageDays: float | None = Field(default=None, ge=0, le=4000000)
    openSampleSize: int = Field(default=0, ge=0, le=2000)
    oldestOpenDays: int | None = Field(default=None, ge=0, le=4000000)
    completionPercent: float | None = Field(default=None, ge=0, le=100)
    noSprint: int = Field(default=0, ge=0, le=2000)
    missingSprint: int = Field(default=0, ge=0, le=2000)
    missingCompletion: int = Field(default=0, ge=0, le=2000)
    invalidDates: int = Field(default=0, ge=0, le=2000)
    notStarted: int = Field(default=0, ge=0, le=2000)
    finishedAfterEnd: int = Field(default=0, ge=0, le=2000)
    totalSprints: int = Field(default=0, ge=0, le=2000)
    sprints: list[SprintSummary] = Field(default_factory=list, max_length=6)
    longestTickets: list[SprintDuration] = Field(default_factory=list, max_length=6)


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
    sections: list[ReportSection] = Field(min_length=1, max_length=200)
    kanban: KanbanSummary | None = None
    insights: Insights = Field(default_factory=Insights)
    leadTime: LeadTime = Field(default_factory=LeadTime)
    sprintMonitor: SprintMonitor = Field(default_factory=SprintMonitor)
    coverHtml: str | None = Field(default=None, max_length=30000)
    coverAssets: dict[str, str] = Field(default_factory=dict, max_length=8)
    siteName: str = Field(default='', max_length=80)
    companyName: str = Field(default='', max_length=120)
    siteLogo: str = Field(default='', max_length=350000)

    @model_validator(mode="after")
    def bounded_report(self):
        if sum(len(section.rows) for section in self.sections) > 2000 or any(len(line) > 1000 for line in self.summary) or any(len(line) > 300 for line in self.insights.observations):
            raise ValueError("Report exceeds its limits")
        if self.coverHtml:
            from report_cover import validate_cover_html
            validate_cover_html(self.coverHtml)
        if any(len(name) > 120 or len(image) > 350000 for name, image in self.coverAssets.items()) or sum(len(image) for image in self.coverAssets.values()) > 1400000:
            raise ValueError('Report cover images exceed their limits')
        return self


def short(text, limit=52):
    text = " ".join(text.split())
    return text if len(text) <= limit else text[:limit - 3] + "..."


def excerpt(text, limit):
    lines = text.replace("\r", "").split("\n")
    clipped = "\n".join(lines[:8])
    return clipped[:limit - 3] + "..." if len(clipped) > limit or len(lines) > 8 else clipped


def compact(values, count=6):
    ordered = sorted(values, key=lambda item: (-item.count, item.label))
    shown = ordered[:count]
    if len(ordered) > count:
        shown = shown + [Breakdown(id="other", label="Other", count=sum(item.count for item in ordered[count:]))]
    return shown


COLORS = ["#2367c9", "#6d55b8", "#159b72", "#dc9618", "#1497a7", "#d45b91", "#77879a"]


def bars(title, values, ordered=False):
    values = values if ordered else compact(values)
    maximum = max([item.count for item in values] + [1])
    rows = "".join(f'<tr><td><div class="bar-label">{escape(short(item.label))}<b>{item.count}</b></div><div class="bar-bg"><div style="width:{item.count / maximum * 100:.2f}%;background:{COLORS[i % len(COLORS)]}"></div></div></td></tr>' for i, item in enumerate(values))
    return f'<div class="chart"><h3>{escape(title)}</h3><table class="bars">{rows}</table>{"<p class=muted>No tickets in this group.</p>" if not values else ""}</div>'


def stage_chart(summary):
    stages = compact(summary.stages)
    circles = []
    offset = 0
    circumference = 251.327
    for index, item in enumerate(stages):
        length = item.count / max(sum(stage.count for stage in stages), 1) * circumference
        circles.append(f'<circle cx="56" cy="56" r="40" fill="none" stroke="{COLORS[index % len(COLORS)]}" stroke-width="13" stroke-dasharray="{length:.3f} {circumference - length:.3f}" stroke-dashoffset="{-offset:.3f}" transform="rotate(-90 56 56)"/>')
        offset += length
    percentage = round(summary.completed / max(summary.total, 1) * 100)
    svg = f'<svg xmlns="http://www.w3.org/2000/svg" width="112" height="112" viewBox="0 0 112 112"><circle cx="56" cy="56" r="40" fill="none" stroke="#edf1f7" stroke-width="13"/>{"".join(circles)}<text x="56" y="57" text-anchor="middle" font-size="19" fill="#132842">{percentage}%</text><text x="56" y="71" text-anchor="middle" font-size="8" fill="#526579">complete</text></svg>'
    legend = "".join(f'<tr><td><span class="swatch" style="background:{COLORS[i % len(COLORS)]}"></span>{escape(short(item.label, 28))}</td><td>{item.count}</td></tr>' for i, item in enumerate(stages))
    return f'<div class="chart"><h3>Tickets by column / stage</h3><table class="donut"><tr><td class="ring">{svg}</td><td><table>{legend}</table></td></tr></table></div>'


def metric(label, value):
    return f'<div class="metric"><strong>{escape(str(value))}</strong><span>{escape(label)}</span></div>'


def number(value):
    return "N/A" if value is None else f"{value:,.1f}"


def duration_label(value):
    return 'N/A' if value is None else number(value) + 'd'


CSS = """
@page {size:A4;margin:17mm 15mm 18mm;@bottom-left {content:'Internal project report';font:8pt 'Liberation Sans';color:#617186}@bottom-right {content:'Page ' counter(page) ' of ' counter(pages);font:8pt 'Liberation Sans';color:#617186}}
body {font:9pt 'Liberation Sans',sans-serif;color:#132842;line-height:1.4;margin:0}
h1 {font-size:24pt;line-height:1.15;margin:3mm 0 4mm}h2 {font-size:15pt;margin:6mm 0 3mm;break-after:avoid}h3 {font-size:10pt;margin:0 0 3mm;break-after:avoid}
p {margin:2mm 0}.eyebrow {font-size:9pt;text-transform:uppercase;letter-spacing:1px;color:#2367c9}.muted {color:#617186;font-size:8pt}.period {font-size:9pt;border-bottom:1px solid #dce4ed;padding-bottom:4mm;margin-bottom:5mm}
.report-page {break-after:page}.metrics {display:table;width:100%;table-layout:fixed;margin:4mm 0 6mm}.metric {display:table-cell;padding:4mm 2mm;background:#f0f5fc;border-right:2mm solid white;vertical-align:top}.metric strong {display:block;font-size:22pt;font-weight:700;line-height:1.2;overflow-wrap:anywhere}.metric span {display:block;font-size:8pt;color:#526579;margin-top:2mm}
.chart-grid:after {content:'';display:block;clear:both}.chart {float:left;box-sizing:border-box;width:49%;height:65mm;padding:4mm;border:1px solid #dce4ed;margin:0 1% 4mm 0;overflow:hidden}
table {width:100%;border-collapse:collapse;table-layout:fixed}td,th {vertical-align:top}.bars td {padding:1mm 0}.bar-label {font-size:8pt;overflow-wrap:anywhere}.bar-label b {float:right;margin-left:2mm}.bar-bg {height:2mm;background:#edf1f7;margin-top:1mm}.bar-bg div {height:2mm}.donut td {font-size:7.5pt;padding:1mm 0;overflow-wrap:anywhere}.donut .ring {width:33mm;vertical-align:middle}.swatch {display:inline-block;width:2mm;height:2mm;margin-right:1mm}
.insight-grid {display:table;width:100%;table-layout:fixed}.insight-column {display:table-cell;width:50%;padding-right:4mm}.insight {padding:3mm 0;border-bottom:1px solid #dce4ed}.insight b {float:right}.observations {clear:both;background:#f0f5fc;padding:4mm;margin-top:5mm}.observations p {margin:2mm 0}
.lead-metrics .metric strong {font-size:19pt}.lead-table th,.lead-table td {text-align:left;padding:2mm;border-bottom:1px solid #dce4ed;font-size:8pt;overflow-wrap:anywhere}.lead-table th {background:#f0f5fc}
.lead-chart-grid .chart {height:55mm}
.creation-duration {clear:both;padding:3mm 4mm;background:#f0f5fc;margin:2mm 0 4mm}.creation-duration strong {font-size:15pt}.creation-duration h3 {margin-bottom:1mm}
.lead-table th,.lead-table td {padding:1mm 2mm;font-size:7.5pt}.board-name {white-space:nowrap;overflow:hidden;text-overflow:ellipsis}
.monitor-table th,.monitor-table td {text-align:left;padding:1.2mm 1.5mm;border-bottom:1px solid #dce4ed;font-size:7.5pt}.monitor-table th {background:#f0f5fc}.monitor-name {white-space:nowrap;overflow:hidden;text-overflow:ellipsis}.monitor-table small {font-size:7pt;color:#617186}
.sprint-page h2 {margin:3mm 0 2mm;font-size:13pt}.sprint-page .metrics {margin-bottom:4mm}.sprint-page .monitor-table th,.sprint-page .monitor-table td {padding:.7mm 1.2mm}
.ticket-section {margin-bottom:6mm}.tickets {font-size:7.5pt;line-height:1.35}.tickets thead {display:table-header-group}.tickets th {text-align:left;background:#edf2f8}.tickets th,.tickets td {padding:2mm 1.5mm;white-space:pre-wrap;overflow-wrap:anywhere;word-break:break-all}.tickets tr {break-inside:avoid}.tickets tbody tr:nth-child(even) {background:#f7f9fc}
"""


def sprint_page(report, heading, period):
    monitor = report.sprintMonitor
    metrics = ''.join(metric(label, value) for label, value in [('Avg completed (days)', number(monitor.completedAverageDays)), ('Avg open age (days)', number(monitor.openAverageDays)), ('Oldest open (days)', number(monitor.oldestOpenDays)), ('Tickets completed', 'N/A' if monitor.completionPercent is None else number(monitor.completionPercent) + '%')])
    rows = ''.join(f'<tr><td><div class="monitor-name">{escape(item.name)} / {escape(item.board)}</div><small>{escape(item.startDate)} - {escape(item.endDate)}</small></td><td>{item.total}</td><td>{item.completed}</td><td>{duration_label(item.completedAverageDays)}<br><small>{item.completedSampleSize} measured</small></td><td>{duration_label(item.openAverageDays)}<br><small>{item.openSampleSize} measured</small></td></tr>' for item in monitor.sprints)
    tickets = ''.join(f'<tr><td><div class="monitor-name">{escape(item.reference)} {escape(short(item.title, 45))}</div></td><td><div class="monitor-name">{escape(item.sprint)}</div><div class="monitor-name"><small>{escape(item.board)}</small></div></td><td><small>{escape(item.sprintStart)}<br>{escape(item.measuredThrough)}</small></td><td>{item.days} days<br><small>{"Completed" if item.completed else "Open / live"}</small></td></tr>' for item in monitor.longestTickets)
    return f'<section class="report-page sprint-page" id="report-sprints">{heading}<h1>Sprint monitor</h1>{period}<div class="metrics lead-metrics">{metrics}</div><p><b>{monitor.assignedTickets} sprint-assigned tickets</b>; {monitor.completedSampleSize} completed durations and {monitor.openSampleSize} open ages measured.</p><p class="muted">Completed duration is sprint start date to ticket completion. Open age is sprint start through the report generation date (UTC). {monitor.finishedAfterEnd} measured tickets completed after their planned sprint end.</p><h2>Sprint overview</h2><table class="monitor-table"><colgroup><col style="width:40%"><col style="width:10%"><col style="width:10%"><col style="width:20%"><col style="width:20%"></colgroup><thead><tr><th>Sprint / board</th><th>Tickets</th><th>Done</th><th>Avg completed</th><th>Avg open age</th></tr></thead><tbody>{rows or "<tr><td colspan=5>No sprint assignments in this scope.</td></tr>"}</tbody></table><h2>Longest sprint durations</h2><table class="monitor-table"><colgroup><col style="width:38%"><col style="width:26%"><col style="width:18%"><col style="width:18%"></colgroup><thead><tr><th>Ticket</th><th>Sprint / board</th><th>Start / through</th><th>Duration</th></tr></thead><tbody>{tickets or "<tr><td colspan=4>No tickets have an eligible sprint duration.</td></tr>"}</tbody></table><p class="muted">Shows the six largest sprint groups of {monitor.totalSprints}, and six longest measured tickets. Every scoped ticket and its dates/durations appears in the ticket export.</p><p class="muted">Excluded from timing: {monitor.noSprint} without a sprint, {monitor.missingSprint} unavailable sprint definitions, {monitor.missingCompletion} missing completion dates, {monitor.invalidDates} completions before sprint start, {monitor.notStarted} open tickets in future sprints. Current sprint assignments are a snapshot; previous membership is not reconstructed.</p></section>'


def report_html(value):
    report = ReportDocument.model_validate(value)
    summary = report.kanban or KanbanSummary(total=sum(len(section.rows) for section in report.sections))
    heading = f'<p class="eyebrow">{escape(report.organization)}</p><p>{escape(short(report.subtitle, 110))}</p>'
    period = f'<p class="period">{escape(report.period)} | Current ticket snapshot</p>'
    metrics = ''.join(metric(label, count) for label, count in [("Total tickets", summary.total), ("Completed", summary.completed), ("Open tickets", summary.total - summary.completed), ("Overdue", summary.overdue), ("Story points", f"{summary.storyPoints:g}")])
    overview = f'<section class="report-page" id="report-summary">{heading}<h1>Kanban summary</h1>{period}<div class="metrics">{metrics}</div><div class="chart-grid">{stage_chart(summary)}{bars("Assigned tickets per person", summary.assignees)}{bars("Tickets by epic", summary.epics)}{bars("Ticket tags", summary.tags)}</div><p class="muted">The same summary measures as the Kanban view, scoped to this report. Large groups show the top six plus Other; ticket totals include subtasks.</p></section>'
    facts = report.insights.metrics
    def facts_column(values):
        return '<div class="insight-column">' + ''.join(f'<div class="insight">{escape(item.label)}<b>{item.count}</b></div>' for item in values) + '</div>'
    observations = ''.join(f'<p>{escape(item)}</p>' for item in report.insights.observations)
    insights = f'<section class="report-page" id="report-insights">{heading}<h1>Additional insights</h1>{period}<div class="insight-grid">{facts_column(facts[:4])}{facts_column(facts[4:])}</div><h2>Work mix</h2><div class="chart-grid">{bars("Ticket priority", report.insights.priorities)}{bars("Ticket types", report.insights.types)}</div><div class="observations"><h3>Delivery observations</h3>{observations or "<p>No tickets matched the selected scope.</p>"}</div><p class="muted">Open-work indicators are evaluated at generation time. These describe delivery conditions; confidential risk scores and individual performance assessments are excluded.</p></section>'
    lead = report.leadTime
    lead_metrics = ''.join(metric(label, number(count)) for label, count in [("Average lead time (days)", lead.averageDays), ("Median (days)", lead.medianDays), ("Fastest (days)", lead.fastestDays), ("Slowest (days)", lead.slowestDays)])
    by_board = ''.join(f'<tr><td><div class="board-name">{escape(short(item.board, 60))}</div></td><td>{item.count}</td><td>{item.averageDays:.1f} days</td></tr>' for item in lead.boards)
    lead_page = f'<section class="report-page" id="report-leadtime">{heading}<h1>Average ticket lead time</h1>{period}<div class="metrics lead-metrics">{lead_metrics}</div><p><b>{lead.sampleSize} measured tickets</b> out of {lead.completedTickets} completed tickets in scope.</p><p class="muted">Lead time is calendar days from the ticket start date to its recorded completion date. Same-day completion is zero days. Reopened tickets are excluded until completed again. The averages and distribution use this same eligible ticket sample.</p><div class="chart-grid lead-chart-grid">{bars("Lead-time distribution", lead.distribution, ordered=True)}<div class="chart"><h3>Data coverage</h3><p>{lead.missingStart} completed tickets have no start date.</p><p>{lead.missingCompletion} have no completion date.</p><p>{lead.invalidDates} have inconsistent date order.</p><p class="muted">These counts can overlap. Missing historical dates are never invented.</p></div></div><div class="creation-duration"><h3>From creation to completion</h3><p><strong>{number(lead.creationAverageDays)} days</strong> average across {lead.creationSampleSize} measured tickets.</p><p class="muted">Server creation date (UTC) to completion. Excluded: {lead.missingCreation} missing creation timestamps, {lead.missingCompletion} missing completion dates, {lead.creationInvalidDates} inconsistent date orders. Counts may overlap.</p></div><h2>Lead time by board</h2><table class="lead-table"><thead><tr><th>Board</th><th>Measured tickets</th><th>Average</th></tr></thead><tbody>{by_board or "<tr><td colspan=3>No eligible completed tickets in this scope.</td></tr>"}</tbody></table><p class="muted">Up to eight boards with the largest measured samples. Completed tickets without a start or completion date remain excluded from lead time.</p></section>'
    sections = []
    limits = [160, 140, 90, 180, 240]
    for section in report.sections:
        widths = [25, 20, 14, 17, 24] if len(section.columns) == 5 else [100 / len(section.columns)] * len(section.columns)
        columns = '<colgroup>' + ''.join(f'<col style="width:{width}%">' for width in widths) + '</colgroup>'
        headers = ''.join(f'<th>{escape(column)}</th>' for column in section.columns)
        rows = ''.join('<tr>' + ''.join(f'<td>{escape(excerpt(cell, limits[index] if len(section.columns) == 5 else 240))}</td>' for index, cell in enumerate(row)) + '</tr>' for row in section.rows)
        sections.append(f'<section class="ticket-section"><h2>{escape(section.title)}</h2><table class="tickets">{columns}<thead><tr>{headers}</tr></thead><tbody>{rows}</tbody></table></section>')
    export = f'<section id="ticket-export">{heading}<h1>Ticket export</h1>{period}<p class="muted">All scoped tickets are listed. Long fields are excerpts: task 160, people 140, status 90, dates 180 and description 240 characters; up to eight lines per cell. N/A durations lack eligible dates. Full details remain in the workspace.</p>{"".join(sections)}</section>'
    return f'<!doctype html><html lang="en"><head><meta charset="utf-8"><style>{CSS}</style></head><body>{overview}{insights}{sprint_page(report, heading, period)}{lead_page}{export}</body></html>'


def index_html(report, pages):
    rows = ''.join(f'<tr><td>{title}</td><td>{page}</td></tr>' for title, page in [("Kanban summary", 1), ("Additional insights", 2), ("Sprint monitor", 3), ("Average ticket lead time", 4), ("Ticket export", 5)])
    scope = ''.join(f'<li>{escape(short(line, 160))}</li>' for line in report.summary[:6])
    return f'<!doctype html><html><head><meta charset="utf-8"><style>@page{{size:A4;margin:22mm}}body{{font:11pt "Liberation Sans",sans-serif;color:#132842;line-height:1.6}}h1{{font-size:30pt;margin-top:20mm}}table{{width:100%;border-collapse:collapse;margin-top:12mm}}td,th{{padding:5mm 0;border-bottom:1px solid #dce4ed}}th{{text-align:left;font-size:10pt;background:#f0f5fc}}td:last-child,th:last-child{{text-align:right}}.muted{{font-size:9pt;color:#617186}}ul{{padding-left:5mm}}</style></head><body><p>{escape(report.organization)}</p><h1>Report index</h1><p>{escape(report.subtitle)}</p><p>{escape(report.period)}</p><table><thead><tr><th scope="col">Content</th><th scope="col">Page</th></tr></thead><tbody>{rows}</tbody></table><p class="muted">Page numbers start at the Kanban summary. This cover and index precede numbered page 1. The report contains {pages} numbered pages.</p><h2>Scope</h2><ul>{scope}</ul></body></html>'


def render_report(value):
    from weasyprint import HTML
    from resources import inline_only, MAX_PDF_BYTES
    from report_cover import render_cover_document
    report = ReportDocument.model_validate(value)
    body = HTML(string=report_html(value), url_fetcher=inline_only).render()
    expected = ["report-summary", "report-insights", "report-sprints", "report-leadtime", "ticket-export"]
    for index, anchor in enumerate(expected):
        if index >= len(body.pages) or anchor not in body.pages[index].anchors:
            raise ValueError("Analytical report pages exceed their fixed layout")
    cover = render_cover_document({"html": report.coverHtml, "title": report.title, "organization": report.organization, "project": report.subtitle, "period": report.period, "ticketCount": (report.kanban.total if report.kanban else sum(len(section.rows) for section in report.sections)), 'assets': report.coverAssets, 'siteName': report.siteName, 'companyName': report.companyName, 'siteLogo': report.siteLogo})
    index = HTML(string=index_html(report, len(body.pages)), url_fetcher=inline_only).render()
    if len(index.pages) != 1 or not 5 <= len(body.pages) <= 198:
        raise ValueError("Report must have a cover, index and 5-198 numbered pages")
    result = body.copy(cover.pages + index.pages + body.pages).write_pdf()
    if len(result) > MAX_PDF_BYTES:
        raise ValueError("Report exceeds 10 MB")
    return result
