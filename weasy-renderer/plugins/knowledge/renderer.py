"""Render selected, server-resolved Markdown pages with a cover and linked contents."""
from html import escape
from html.parser import HTMLParser
import re
from uuid import UUID

from pydantic import BaseModel, ConfigDict, Field


class KnowledgePage(BaseModel):
    model_config = ConfigDict(extra='forbid')
    id: UUID
    title: str = Field(min_length=1, max_length=180)
    html: str = Field(max_length=2_000_000)


class KnowledgeBook(BaseModel):
    model_config = ConfigDict(extra='forbid')
    title: str = Field(min_length=1, max_length=180)
    cover: dict
    pages: list[KnowledgePage] = Field(min_length=1, max_length=50)


class PageHtml(HTMLParser):
    """Defense in depth: the worker never accepts arbitrary active HTML or resource URLs."""
    tags = set('p h1 h2 h3 h4 h5 h6 strong em del s blockquote ul ol li pre code table thead tbody tr th td hr br a img div'.split())
    void = {'img', 'hr', 'br'}
    attributes = {'id', 'class', 'title', 'alt', 'href', 'src', 'align', 'start', 'data-diagram'}

    def __init__(self, prefix):
        super().__init__(convert_charrefs=True)
        self.output, self.headings, self.ids = [], [], set()
        self.heading = None
        self.prefix = prefix

    def handle_starttag(self, tag, attrs):
        from report_cover import raster_image
        if tag not in self.tags or len(dict(attrs)) != len(attrs):
            raise ValueError('Unsupported knowledge markup')
        attributes = dict(attrs)
        if set(attributes) - self.attributes:
            raise ValueError('Unsupported knowledge attribute')
        for key, value in attrs:
            if value is None:
                raise ValueError('Invalid attribute')
            if key == 'src':
                if tag != 'img':
                    raise ValueError('Invalid image resource')
                raster_image(value)
            if key == 'href' and (tag != 'a' or not re.match(r'^(#[\w-]+|https?://[^\s]+|mailto:[^\s]+)$', value, re.I)):
                raise ValueError('Unsupported knowledge link')
            if key == 'id':
                if not re.fullmatch(re.escape(self.prefix) + r'-[\w-]{1,180}', value) or value in self.ids:
                    raise ValueError('Invalid or duplicate heading anchor')
                self.ids.add(value)
        if tag.startswith('h') and len(tag) == 2 and tag[1].isdigit():
            self.heading = [attributes.get('id', ''), int(tag[1]), []]
        self.output.append('<' + tag + ''.join(f' {key}="{escape(value, quote=True)}"' for key, value in attrs) + '>')

    def handle_startendtag(self, tag, attrs):
        self.handle_starttag(tag, attrs)
        if tag not in self.void:
            self.handle_endtag(tag)

    def handle_endtag(self, tag):
        if tag not in self.tags:
            raise ValueError('Unsupported knowledge markup')
        if self.heading and tag == f'h{self.heading[1]}':
            anchor, level, texts = self.heading
            if anchor:
                self.headings.append((anchor, level, ''.join(texts)))
            self.heading = None
        if tag not in self.void:
            self.output.append('</' + tag + '>')

    def handle_data(self, value):
        self.output.append(escape(value))
        if self.heading:
            self.heading[2].append(value)

    def handle_decl(self, decl):
        raise ValueError('No declarations in documentation content')


CSS = '''
@page {size:A4;margin:20mm 18mm 19mm}
@page:first {counter-reset:page 2}
body {font:10.5pt "Liberation Sans",sans-serif;line-height:1.55;color:#183047;overflow-wrap:anywhere}
h1 {font-size:27pt;line-height:1.2;color:#133f51;bookmark-level:1}
h2 {font-size:18pt} h3 {font-size:14pt} h4,h5,h6 {font-size:11pt}
h1,h2,h3,h4,h5,h6 {break-after:avoid}
.doc-content h1 {font-size:21pt;bookmark-level:2}.doc-content h2 {bookmark-level:3}.doc-content h3 {bookmark-level:4}
.doc-content h4 {bookmark-level:5}.doc-content h5 {bookmark-level:6}.doc-content h6 {bookmark-level:7}
.document {break-before:page}.eyebrow {color:#157f82;font-size:9pt;letter-spacing:1px}
a {color:#086f85;text-decoration:none} img {max-width:100%;max-height:220mm;object-fit:contain}
figure,.knowledge-diagram {break-inside:avoid;text-align:center;margin:6mm 0}
pre {white-space:pre-wrap;overflow-wrap:anywhere;font-size:8.5pt;background:#eef3f6;padding:4mm;border-left:2px solid #157f82}
code {font-family:"Liberation Mono",monospace;font-size:9pt}
blockquote {border-left:3px solid #96b9be;padding-left:4mm;margin-left:0;color:#526b7b}
table {border-collapse:collapse;width:100%;table-layout:fixed;margin:4mm 0;font-size:9pt}
th,td {border:1px solid #d4e0e8;padding:2mm;overflow-wrap:anywhere} th {background:#eef5f6;text-align:left}
thead {display:table-header-group} tr {break-inside:avoid} hr {border:0;border-top:1px solid #d4e0e8}
.contents {list-style:none;padding:0}.contents li {margin:2.5mm 0}.contents a {display:block}
.contents a::after {content:leader('.') target-counter(attr(href),page);color:#526b7b}
.contents .sub {padding-left:6mm;font-size:9pt}.contents .sub2 {padding-left:11mm}
'''


def book_html(value):
    book = KnowledgeBook.model_validate(value)
    ids = [page.id for page in book.pages]
    if len(set(ids)) != len(ids):
        raise ValueError('Duplicate knowledge pages')
    contents, documents = [], []
    for page in book.pages:
        prefix = f'page-{page.id}'
        parser = PageHtml(prefix)
        parser.feed(page.html)
        parser.close()
        contents.append(f'<li><a href="#{prefix}">{escape(page.title)}</a></li>')
        for anchor, level, title in parser.headings:
            if level <= 2:
                contents.append(f'<li class="sub{"2" if level == 2 else ""}"><a href="#{anchor}">{escape(title)}</a></li>')
        documents.append(f'<article class="document"><p class="eyebrow">KNOWLEDGE BASE</p><h1 id="{prefix}">{escape(page.title)}</h1><div class="doc-content">{"".join(parser.output)}</div></article>')
    return f'<!doctype html><html lang="en"><head><meta charset="utf-8"><title>{escape(book.title)}</title><style>{CSS}</style></head><body><section><p class="eyebrow">{escape(book.title)}</p><h1 id="contents">Contents</h1><p>{len(book.pages)} selected documents</p><ol class="contents">{"".join(contents)}</ol></section>{"".join(documents)}</body></html>'


def render(value):
    from weasyprint import HTML
    from resources import inline_only, MAX_PDF_BYTES
    from report_cover import render_cover_document
    book = KnowledgeBook.model_validate(value)
    cover = render_cover_document(book.cover)
    body = HTML(string=book_html(value), url_fetcher=inline_only).render()
    if not 2 <= len(body.pages) <= 199:
        raise ValueError('Knowledge books must fit in 200 physical pages')
    pdf = body.copy(cover.pages + body.pages).write_pdf()
    if len(pdf) > MAX_PDF_BYTES:
        raise ValueError('Knowledge book exceeds 10 MiB')
    return pdf
