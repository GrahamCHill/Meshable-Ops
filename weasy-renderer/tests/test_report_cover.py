import unittest
from report_cover import cover_html, validate_cover_html, render_cover_document


class ReportCoverTest(unittest.TestCase):
    def test_cover_tokens_are_escaped_visible_text(self):
        value = cover_html({"html": "<h1>{{project}}</h1><p>{{period}}</p>", "project": "<script>stolen</script>", "period": "October"})
        self.assertIn("&lt;script&gt;stolen&lt;/script&gt;", value)
        self.assertNotIn("<script>", value)

    def test_designs_cannot_fetch_resources_execute_content_or_move_tokens_into_attributes(self):
        examples = ['<script>alert(1)</script>', '<img src="https://example.test/image.png">', '<div onclick="alert(1)">X</div>',
                    '<style>@import "https://example.test/theme.css";</style>', '<p style="background:url(file:///etc/passwd)">X</p>',
                    '<style>p{background:u/**/rl(https://example.test/a)}</style>', '<a href="https://example.test">X</a>',
                    '<div class="{{project}}">X</div>', '<style>p{color:{{project}}}</style>', '<h1>{{unknown}}</h1>']
        for source in examples:
            with self.subTest(source=source), self.assertRaises(ValueError):
                validate_cover_html(source)

    def test_preview_enforces_one_page_and_styles_stay_in_the_cover_document(self):
        self.assertEqual(len(render_cover_document({"html": "<style>body{color:red}</style><h1>Cover</h1>"}).pages), 1)
        with self.assertRaises(ValueError):
            render_cover_document({"html": '<div style="height:900mm">Too tall</div>'})
