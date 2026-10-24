import unittest
from report_cover import cover_html, validate_cover_html, render_cover_document


class ReportCoverTest(unittest.TestCase):
    def test_branding_and_named_images_resolve_in_images_and_css_without_external_requests(self):
        from report_cover import EMPTY_IMAGE
        source='<h1>{{siteName}}</h1><p>{{companyName}}</p><img src="{{siteLogo}}"><img src="images/Banner.PNG"><div style="background-image:url(\'art/Banner.PNG\')">Cover</div>'
        html=cover_html({'html':source,'siteName':'Site & Co','companyName':'Company <Ltd>','siteLogo':EMPTY_IMAGE,'assets':{'banner.png':EMPTY_IMAGE}})
        self.assertIn('Site &amp; Co',html)
        self.assertIn('Company &lt;Ltd&gt;',html)
        self.assertEqual(html.count(EMPTY_IMAGE),3)
        self.assertNotIn('images/Banner.PNG',html)
        self.assertEqual(len(render_cover_document({'html':source,'assets':{'banner.png':EMPTY_IMAGE}}).pages),1)

    def test_missing_duplicate_or_unsafe_image_resources_cannot_silently_render(self):
        from report_cover import EMPTY_IMAGE
        for value in [
            {'html':'<img src="missing.png">'},
            {'html':'<img src="../secret.png">'},
            {'html':'<style>body{background:url(https://example.test/a.png)}</style>'},
            {'html':'<img src="{{project}}">'},
            {'html':'<p>Cover</p>','assets':{'a.png':EMPTY_IMAGE,'A.PNG':EMPTY_IMAGE}},
            {'html':'<p>Cover</p>','assets':{'a.png':'data:image/svg+xml;base64,PHN2Zz4='}},
        ]:
            with self.subTest(value=value), self.assertRaises(ValueError):
                cover_html(value)
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
