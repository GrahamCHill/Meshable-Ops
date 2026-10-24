#!/usr/bin/env python3
"""Keep published template versions aligned with the application release used by plugin checks."""
import argparse
import json
import re
from pathlib import Path
from xml.etree import ElementTree

ROOT = Path(__file__).resolve().parents[2]


def check_release_version(expected=None, root=ROOT):
    project = ElementTree.parse(root / "backend/pom.xml").getroot()
    backend = project.findtext("{http://maven.apache.org/POM/4.0.0}version")
    frontend = json.loads((root / "frontend/package.json").read_text(encoding="utf-8"))["version"]
    pattern = r"(?:0|[1-9][0-9]*)\.(?:0|[1-9][0-9]*)\.(?:0|[1-9][0-9]*)(?:-[0-9A-Za-z.-]+)?(?:\+[0-9A-Za-z.-]+)?"
    if not backend or not re.fullmatch(pattern, backend) or frontend != backend:
        raise ValueError("Set matching semantic release versions in backend/pom.xml and frontend/package.json")
    if expected is not None and expected != backend:
        raise ValueError(f"Requested template version {expected} differs from application version {backend}; bump and test the application before publishing")
    return backend


if __name__ == "__main__":
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("expected", nargs="?")
    args = parser.parse_args()
    try:
        print(f"Application release version: {check_release_version(args.expected)}")
    except (ValueError, KeyError, ElementTree.ParseError) as error:
        raise SystemExit(str(error))
