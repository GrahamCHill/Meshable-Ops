# Storage listing performance

The performance target is **p99 below 200 ms** for storage reads with more than 2,000
files. A p99 target also bounds p95 and p50: the originally requested p95 of 500 ms
and p50 of 1 second are looser limits, not an increasing percentile sequence.

## Request work

- Application-root file counts come from one paginated, read-only namespace walk.
  Its result serves all root pages, rather than repeating the walk for each page.
  Counting does not create a bucket/container or missing folder markers. Explicit
  folder creation still creates markers.
- Directory browsing uses the provider's delimiter/hierarchy listing. It lists
  immediate children across every provider page; it does not recursively walk
  descendants to display a directory.
- Bounded Caffeine caches retain raw provider listings and counts. Reads after
  five seconds trigger a background refresh; entries expire after 30 seconds.
  Cold requests for the same key share a load. Each cache has a 100,000-unit
  weight budget, with a 256-unit minimum per entry and additional weight per
  listed entry/root. Refresh uses two threads and a 32-task queue. Listing calls
  use the existing bounded provider timeouts.
- Cache keys include provider, bucket/container, endpoint, region, credentials,
  configured namespace, directory root, and relative path as applicable. Keys
  retain detached connection values, and their diagnostic strings hide credentials.
  Upload attempts and explicit folder mutations invalidate the local caches.
  Changes made outside this process, including another replica, are observed by
  refresh/expiry.
- Directory responses read only index identity, checksum, status, and timestamp
  in one metadata projection query. They do not persist each file, generate
  embeddings, or wait for content extraction. Changed listings coalesce into
  PostgreSQL directory jobs. A dedicated worker indexes current entries; leases,
  claim tokens, revisions, and retry delays retain work across crashes and requests
  arriving during processing. Uploaded directory trees enqueue their affected
  parents without re-walking all existing files.
- Connection folder counts use one root projection for all enabled connections.
  Restricted file counts fetch paths once for the returned root page. Each
  listing uses one permission snapshot rather than resolving permissions per file.
  Root prefix calculation also shares one settings read for the returned page.
- The frontend displays directory folder/file totals and renders 100 rows per page.
  All provider entries remain available through directory pagination.

The caches contain no user-specific permission decisions. Current connection/root
visibility and path ACLs still apply to every request, including a cache hit.
Generated PDF export paths are excluded from ordinary responses and file counts.
Downloads and recursive index/archive walks continue to read storage directly.

New or changed files are immediately browsable/downloadable. Search metadata and
index status appear after the background worker finishes. Restricted file counts
still describe indexed, permitted files; a full reindex reconciles removed files.

## Reproducible measurement

`StorageWalkingBenchmarkTest` uses 2,500 immediate files, nested and empty folders,
a reserved export, actual Spring Data JPA/PostgreSQL queries and transactions,
the S3 SDK with three-page loopback HTTP responses, and JSON serialization.
It measures the first cold request, 200 warm requests, 200 requests with four
concurrent readers, and 100 forced cache misses after JVM warmup. Warm p99 above
200 ms fails this opt-in benchmark. Regular CI runs correctness tests without
machine-sensitive timing assertions.

Run against a disposable PostgreSQL database (the benchmark creates and drops an
isolated schema):

```bash
cd backend
export DOCUMENT_TEST_JDBC_URL='jdbc:postgresql://127.0.0.1:5432/storage_bench?user=storage_test'
export STORAGE_WALK_BENCHMARK=true
mvn --batch-mode --no-transfer-progress -Dtest=StorageWalkingBenchmarkTest \
  '-DargLine=-XX:ActiveProcessorCount=2 -Xmx512m' test
```

The Java option makes JVM sizing use two processors; it does not impose a host CPU
quota. The 512 MiB limit applies to the benchmark JVM, not PostgreSQL. These local
measurements exclude authentication middleware, reverse proxy, WAN/cloud latency,
and browser rendering. They are not a deployment-level latency guarantee.

On Windows, if the JDK cannot establish its selector's Unix-domain loopback socket,
set `java.io.tmpdir` and `jdk.net.unixdomain.tmpdir` to an existing short local path
in the test JVM arguments.

## Deployment validation

Local run on 2026-10-28: Windows, JetBrains Java 21.0.10, PostgreSQL 18,
`ActiveProcessorCount=2`, 512 MiB benchmark heap, loopback storage/database.

| Service operation | Samples | p50 | p95 | p99 |
| --- | ---: | ---: | ---: | ---: |
| Warm root listing and file counts | 200 | 5.78 ms | 9.83 ms | 11.34 ms |
| Warm 2,500-file directory listing | 200 | 30.04 ms | 41.49 ms | 51.07 ms |
| Directory listing, four concurrent readers | 200 | 35.05 ms | 55.34 ms | 78.90 ms |
| Directory listing with forced cache miss, warmed JVM | 100 | 52.85 ms | 64.13 ms | 70.40 ms |

The very first root listing took **386.20 ms** and the first directory listing
took **190.09 ms**. Each traversed three storage pages. These are individual
cold observations, not cold percentiles. The warm service target passed;
initial cold root loading did not meet 200 ms in this run.

Measure authenticated `/api/items`, `/api/storage-connections`, and
`/api/items/{id}/contents` requests on the actual deployed 2-vCPU/8-GB host and
configured storage provider. Keep cold starts/cache misses separate from warm
and concurrent traffic. Include out-of-process changes, local uploads, cache
refresh while indexing is active, and access revocation. Provider/network latency
still limits cold reads; do not infer a cold p99 guarantee from a warm-cache result.
