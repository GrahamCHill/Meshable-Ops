import importlib.util
import json
import tempfile
import unittest
from pathlib import Path

spec = importlib.util.spec_from_file_location("release_version", Path(__file__).resolve().parents[1] / "check-release-version.py")
release = importlib.util.module_from_spec(spec)
spec.loader.exec_module(release)


class ReleaseVersionTests(unittest.TestCase):
    def test_published_version_must_match_both_application_packages(self):
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            (root / "backend").mkdir()
            (root / "frontend").mkdir()
            (root / "backend/pom.xml").write_text('<project xmlns="http://maven.apache.org/POM/4.0.0"><parent><version>3.3.2</version></parent><version>1.2.3</version></project>', encoding="utf-8")
            (root / "frontend/package.json").write_text(json.dumps({"version": "1.2.3"}), encoding="utf-8")
            self.assertEqual(release.check_release_version("1.2.3", root), "1.2.3")
            with self.assertRaisesRegex(ValueError, "differs"):
                release.check_release_version("2.0.0", root)
            (root / "frontend/package.json").write_text(json.dumps({"version": "1.2.4"}), encoding="utf-8")
            with self.assertRaisesRegex(ValueError, "matching"):
                release.check_release_version(root=root)


if __name__ == "__main__":
    unittest.main()
