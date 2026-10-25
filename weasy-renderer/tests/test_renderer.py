import copy
import json
import os
from pathlib import Path
import unittest
from unittest.mock import patch

from fastapi.testclient import TestClient
from app import app
from backend.generator import DocumentTemplate, render_document_html
from plugins.documents.renderer import make_draft, render
from resources import inline_only

SOURCE = json.loads((Path(__file__).parent / "fixtures/quote.json").read_text())
VALUES = {"company.name": "Sample Company", "document.number": "Q-2026-001", "document.date": "2026-10-24",
    "customer.name": "Sample Customer", "line_items": "Consulting services - 2 hours at 100.00",
    "subtotal": "200.00", "tax_rate": "15", "tax": "30.00", "total": "230.00"}


class RendererTests(unittest.TestCase):
    def test_quote_template_and_values_resolve_to_escaped_html(self):
        template = SOURCE["template"]
        DocumentTemplate.model_validate(template)
        draft = make_draft(template, {**VALUES, "customer.name": "<script>alert('sample')</script>"})
        html = render_document_html(draft)
        self.assertIn("&lt;script&gt;", html)
        self.assertNotIn("<script>", html)
        self.assertIn("230.00", html)
        self.assertNotIn("{{api:", html)

    def test_required_fields_unknown_fields_and_external_assets_fail_closed(self):
        missing = {**VALUES}; del missing["customer.name"]
        with self.assertRaises(ValueError): make_draft(SOURCE["template"], missing)
        with self.assertRaises(ValueError): make_draft(SOURCE["template"], {**VALUES, "private.extra": "forbidden"})
        for url in ["file:///etc/passwd", "https://example.com/logo.png", "http://169.254.169.254/", "data:image/svg+xml;base64,PHN2Zy8+"]:
            with self.assertRaises(ValueError): inline_only(url)

    def test_document_factory_honors_section_paragraph_minimums(self):
        template = copy.deepcopy(SOURCE["template"])
        section = next(section for section in template["sections"] if section["kind"] == "text")
        section["rules"]["minParagraphs"] = 2
        draft = make_draft(template, VALUES)
        self.assertEqual(len(draft.paragraphs), 2)
        self.assertEqual(draft.paragraphSectionIds, ["items", "items"])

    def test_multiple_required_sections_do_not_force_headings_or_empty_heading_space(self):
        template = copy.deepcopy(SOURCE["template"])
        template["sections"].insert(4, {"kind": "body", "id": "terms", "label": "Terms", "required": True})
        template["defaults"]["paragraphs"].append("Payment is due within thirty days.")
        template["defaults"]["paragraphSectionIds"].append("terms")
        template["defaults"]["subparagraphs"].append([])
        for headings in [{}, {"items": "", "terms": "  \n"}]:
            with self.subTest(headings=headings):
                template["defaults"]["text"] = {"sectionHeadings": headings}
                html = render_document_html(make_draft(template, VALUES))
                self.assertNotIn('<h2 class="paragraph-section-heading">', html)
                self.assertIn("Payment is due within thirty days.", html)
        template["defaults"]["text"]["sectionHeadings"]["terms"] = "Payment & delivery"
        html = render_document_html(make_draft(template, VALUES))
        self.assertEqual(html.count('<h2 class="paragraph-section-heading">'), 1)
        self.assertIn('>Payment &amp; delivery</h2>', html)

    def test_single_section_can_have_an_optional_inline_or_bound_heading(self):
        template = copy.deepcopy(SOURCE["template"])
        template["defaults"]["text"] = {"sectionHeadings": {"items": "For {{api:customer.name}}"}}
        html = render_document_html(make_draft(template, VALUES))
        self.assertIn('>For Sample Customer</h2>', html)
        self.assertNotIn("{{api:", html)
        section = next(section for section in template["sections"] if section["kind"] == "text")
        next(field for field in template["apiFields"] if field["key"] == "customer.name")["maxLength"] = 80
        section["bindings"] = {"heading": "customer.name"}
        html = render_document_html(make_draft(template, VALUES))
        self.assertIn('>Sample Customer</h2>', html)

    def test_compact_spacing_defaults_and_explicit_spacing_reach_the_pdf_layout(self):
        template = copy.deepcopy(SOURCE["template"])
        template["layout"].pop("paragraphGap")
        template["formatting"].pop("body")
        draft = make_draft(template, VALUES)
        self.assertEqual(draft.layout.paragraphGap, 8)
        self.assertEqual(draft.formatting.body.lineHeight, 1.2)
        html = render_document_html(draft)
        self.assertIn("margin-bottom: 8.0px", html)
        self.assertIn("line-height:1.2", html)
        template["layout"]["paragraphGap"] = 4
        template["formatting"]["body"] = {"lineHeight": 1.15}
        html = render_document_html(make_draft(template, VALUES))
        self.assertIn("margin-bottom: 4.0px", html)
        self.assertIn("line-height:1.15", html)

    def test_renderer_endpoints_require_configured_internal_token_and_bound_bodies(self):
        with patch.dict(os.environ, {"APP_DOCUMENT_RENDER_TOKEN": "sample-internal-render-token"}):
            client = TestClient(app)
            self.assertEqual(client.post("/validate", json={"template": SOURCE["template"]}).status_code, 401)
            headers = {"Authorization": "Bearer sample-internal-render-token"}
            self.assertEqual(client.post("/validate", json={"template": SOURCE["template"]}, headers=headers).status_code, 200)
            self.assertEqual(client.post("/validate", content=b"x" * (2 * 1024 * 1024 + 1), headers=headers).status_code, 413)
            self.assertEqual(client.post("/validate", json={"template": {}}, headers=headers).status_code, 400)

    def test_real_pdf_is_nonempty_and_uses_the_same_template_contract(self):
        pdf = render(SOURCE["template"], VALUES)
        self.assertTrue(pdf.startswith(b"%PDF-"))
        self.assertGreater(len(pdf), 1000)
        target = Path("target"); target.mkdir(exist_ok=True)
        (target / "sample-quote.pdf").write_bytes(pdf)

    def test_worker_process_endpoint_returns_a_real_pdf_and_no_source(self):
        with patch.dict(os.environ, {"APP_DOCUMENT_RENDER_TOKEN": "sample-internal-render-token"}):
            response = TestClient(app).post("/render", json={"template": SOURCE["template"], "values": VALUES}, headers={"Authorization": "Bearer sample-internal-render-token"})
            self.assertEqual(response.status_code, 200, response.text[:100] if response.status_code != 200 else "")
            self.assertTrue(response.content.startswith(b"%PDF-"))
            self.assertEqual(response.headers["cache-control"], "no-store")

    def test_namespaced_python_plugin_routes_share_the_authentication_and_render_contract(self):
        with patch.dict(os.environ, {"APP_DOCUMENT_RENDER_TOKEN": "sample-internal-render-token"}):
            client = TestClient(app)
            body = {"template": SOURCE["template"], "values": VALUES}
            self.assertEqual(client.post("/plugins/documents/validate", json=body).status_code, 401)
            headers = {"Authorization": "Bearer sample-internal-render-token"}
            self.assertEqual(client.post("/plugins/documents/validate", json=body, headers=headers).status_code, 200)
            result = client.post("/plugins/documents/render", json=body, headers=headers)
            self.assertEqual(result.status_code, 200)
            self.assertTrue(result.content.startswith(b"%PDF-"))

    def test_worker_only_dispatches_reviewed_plugin_operations(self):
        from plugins.registry import render_plugin
        with self.assertRaises(ValueError):
            render_plugin("os.system", {})


if __name__ == "__main__": unittest.main()
