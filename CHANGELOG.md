# Changelog

All notable changes to `floop-kotlin-sdk` are documented here.

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
