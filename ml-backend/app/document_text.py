"""Bounded text extraction. Documents are data; macros, scripts and external links never run."""
import io
import multiprocessing
import re
import zipfile
from html.parser import HTMLParser
from xml.etree import ElementTree

MAX_BYTES = 5 * 1024 * 1024
MAX_CHARS = 120_000
TEXT_EXTENSIONS = {"txt", "md", "csv", "tsv", "json", "xml", "html", "htm", "log", "yaml", "yml", "rst"}


class TextHTMLParser(HTMLParser):
    def __init__(self):
        super().__init__(convert_charrefs=True)
        self.parts = []
        self.hidden = 0

    def handle_starttag(self, tag, attrs):
        if tag in {"script", "style"}:
            self.hidden += 1

    def handle_endtag(self, tag):
        if tag in {"script", "style"}:
            self.hidden = max(0, self.hidden - 1)

    def handle_data(self, data):
        if not self.hidden:
            self.parts.append(data)


def extract_document(filename: str, content: bytes) -> tuple[str, bool]:
    if len(content) > MAX_BYTES:
        raise ValueError("Document exceeds the extraction size limit")
    extension = filename.rsplit(".", 1)[-1].lower()
    truncated = False
    if extension == "pdf":
        from pypdf import PdfReader
        reader = PdfReader(io.BytesIO(content))
        if reader.is_encrypted:
            raise ValueError("Encrypted PDFs cannot be indexed")
        parts, count = [], 0
        for index, page in enumerate(reader.pages):
            if index >= 200 or count >= MAX_CHARS:
                truncated = True
                break
            part = page.extract_text() or ""
            count += len(part)
            parts.append(part[:MAX_CHARS])
        text = "\n".join(parts)
    elif extension == "docx":
        with zipfile.ZipFile(io.BytesIO(content)) as archive:
            info = archive.getinfo("word/document.xml")
            if info.file_size > 2 * 1024 * 1024 or len(archive.infolist()) > 5000:
                raise ValueError("Document exceeds the XML extraction limit")
            xml = archive.read(info)
            if b"<!DOCTYPE" in xml.upper() or b"<!ENTITY" in xml.upper():
                raise ValueError("Document entities are not supported")
            root = ElementTree.fromstring(xml)
            text = "\n".join("".join(paragraph.itertext()) for paragraph in root.iter("{http://schemas.openxmlformats.org/wordprocessingml/2006/main}p"))
    elif extension in TEXT_EXTENSIONS:
        text = content.decode("utf-8-sig")
        if extension in {"html", "htm"}:
            parser = TextHTMLParser()
            parser.feed(text)
            text = "\n".join(parser.parts)
    else:
        raise ValueError("Unsupported document format")
    text = re.sub(r"[\x00-\x08\x0b\x0c\x0e-\x1f]", "", text).strip()
    return text[:MAX_CHARS], truncated or len(text) > MAX_CHARS


def _worker(connection, filename, content):
    try:
        # The process boundary also contains malformed PDFs and decompression bombs.
        try:
            import resource
            resource.setrlimit(resource.RLIMIT_AS, (512 * 1024 * 1024, 512 * 1024 * 1024))
            resource.setrlimit(resource.RLIMIT_CPU, (15, 15))
        except ImportError:  # Windows development; production uses Linux containers.
            pass
        connection.send((True, extract_document(filename, content)))
    except Exception:
        connection.send((False, None))
    finally:
        connection.close()


def extract_with_limits(filename: str, content: bytes) -> tuple[str, bool]:
    if len(content) > MAX_BYTES:
        raise ValueError("Document exceeds the extraction size limit")
    context = multiprocessing.get_context("spawn")
    parent, child = context.Pipe(duplex=False)
    process = context.Process(target=_worker, args=(child, filename, content), daemon=True)
    process.start()
    child.close()
    try:
        if not parent.poll(20):
            raise ValueError("Document extraction timed out")
        success, result = parent.recv()
        if not success:
            raise ValueError("Document could not be extracted")
        return result
    finally:
        parent.close()
        if process.is_alive():
            process.terminate()
        process.join(timeout=2)
        if process.is_alive():
            process.kill()
            process.join()
