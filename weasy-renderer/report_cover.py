"""HTML cover designs, isolated from report pages and from external resources."""
import re
from datetime import datetime, timezone
from html import escape
from html.parser import HTMLParser
from urllib.parse import unquote, urlsplit
import base64

TOKENS = {"title", "organization", "project", "projectName", "period", "ticketCount", "generatedAt", "siteName", "companyName"}
IMAGE_TOKEN = re.compile(r"\{\{\s*(siteLogo|clientLogo)\s*\}\}")
TEXT_TOKEN = re.compile(r"\{\{\s*(\w+)\s*\}\}")
CSS_URL = re.compile(r'''url\s*\(\s*(?:"([^"]*)"|'([^']*)'|([^)]*))\s*\)''', re.I)
EMPTY_IMAGE = 'data:image/png;base64,iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAQAAAC1HAwCAAAAC0lEQVR42mNk+A8AAQUBAScY42YAAAAASUVORK5CYII='
DEFAULT_COVER = """<!doctype html><html><head><meta charset="utf-8"><style>
@page {size:A4;margin:0} body {margin:0;font-family:Liberation Sans,sans-serif;color:#132842}
.cover {height:297mm;box-sizing:border-box;padding:28mm 22mm;background:#f4f7fb;border-top:12mm solid #2367c9}
.brand {font-size:12pt;letter-spacing:2px;color:#2367c9;margin-bottom:25mm}.logo {max-width:50mm;max-height:16mm}.company {font-size:10pt;letter-spacing:0}
h1 {font-size:36pt;line-height:1.16;margin:0 0 12mm;overflow-wrap:anywhere}
.project {font-size:18pt;line-height:1.4;overflow-wrap:anywhere}.period {font-size:13pt;color:#526579}
.rule {height:2mm;width:24mm;background:#2367c9;margin:20mm 0 12mm}.foot {font-size:9pt;color:#526579}
</style></head><body><main class="cover"><div class="brand"><img class="logo" src="{{siteLogo}}" alt="Site logo"><p>{{siteName}}</p><p class="company">{{companyName}}</p></div><h1>{{title}}</h1>
<img class="logo" src="{{clientLogo}}" alt="Client logo"><p class="project">{{projectName}}</p><p class="period">{{period}}</p><div class="rule"></div>
<p class="foot">{{ticketCount}} tickets in scope<br>Generated {{generatedAt}}<br>Internal project report</p></main></body></html>"""


def image_name(reference):
    reference = unquote(reference.strip())
    parts = urlsplit(reference)
    if parts.scheme or parts.netloc or parts.query or parts.fragment or '\\' in reference or reference.startswith('/') or '..' in reference.split('/'):
        raise ValueError('Use an uploaded image filename, not an external or filesystem URL')
    name = reference.split('/')[-1]
    if not re.fullmatch(r'[A-Za-z0-9][A-Za-z0-9._ -]{0,119}', name) or name != name.strip() or name in {'.', '..'}:
        raise ValueError('Invalid cover image filename')
    return name.lower()


def raster_image(value):
    from resources import embedded_image
    from io import BytesIO
    from PIL import Image
    image = Image.open(BytesIO(embedded_image(value)['string']))
    if image.format not in {'PNG', 'JPEG', 'WEBP'} or image.width * image.height > 8000000:
        raise ValueError('Use a bounded raster cover image')
    image.verify()
    return value


def cover_assets(images):
    if not isinstance(images, dict) or len(images) > 8:
        raise ValueError('Use at most eight cover images')
    result = {}
    total = 0
    for key, value in images.items():
        if not isinstance(key, str) or '/' in key or not isinstance(value, str):
            raise ValueError('Invalid cover image')
        name = image_name(key)
        if name in result:
            raise ValueError('Cover image filenames must be unique')
        raster_image(value)
        total += len(base64.b64decode(value.split(',', 1)[1], validate=True))
        if total > 1024 * 1024:
            raise ValueError('Cover images exceed 1 MiB')
        result[name] = value
    return result


def resolve_image(reference, assets, logos, render):
    token = IMAGE_TOKEN.fullmatch(reference.strip())
    if token:
        return raster_image(logos.get(token[1], '') or EMPTY_IMAGE) if render else reference
    if '{{' in reference:
        raise ValueError('Only siteLogo and clientLogo can be used as image tokens')
    if reference.startswith('data:'):
        return raster_image(reference)
    name = image_name(reference)
    if render and name not in assets:
        raise ValueError('Upload the matching cover image: ' + name)
    return assets[name] if render else reference


def validate_css(css, assets=None, logos=None, render=False):
    cleaned = re.sub(r"/\*.*?\*/", "", css, flags=re.S)
    if re.search(r"@(?:import|font-face|namespace|document)|image-set\s*\(|attr\s*\(|[\\]", cleaned, re.I):
        raise ValueError("Unsupported cover CSS resource or template token")
    remaining = CSS_URL.sub('', cleaned)
    if re.search(r'url\s*\(|\{\{', remaining, re.I):
        raise ValueError('Invalid cover CSS resource')
    def replace(match):
        source = (match[1] if match[1] is not None else match[2] if match[2] is not None else match[3]).strip()
        return 'url("' + resolve_image(source, assets or {}, logos or {}, render) + '")'
    return CSS_URL.sub(replace, cleaned)


class CoverParser(HTMLParser):
    tags = set("html head body meta title style div section header footer main article h1 h2 h3 h4 p span strong em b i br hr ul ol li table thead tbody tr th td img".split())
    attrs = set("class id style width height alt colspan rowspan charset".split())

    def __init__(self, values=None, assets=None, logos=None, render=False):
        super().__init__(convert_charrefs=True)
        self.styling = False
        self.values, self.assets, self.logos, self.render = values or {}, assets or {}, logos or {}, render
        self.output = []

    def handle_starttag(self, tag, attrs):
        if tag not in self.tags:
            raise ValueError("Unsupported cover HTML element")
        self.styling = tag == "style" or self.styling
        rendered = []
        for key, value in attrs:
            value = value or ""
            if key == "src" and tag == "img":
                value = resolve_image(value, self.assets, self.logos, self.render)
            elif key not in self.attrs:
                raise ValueError("Unsupported cover HTML attribute")
            elif key == "style":
                value = validate_css(value, self.assets, self.logos, self.render)
            elif '{{' in value:
                raise ValueError('Text tokens belong in visible text')
            if key == "charset" and value.lower() != "utf-8":
                raise ValueError("Cover encoding must be UTF-8")
            rendered.append(f' {key}="{escape(value, quote=True)}"')
        self.output.append('<' + tag + ''.join(rendered) + '>')

    def handle_startendtag(self, tag, attrs):
        self.handle_starttag(tag, attrs)
        self.handle_endtag(tag)

    def handle_endtag(self, tag):
        if tag not in self.tags:
            raise ValueError("Unsupported cover HTML element")
        if tag == "style":
            self.styling = False
        if tag not in {'img', 'br', 'hr', 'meta'}:
            self.output.append('</' + tag + '>')

    def handle_data(self, data):
        if self.styling:
            self.output.append(validate_css(data, self.assets, self.logos, self.render))
        else:
            if any(token not in TOKENS for token in TEXT_TOKEN.findall(data)):
                raise ValueError('Unknown visible-text cover token')
            text = escape(data)
            if self.render:
                text = TEXT_TOKEN.sub(lambda match: escape(self.values[match[1]]), text)
            self.output.append(text)

    def handle_decl(self, decl):
        if decl.lower() != 'doctype html':
            raise ValueError('Unsupported cover declaration')
        self.output.append('<!doctype html>')


def validate_cover_html(html):
    if not isinstance(html, str) or not html.strip() or len(html) > 30000:
        raise ValueError("Cover design must be 1-30,000 characters")
    parser = CoverParser()
    parser.feed(html)
    parser.close()
    return html


def cover_html(value):
    html = validate_cover_html(value.get("html") or DEFAULT_COVER)
    values = {key: str(value.get(key, "")) for key in TOKENS}
    values['siteName'] = values['siteName'] or values['organization']
    values['projectName'] = values['projectName'] or values['project']
    values['project'] = values['project'] or values['projectName']
    values["generatedAt"] = datetime.now(timezone.utc).strftime("%Y-%m-%d %H:%M UTC")
    if any(len(text) > 1000 for text in values.values()):
        raise ValueError("Cover context exceeds its limits")
    assets = cover_assets(value.get('assets', {}))
    logos = {key: value.get(key, '') for key in ('siteLogo', 'clientLogo')}
    for logo in logos.values():
        if logo:
            raster_image(logo)
    parser = CoverParser(values, assets, logos, True)
    parser.feed(html)
    parser.close()
    return ''.join(parser.output)


def render_cover_document(value):
    from weasyprint import HTML
    from resources import inline_only
    document = HTML(string=cover_html(value), url_fetcher=inline_only).render()
    if len(document.pages) != 1:
        raise ValueError("The cover design must fit on exactly one page")
    page = document.pages[0]
    for box in page._page_box.descendants():
        if box.element_tag and (box.border_box_y() + box.border_height() > page.height + 2 or box.border_box_x() + box.border_width() > page.width + 2):
            raise ValueError("The cover design extends beyond its page")
    return document


def render_cover(value):
    from resources import MAX_PDF_BYTES
    pdf = render_cover_document(value).write_pdf()
    if len(pdf) > MAX_PDF_BYTES:
        raise ValueError("Cover exceeds the PDF size limit")
    return pdf
