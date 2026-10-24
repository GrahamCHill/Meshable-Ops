import unittest
from report import ReportDocument, report_html


class ProjectReportsTest(unittest.TestCase):
    def value(self):
        return {"title": "Sprint report", "organization": "Meshable Ops", "subtitle": "Project", "period": "2026-10-01 to 2026-10-14",
                "summary": ["1 ticket"], "sections": [{"title": "Board", "columns": ["Task"], "rows": [["<script>private</script>"]]}]}

    def test_report_escapes_all_user_content_and_forces_a_separate_cover(self):
        html = report_html(self.value())
        self.assertIn("&lt;script&gt;private&lt;/script&gt;", html)
        self.assertNotIn("<script>", html)
        self.assertIn("break-after:page", html)
        self.assertIn("table-header-group", html)

    def test_rejects_oversized_or_ragged_tables(self):
        value = self.value(); value["sections"][0]["rows"] = [["one", "two"]]
        with self.assertRaises(ValueError):
            ReportDocument.model_validate(value)

    def test_numbered_pages_follow_the_requested_order_and_export_cells_are_bounded(self):
        value = self.value()
        value['sections'][0]['rows'] = [['longword' * 400]]
        html = report_html(value)
        anchors = ['id="report-summary"', 'id="report-insights"', 'id="report-leadtime"', 'id="ticket-export"']
        self.assertEqual([html.index(anchor) for anchor in anchors], sorted(html.index(anchor) for anchor in anchors))
        self.assertNotIn('longword' * 40, html)
        self.assertIn('table-layout:fixed', html)
        value = self.value(); value["sections"][0]["rows"] = [["x"]] * 2001
        with self.assertRaises(ValueError):
            ReportDocument.model_validate(value)

    def test_shared_report_worker_returns_a_real_pdf_independently_of_template_operations(self):
        from fastapi.testclient import TestClient
        from app import app
        result = TestClient(app).post('/reports/render', json={'report': self.value()})
        self.assertEqual(result.status_code, 200)
        self.assertTrue(result.content.startswith(b'%PDF-'))


if __name__ == "__main__":
    unittest.main()
