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

    def test_index_table_labels_content_and_page_columns(self):
        from report import index_html
        html=index_html(ReportDocument.model_validate(self.value()),4)
        self.assertIn('<thead><tr><th scope="col">Content</th><th scope="col">Page</th></tr></thead>',html)

    def test_rejects_oversized_or_ragged_tables(self):
        value = self.value(); value["sections"][0]["rows"] = [["one", "two"]]
        with self.assertRaises(ValueError):
            ReportDocument.model_validate(value)

    def test_lead_time_explains_start_to_completion_and_missing_start_dates(self):
        value = self.value()
        value['leadTime'] = {'missingStart': 2, 'completedTickets': 3, 'sampleSize': 1, 'averageDays': 4, 'creationAverageDays': 8, 'creationSampleSize': 2, 'missingCreation': 1}
        html = report_html(value)
        self.assertIn('from the ticket start date to its recorded completion date', html)
        self.assertIn('2 completed tickets have no start date', html)
        self.assertNotIn('creation-to-completion', html)
        self.assertNotIn('Recorded cycle time', html)
        self.assertIn('From creation to completion',html)
        self.assertIn('8.0 days',html)
        self.assertIn('average across 2 measured tickets',html)

    def test_numbered_pages_follow_the_requested_order_and_export_cells_are_bounded(self):
        value = self.value()
        value['sections'][0]['rows'] = [['longword' * 400]]
        html = report_html(value)
        anchors = ['id="report-summary"', 'id="report-insights"', 'id="report-sprints"', 'id="report-leadtime"', 'id="ticket-export"']
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

    def test_both_duration_metrics_and_eight_boards_keep_the_fixed_analytical_page_order(self):
        from report import render_report
        value=self.value()
        value['leadTime']={'averageDays':4,'creationAverageDays':8,'boards':[{'board':('Delivery team '+str(index)+' ') * 5,'count':10,'averageDays':4} for index in range(8)]}
        self.assertTrue(render_report(value).startswith(b'%PDF-'))

    def test_duration_distribution_rejects_inconsistent_sample_counts(self):
        value=self.value()
        value['leadTime']={'sampleSize':2,'completedTickets':2,'distribution':[{'label':'1-2 days','count':1}]}
        with self.assertRaises(ValueError):
            ReportDocument.model_validate(value)

    def test_sprint_monitor_has_explicit_live_and_completed_measurements_and_exclusions(self):
        value=self.value()
        value['sprintMonitor']={'assignedTickets':3,'completedAverageDays':4,'completedSampleSize':2,'openAverageDays':8,'openSampleSize':1,'oldestOpenDays':8,'missingCompletion':1}
        html=report_html(value)
        self.assertIn('Sprint monitor',html)
        self.assertIn('2 completed durations and 1 open ages measured',html)
        self.assertIn('1 missing completion dates',html)

    def test_sprint_page_fits_six_groups_and_six_ticket_rows_with_long_names(self):
        from report import render_report
        value=self.value()
        value['sprintMonitor']={'assignedTickets':12,'totalSprints':6,
            'sprints':[{'name':'Sprint '*14,'board':'Delivery '*22,'startDate':'2026-10-01','endDate':'2026-10-14','total':2,'completed':1,'completedAverageDays':4,'openAverageDays':8} for _ in range(6)],
            'longestTickets':[{'reference':'OPS-123','title':'Long ticket title '*8,'board':'Delivery '*22,'sprint':'Sprint '*14,'sprintStart':'2026-10-01','measuredThrough':'2026-10-10','days':9,'completed':False} for _ in range(6)]}
        self.assertTrue(render_report(value).startswith(b'%PDF-'))


if __name__ == "__main__":
    unittest.main()
