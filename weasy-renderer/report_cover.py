"""HTML cover designs, isolated from report pages and from external resources."""
import re
from datetime import datetime, timezone
from html import escape
from html.parser import HTMLParser

TOKENS = {"title", "organization", "project", "period", "ticketCount", "generatedAt"}
DEFAULT_COVER = """<!doctype html><html><head><meta charset="utf-8"><style>
@page {size:A4;margin:0} body {margin:0;font-family:Liberation Sans,sans-serif;color:#132842}
.cover {height:297mm;box-sizing:border-box;padding:28mm 22mm;background:#f4f7fb;border-top:12mm solid #2367c9}
.brand {font-size:12pt;letter-spacing:2px;color:#2367c9;margin-bottom:40mm}
h1 {font-size:36pt;line-height:1.16;margin:0 0 12mm;overflow-wrap:anywhere}
.project {font-size:18pt;line-height:1.4;overflow-wrap:anywhere}.period {font-size:13pt;color:#526579}
.rule {height:2mm;width:24mm;background:#2367c9;margin:20mm 0 12mm}.foot {font-size:9pt;color:#526579}
</style></head><body><main class="cover"><p class="brand">{{organization}}</p><h1>{{title}}</h1>
<p class="project">{{project}}</p><p class="period">{{period}}</p><div class="rule"></div>
<p class="foot">{{ticketCount}} tickets in scope<br>Generated {{generatedAt}}<br>Internal project report</p></main></body></html>"""


def validate_css(css):
    cleaned = re.sub(r"/\*.*?\*/", "", css, flags=re.S)
    if re.search(r"url\s*\(|@(?:import|font-face|namespace|document)|image-set\s*\(|attr\s*\(|[\\]|\{\{", cleaned, re.I):
        raise ValueError("Cover CSS cannot load resources or contain template tokens")


class CoverParser(HTMLParser):
    tags = set("html head body meta title style div section header footer main article h1 h2 h3 h4 p span strong em b i br hr ul ol li table thead tbody tr th td img".split())
    attrs = set("class id style width height alt colspan rowspan charset".split())

    def __init__(self):
        super().__init__(convert_charrefs=True)
        self.styling = False

    def handle_starttag(self, tag, attrs):
        if tag not in self.tags:
            raise ValueError("Unsupported cover HTML element")
        self.styling = tag == "style" or self.styling
        for key, value in attrs:
            value = value or ""
            if "{{" in value:
                raise ValueError("Cover tokens belong in visible text, not attributes")
            if key == "src" and tag == "img":
                from resources import inline_only
                from io import BytesIO
                from PIL import Image
                image = Image.open(BytesIO(inline_only(value)["string"]))
                if image.format not in {"PNG", "JPEG", "WEBP"} or image.width * image.height > 8000000:
                    raise ValueError("Use a bounded raster cover image")
                image.verify()
            elif key not in self.attrs:
                raise ValueError("Unsupported cover HTML attribute")
            if key == "style":
                validate_css(value)
            if key == "charset" and value.lower() != "utf-8":
                raise ValueError("Cover encoding must be UTF-8")

    def handle_startendtag(self, tag, attrs):
        self.handle_starttag(tag, attrs)
        self.handle_endtag(tag)

    def handle_endtag(self, tag):
        if tag not in self.tags:
            raise ValueError("Unsupported cover HTML element")
        if tag == "style":
            self.styling = False

    def handle_data(self, data):
        if self.styling:
            validate_css(data)


def validate_cover_html(html):
    if not isinstance(html, str) or not html.strip() or len(html) > 30000:
        raise ValueError("Cover design must be 1-30,000 characters")
    if any(token not in TOKENS for token in re.findall(r"\{\{\s*(\w+)\s*\}\}", html)):
        raise ValueError("Unknown cover token")
    parser = CoverParser()
    parser.feed(html)
    parser.close()
    return html


def cover_html(value):
    html = validate_cover_html(value.get("html") or DEFAULT_COVER)
    values = {key: str(value.get(key, "")) for key in TOKENS}
    values["generatedAt"] = datetime.now(timezone.utc).strftime("%Y-%m-%d %H:%M UTC")
    if any(len(text) > 1000 for text in values.values()):
        raise ValueError("Cover context exceeds its limits")
    return re.sub(r"\{\{\s*(\w+)\s*\}\}", lambda match: escape(values[match[1]]), html)


def render_cover_document(value):
    from weasyprint import HTML
    from resources import inline_only
    document = HTML(string=cover_html(value), url_fetcher=inline_only).render()
    if len(document.pages) != 1:
        raise ValueError("The cover design must fit on exactly one page")
    return document


def render_cover(value):
    from resources import MAX_PDF_BYTES
    pdf = render_cover_document(value).write_pdf()
    if len(pdf) > MAX_PDF_BYTES:
        raise ValueError("Cover exceeds the PDF size limit")
    return pdf
