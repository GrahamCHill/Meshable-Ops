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
        value = self.value(); value["sections"][0]["rows"] = [["x"]] * 2001
        with self.assertRaises(ValueError):
            ReportDocument.model_validate(value)


if __name__ == "__main__":
    unittest.main()
