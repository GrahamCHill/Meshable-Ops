import time
import unittest
from backend.media import SIGNATURE_MARKER


class SignatureMarkerTests(unittest.TestCase):
    def test_supported_signature_markers_preserve_existing_templates(self):
        for value in ('{{signature}}', '_____', 'Signature: ______', 'signed by ______', '  Sign here: ______  '):
            self.assertIsNotNone(SIGNATURE_MARKER.search(value), value)
        for value in ('ordinary words', 'Signature: ____', '____x', 'Signature:\ntext'):
            self.assertIsNone(SIGNATURE_MARKER.search(value), value)

    def test_long_near_matches_do_not_backtrack_over_whitespace_and_underscores(self):
        started=time.monotonic()
        for value in (' ' * 100000 + 'x', 'Signature: ' + '_' * 100000 + 'x', ' ' * 100000 + '_' * 100000 + 'x'):
            self.assertIsNone(SIGNATURE_MARKER.search(value))
        self.assertLess(time.monotonic()-started, 3)
