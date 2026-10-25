import unittest
from uuid import UUID

from plugins.knowledge.renderer import KnowledgeBook, book_html, render


class KnowledgeBookTest(unittest.TestCase):
    def value(self):
        first, second = UUID(int=1), UUID(int=2)
        return {'title': 'Team handbook', 'cover': {'html': '<h1>{{title}}</h1><p>{{documentCount}} documents</p>', 'title': 'Team handbook', 'documentCount': '2'},
                'pages': [{'id': str(first), 'title': 'Getting started', 'html': f'<h2 id="page-{first}-overview">Overview</h2><p>Welcome to the team.</p><p><a href="#page-{second}">Runbook</a></p>'},
                          {'id': str(second), 'title': 'Runbook', 'html': '<p>Publish the current release.</p>'}]}

    def test_selected_pages_headings_and_internal_links_have_bookmark_destinations(self):
        from weasyprint import HTML
        from resources import inline_only
        value = self.value()
        html = book_html(value)
        self.assertLess(html.index('id="page-00000000-0000-0000-0000-000000000001"'), html.index('id="page-00000000-0000-0000-0000-000000000002"'))
        body = HTML(string=html, url_fetcher=inline_only).render()
        self.assertEqual(len(body.pages), 3)
        self.assertIn('contents', body.pages[0].anchors)
        self.assertIn('page-00000000-0000-0000-0000-000000000001', body.pages[1].anchors)
        self.assertIn('page-00000000-0000-0000-0000-000000000002', body.pages[2].anchors)
        tree = body.make_bookmark_tree()
        self.assertEqual([node[0] for node in tree], ['Contents', 'Getting started', 'Runbook'])
        self.assertEqual(tree[1][2][0][0], 'Overview')
        texts = [box.text for box in body.pages[0]._page_box.descendants() if hasattr(box, 'text')]
        self.assertIn('3', texts)  # Physical page after cover + contents.
        self.assertIn('4', texts)
        self.assertTrue(render(value).startswith(b'%PDF-'))

    def test_paginated_documents_keep_the_next_contents_entry_correct(self):
        from weasyprint import HTML
        from resources import inline_only
        value = self.value()
        value['pages'][0]['html'] += '<p>' + ('Long documentation. ' * 2500) + '</p>'
        body = HTML(string=book_html(value), url_fetcher=inline_only).render()
        destination = next(index + 2 for index, page in enumerate(body.pages) if 'page-00000000-0000-0000-0000-000000000002' in page.anchors)
        self.assertGreater(destination, 4)
        texts = [box.text for box in body.pages[0]._page_box.descendants() if hasattr(box, 'text')]
        self.assertIn(str(destination), texts)

    def test_images_and_rendered_mermaid_are_embedded_rasters(self):
        from report_cover import EMPTY_IMAGE
        value = self.value()
        value['pages'][0]['html'] += f'<p><img src="{EMPTY_IMAGE}" alt="Architecture"></p><div class="knowledge-diagram"><img src="{EMPTY_IMAGE}" alt="Mermaid diagram"></div>'
        self.assertEqual(book_html(value).count(EMPTY_IMAGE), 2)
        self.assertTrue(render(value).startswith(b'%PDF-'))

    def test_active_content_external_resources_and_invalid_heading_anchors_are_rejected(self):
        for html in ['<script>stolen()</script>', '<iframe src="file:///etc/passwd"></iframe>', '<img src="http://127.0.0.1/private">',
                     '<p style="background:url(file:///etc/passwd)">Secret</p>', '<a href="javascript:alert(1)">Bad</a>', '<h1 id="contents">Spoof</h1>',
                     '<img src="data:image/svg+xml;base64,PHN2Zz4=">', '<img src="x.png" onerror="alert(1)">']:
            with self.subTest(html=html), self.assertRaises(ValueError):
                value = self.value(); value['pages'][0]['html'] = html; book_html(value)

    def test_empty_duplicate_and_oversized_selections_fail(self):
        value = self.value(); value['pages'] = []
        with self.assertRaises(ValueError):
            KnowledgeBook.model_validate(value)
        value = self.value(); value['pages'].append(value['pages'][0])
        with self.assertRaises(ValueError):
            book_html(value)
        value = self.value(); value['pages'] *= 26
        with self.assertRaises(ValueError):
            KnowledgeBook.model_validate(value)

    def test_custom_cover_tokens_are_escaped_and_fit_exactly_one_page(self):
        from report_cover import cover_html
        value = self.value()
        value['cover']['title'] = '<script>Title</script>'
        cover = cover_html(value['cover'])
        self.assertIn('&lt;script&gt;Title&lt;/script&gt;', cover)
        self.assertIn('2 documents', cover)
        value['cover']['html'] = '<h1>Too tall</h1><div style="height:600mm">Overflow</div>'
        with self.assertRaises(ValueError):
            render(value)
