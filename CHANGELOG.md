# Changelog

All notable changes to `floop-kotlin-sdk` are documented here.

## [0.1.0-alpha.2] — 2026-04-28

### Changed — heads-up for downstream consumers

- **OkHttp 4.12.0 → 5.3.2** (transitive). Users of the SDK now pull
  OkHttp 5 onto their classpath. OkHttp 5 has a new `mockwebserver3`
  namespace and changes a handful of internal APIs; the legacy
  `okhttp3.mockwebserver.*` import path is still provided as a
  deprecation-flagged compat shim in 5.3.2 (which is why the existing
  `TransportTest.kt` still compiles unchanged). If your project also
  depends on OkHttp 4 directly (or on another library that does),
  pin a single major or test the upgrade path before production —
  classpath conflicts between OkHttp 4 and 5 are the most common
  surprise. The compat shim is unlikely to survive into OkHttp 6;
  a migration of `TransportTest.kt` to `mockwebserver3` is queued for
  alpha.3.
- **Kotlin compiler 2.0.21 → 2.3.21** (build-time). User-facing
  artifact still targets `jvmToolchain(11)`; Kotlin 2.3 is fully
  source-compatible with Kotlin 2.0 callers.
- **Gradle 8.10 → 9.4.1** (build-time). No effect on consumers.
- **kotlinx-serialization 1.7.3 → 1.11.0** and
  **kotlinx-coroutines 1.9.0 → 1.10.2** (transitive). Both are
  drop-in patch / minor bumps with no API breakage in the surface
  the SDK uses.

No SDK API changes. CI matrix runs JDK 17 / 21 (JDK 11 was dropped
from the matrix in the alpha-followup PR before this bundle merged,
so the OkHttp 5 + Kotlin 2.3 + Gradle 9 combination passes cleanly).

## [0.1.0-alpha.1] — 2026-04-25

First public release. Full parity with the Node, Python, Go, Rust, Ruby, PHP, and Swift SDKs. Distribution via JitPack during the alpha phase; Maven Central is the planned next step.

### Added

- `FloopFloop` client. Construct with `apiKey` plus optional `baseUrl`, `timeout`, `userAgentSuffix`, `httpClient`.
- Resource accessors: `projects`, `subdomains`, `secrets`, `library`, `usage`, `apiKeys`, `uploads`, `user`.
- `projects` — `create`, `list`, `get`, `status`, `cancel`, `reactivate`, `refine`, `conversations`, `stream` (cold `Flow<StatusEvent>`), `waitForLive`.
- `uploads.create` — two-step flow: presign against `/api/v1/uploads`, then direct `PUT` to the returned S3 URL. 5 MB cap, allowlisted MIME types, validated client-side.
- `FloopError` — single exception type. Exposes `code: String`, `status: Int`, `message: String`, `requestId: String?`, `retryAfter: Duration?`. `FloopErrorCode` object holds the 13 documented codes; unknown server codes pass through verbatim.
- `Retry-After` parsing handles both delta-seconds and HTTP-date.
- Concurrency: every method is `suspend`. The `stream()` method returns a cold `Flow<StatusEvent>`.
- Test suite uses OkHttp `MockWebServer` for offline integration coverage.

[0.1.0-alpha.1]: https://github.com/FloopFloopAI/floop-kotlin-sdk/releases/tag/v0.1.0-alpha.1
