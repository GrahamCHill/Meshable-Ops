# Search and AI crawler exclusion

WeaveOps is a private business workspace. Every installation ships with crawler exclusion enabled; there is no admin switch that can accidentally publish the app to crawlers.

| Layer | Policy |
| --- | --- |
| Public `/robots.txt` | `User-agent: *` with `Disallow: /`, applying to all compliant search/SEO and AI crawlers, including future crawler names |
| Frontend HTML | A robots meta tag with `noindex, nofollow, noarchive, nosnippet, noimageindex`, available before JavaScript runs |
| Frontend nginx | The same `X-Robots-Tag` on pages, assets, API responses, shared files, Superset embeds/proxy routes, redirects, and errors |
| Backend security chain | The header on direct backend responses, including public routes, PDF delivery, denied requests, and missing routes |
| Deployment proxies | Caddy and host nginx examples also set the header |
| Native frontend development | Vite dev/preview servers set the header; Vite serves the same public robots file |

The nginx static-asset location includes the common headers explicitly because its own cache header prevents nginx from inheriting server-level `add_header` directives. Retain that include when editing caching or adding locations with their own headers. The runtime image copies the shared headers snippet with its nginx configuration.

## Limits

The [Robots Exclusion Protocol](https://www.rfc-editor.org/rfc/rfc9309.html) is a cooperative instruction, not access authorization. Bots can ignore it or impersonate normal browsers. Authentication, ownership checks, MFA where enabled, and network controls remain responsible for protecting private data. These directives do not affect the app's authenticated internal storage/vector search or optional AI integrations.

`robots.txt` blocks crawling; `noindex` asks crawlers that receive a response to exclude it from their indexes. A crawler blocked by robots cannot read a page's metadata/headers, and previously indexed or externally referenced URLs can still appear as URL-only listings. For an installation that was already public, use the relevant search provider's removal tools and retain authenticated access. Changes do not retroactively erase existing indexes or model training data. See [Google's indexing directive documentation](https://developers.google.com/search/docs/crawling-indexing/robots-meta-tag).

## Deploy and verify

Rebuild/recreate the frontend and backend using the installation's existing Compose project, environment, and override. Reload an external reverse proxy if you use the updated host examples. Database volumes and credentials do not change.

Run the read-only smoke check against the deployed application origin:

```bash
python deploy/github/check-crawler-policy.py --base-url http://localhost:3000
```

For production, supply the HTTPS application origin. The check verifies the public plain-text robots file for common search/AI agents plus an unknown agent, the initial HTML meta tag, built assets, protected API paths, Superset paths, redirects, and errors. It sends no credentials and does not follow redirects. This checks delivered policy, not a third party's compliance. Backend regression coverage exercises the real Spring Security chain for public, PDF, denied, and missing-route responses.
