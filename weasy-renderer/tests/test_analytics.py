import copy
import unittest

from plugins.analytics.renderer import AnalyticsPdf, report_html, render


def sample():
    return {
        'title': 'Web traffic insights', 'organization': 'Meshable Ops', 'contextName': 'Autumn launch',
        'contextKind': 'PROJECT', 'provider': 'Google Analytics', 'generatedAt': '2026-10-25T12:00:00Z',
        'links': [{
            'label': 'Launch landing page', 'selection': {'resourceId': '123', 'filter': 'PAGE', 'selector': '/launch'},
            'snapshot': {'from': '2026-10-01', 'to': '2026-10-24', 'syncedAt': '2026-10-25T10:00:00Z', 'currency': 'ZAR',
                         'metrics': [{'key': 'sessions', 'label': 'Sessions', 'value': 12890, 'unit': 'NUMBER'},
                                     {'key': 'revenue', 'label': 'Revenue', 'value': 48500.25, 'unit': 'CURRENCY'},
                                     {'key': 'engagement', 'label': 'Engagement rate', 'value': 71.22, 'unit': 'PERCENT'},
                                     {'key': 'missing', 'label': 'Withheld metric', 'value': None, 'unit': 'NUMBER'}],
                         'notes': ['Provider attribution applies.', 'Missing metrics are unavailable, not zero.']}
        }]
    }


class AnalyticsExportTest(unittest.TestCase):
    def test_exports_exact_scope_dates_currency_missing_values_and_escaped_labels(self):
        value = sample()
        value['links'][0]['label'] = '<img src="https://example.test/secret">'
        html = report_html(value)
        self.assertIn('&lt;img src=', html)
        self.assertNotIn('<img src=', html)
        for expected in ['/launch', '2026-10-01', '2026-10-24', '48,500.25 ZAR', '71.22%', 'Unavailable', 'not combined']:
            self.assertIn(expected, html)

    def test_rejects_unbounded_invalid_and_nonfinite_data(self):
        value = sample()
        value['links'] *= 31
        with self.assertRaises(ValueError):
            AnalyticsPdf.model_validate(value)
        value = sample()
        value['links'][0]['snapshot']['metrics'][0]['value'] = float('inf')
        with self.assertRaises(ValueError):
            AnalyticsPdf.model_validate(value)
        value = sample()
        value['links'][0]['snapshot']['to'] = '2026-12-01'
        with self.assertRaises(ValueError):
            AnalyticsPdf.model_validate(value)

    def test_real_pdf_keeps_snapshot_scopes_on_separate_pages(self):
        from weasyprint import HTML
        from resources import inline_only
        value = sample()
        second = copy.deepcopy(value['links'][0])
        second['label'] = 'Campaign web traffic'
        second['selection'] = {'resourceId': '123', 'filter': 'CAMPAIGN', 'selector': 'autumn-launch'}
        value['links'].append(second)
        pdf = render(value)
        self.assertTrue(pdf.startswith(b'%PDF-'))
        document = HTML(string=report_html(value), url_fetcher=inline_only).render()
        self.assertEqual(len(document.pages), 2)
        self.assertIn('analytics-snapshot-0', document.pages[0].anchors)
        self.assertIn('analytics-snapshot-1', document.pages[1].anchors)


if __name__ == '__main__':
    unittest.main()
