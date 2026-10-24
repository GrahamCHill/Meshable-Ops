#!/usr/bin/env python3
"""Read-only smoke check of crawler exclusion on a running application origin."""
import argparse
from html.parser import HTMLParser
from urllib.error import HTTPError
from urllib.parse import urlsplit
from urllib.request import HTTPRedirectHandler, Request, build_opener
from urllib.robotparser import RobotFileParser

DIRECTIVES = {"noindex", "nofollow", "noarchive", "nosnippet", "noimageindex"}


class NoRedirects(HTTPRedirectHandler):
    def redirect_request(self, req, fp, code, msg, headers, newurl):
        return None


class PageMetadata(HTMLParser):
    def __init__(self):
        super().__init__()
        self.robots = set()
        self.assets = set()

    def handle_starttag(self, tag, attrs):
        values = dict(attrs)
        if tag == "meta" and values.get("name", "").lower() == "robots":
            self.robots.update(part.strip().lower() for part in values.get("content", "").split(","))
        for attribute in ("src", "href"):
            path = values.get(attribute, "")
            if path.startswith("/assets/") and not urlsplit(path).netloc:
                self.assets.add(path)


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--base-url", default="http://localhost:3000")
    args = parser.parse_args()
    base = args.base_url.rstrip("/")
    origin = urlsplit(base)
    if origin.scheme not in {"http", "https"} or not origin.hostname or origin.username or origin.password or origin.path or origin.query or origin.fragment:
        parser.error("Use an HTTP(S) origin without credentials, path, query, or fragment")
    opener = build_opener(NoRedirects())

    def read(path):
        request = Request(base + path, headers={"User-Agent": "Meshable Ops-Deployment-Check"})
        try:
            response = opener.open(request, timeout=30)
        except HTTPError as error:
            response = error
        with response:
            directives = {part.strip().lower() for value in response.headers.get_all("X-Robots-Tag", []) for part in value.split(",")}
            assert DIRECTIVES <= directives, f"Crawler header missing directives on {path} ({response.status})"
            return response.status, response.headers, response.read(128 * 1024).decode("utf-8", errors="replace")

    status, headers, body = read("/robots.txt")
    assert status == 200 and headers.get_content_type() == "text/plain", "robots.txt must be publicly served as text/plain"
    robots = RobotFileParser(base + "/robots.txt")
    robots.parse(body.splitlines())
    for agent in ("Googlebot", "bingbot", "GPTBot", "OAI-SearchBot", "ClaudeBot", "PerplexityBot", "CCBot", "Google-Extended", "UnlistedFutureCrawler"):
        for path in ("/", "/login", "/documents", "/api/", "/shared/check"):
            assert not robots.can_fetch(agent, base + path), f"robots.txt allows {agent}: {path}"

    metadata = PageMetadata()
    for path in ("/", "/login"):
        status, _, body = read(path)
        assert status == 200, f"Workspace HTML must remain available at {path}"
        page = PageMetadata()
        page.feed(body)
        assert DIRECTIVES <= page.robots, f"HTML robots metadata missing directives at {path}"
        metadata.assets.update(page.assets)
    assert metadata.assets, "Built frontend assets were not found"
    routes = ["/admin", "/shared/crawler-policy-check", "/brand-mark.svg", "/assets/crawler-policy-missing.js", "/api/branding", "/api/branding/login-images/0/crawler-policy-missing",
              "/api/health", "/api/admin/plugins", "/api/shared/crawler-policy-check/pdf", "/embedded/crawler-policy-check", "/superset/chart/list"]
    for path in sorted(metadata.assets) + routes:
        status, _, _ = read(path)
        print(f"Crawler headers checked: {status} {path}")
    print("Wildcard crawl exclusion, HTML directives, and page/API/asset/embed/error response headers passed.")


if __name__ == "__main__":
    main()
