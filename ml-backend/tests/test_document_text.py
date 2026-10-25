import io
import unittest
import zipfile

from app.document_text import extract_document, extract_with_limits, extract_pages, MAX_BYTES, MAX_CHARS


class DocumentTextTests(unittest.TestCase):
    def test_utf8_html_excludes_scripts(self):
        text, truncated = extract_document("note.html", b"<h1>Renewal</h1><script>private-script</script><p>60 days notice</p>")
        self.assertIn("60 days notice", text)
        self.assertNotIn("private-script", text)
        self.assertFalse(truncated)

    def test_docx_extracts_paragraphs_without_extracting_files(self):
        output = io.BytesIO()
        with zipfile.ZipFile(output, "w") as archive:
            archive.writestr("word/document.xml", '<w:document xmlns:w="http://schemas.openxmlformats.org/wordprocessingml/2006/main"><w:body><w:p><w:r><w:t>Renewal on December 31</w:t></w:r></w:p></w:body></w:document>')
        self.assertEqual(extract_document("contract.docx", output.getvalue())[0], "Renewal on December 31")

    def test_pdf_extracts_actual_page_text(self):
        from pypdf import PdfWriter
        from pypdf.generic import DictionaryObject, NameObject, DecodedStreamObject
        writer = PdfWriter()
        page = writer.add_blank_page(width=400, height=400)
        font = DictionaryObject({NameObject("/Type"): NameObject("/Font"), NameObject("/Subtype"): NameObject("/Type1"), NameObject("/BaseFont"): NameObject("/Helvetica")})
        page[NameObject("/Resources")] = DictionaryObject({NameObject("/Font"): DictionaryObject({NameObject("/F1"): font})})
        stream = DecodedStreamObject(); stream.set_data(b"BT /F1 12 Tf 30 300 Td (Notice period is 60 days.) Tj ET")
        page[NameObject("/Contents")] = stream
        output = io.BytesIO(); writer.write(output)
        self.assertIn("Notice period is 60 days.", extract_document("contract.pdf", output.getvalue())[0])

    def test_limits_and_unsupported_formats(self):
        with self.assertRaises(ValueError):
            extract_document("too-large.txt", b"a" * (MAX_BYTES + 1))
        with self.assertRaises(ValueError):
            extract_document("program.exe", b"program")
        text, truncated = extract_document("long.txt", b"a" * (MAX_CHARS + 1))
        self.assertEqual(len(text), MAX_CHARS); self.assertTrue(truncated)

    def test_extraction_runs_in_a_bounded_process(self):
        self.assertEqual(extract_with_limits("note.txt", b"Renewal in December"), ("Renewal in December", False))

    def test_image_scans_require_an_external_ocr_service(self):
        result = extract_pages("scan.png", b"original bytes for the external provider")
        self.assertTrue(result["needsOcr"])
        self.assertEqual(result["text"], "")

    def test_image_only_pdf_reports_original_page_number_and_ocr_requirement(self):
        from pypdf import PdfWriter
        from pypdf.generic import DictionaryObject, NameObject
        writer = PdfWriter()
        page = writer.add_blank_page(width=400, height=400)
        page[NameObject("/Resources")] = DictionaryObject({
            NameObject("/XObject"): DictionaryObject({NameObject("/Im1"): DictionaryObject({NameObject("/Subtype"): NameObject("/Image")})})
        })
        output = io.BytesIO()
        writer.write(output)
        result = extract_pages("scan.pdf", output.getvalue())
        self.assertTrue(result["needsOcr"])
        self.assertEqual(result["pages"][0]["pageNumber"], 1)

    def test_native_text_beyond_the_former_5_mib_limit_can_be_extracted(self):
        result = extract_pages("long.txt", b"a" * (6 * 1024 * 1024))
        self.assertEqual(len(result["text"]), MAX_CHARS)
        self.assertTrue(result["truncated"])


if __name__ == "__main__":
    unittest.main()
