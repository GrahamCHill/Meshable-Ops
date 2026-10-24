# Supplied quality report remediation

The supplied Sonar text contains **50 displayed findings out of a reported 492**. This pass covers those displayed findings; the remaining findings require their own export. A new Sonar analysis is needed to confirm remote issue status and ratings.

| Area | Change or reviewed rationale |
| --- | --- |
| Environment-file paths | Provisioning accepts only `.env` / `.env.production`, rejects parent traversal, symlink/junction ancestors and file aliases, and never overwrites a first-created environment. Linux operations walk directory handles with `O_NOFOLLOW`; writes/permissions use the opened descriptor. Tests exercise traversal, symlinks, hardlinks and preservation. |
| Dependency installation | Renderer dependencies are resolved with hashes and installed from binary wheels only. Frontend CI/container installs disable dependency lifecycle scripts. Package build commands remain explicit. |
| Regular expressions | Bounded split-based email validation, linear slash/parent-folder handling, and possessive signature-marker whitespace/underscore matching avoid unnecessary backtracking. |
| Accessibility | Native calendar buttons support keyboard creation without nested buttons, scrollable notification entries use native disclosure controls, assistant Escape handling is scoped through a document listener, and automatic focus is limited to explicit dialog/launcher behavior. |
| Maintainability | Explicit locale sorting, `Number.parseInt`, non-shadowing loop names, handled embed mounting, page titles, clearer image alternatives, and shared access-feature constants. |
| Example endpoints | User-facing Ollama/S3 examples use HTTPS; Teams Adaptive Cards uses the HTTPS schema identifier. Local HTTP services can still be configured where an adapter explicitly permits them; production traffic should use TLS. |

## Protocol and negative-test hotspots

TOTP deliberately retains **HMAC-SHA1** for HOTP/TOTP interoperability and existing authenticator enrollments. This is a keyed MAC, not unkeyed SHA-1 for password hashing or signatures. Changing the algorithm without re-enrollment would break existing MFA. RFC 6238 vectors and replay/window checks remain tested. See [RFC 6238](https://www.rfc-editor.org/rfc/rfc6238) and [RFC 4226](https://www.rfc-editor.org/rfc/rfc4226). Passwords continue to use the application's salted password encoder.

CalDAV XML parser `http://...` feature strings are exact parser identifiers. They make no HTTP requests and cannot be changed to HTTPS aliases. The parser enables secure processing, disables DOCTYPE, external entities and XInclude, and denies external DTD/schema access. Existing hostile XML tests remain in place. See [JAXP security guidance](https://docs.oracle.com/en/java/javase/21/security/java-api-xml-processing-jaxp-security-guide.html).

Webhook private addresses are negative SSRF test inputs. Byte-constructed fixtures avoid DNS and cover loopback, private, link-local, CGNAT, benchmarking, multicast and IPv6 ranges. An explicit loopback rejection fixture remains documented at its use. These tests do not authorize production private destinations or weaken the resolver.

Narrow `NOSONAR` comments record the protocol/negative-test rationale where a literal must remain. They are not blanket exclusions. Security hotspots should be reviewed with this context in the scanner; a suppressed warning alone is not evidence of safety.
