# FloopFloop Kotlin SDK

[![CI](https://img.shields.io/github/actions/workflow/status/FloopFloopAI/floop-kotlin-sdk/ci.yml?branch=main&logo=github&label=ci)](https://github.com/FloopFloopAI/floop-kotlin-sdk/actions/workflows/ci.yml)
[![JitPack](https://img.shields.io/jitpack/version/com.github.FloopFloopAI/floop-kotlin-sdk?logo=jitpack)](https://jitpack.io/#FloopFloopAI/floop-kotlin-sdk)
[![Kotlin](https://img.shields.io/badge/kotlin-2.0%2B-blueviolet?logo=kotlin&logoColor=white)](https://kotlinlang.org)
[![JVM](https://img.shields.io/badge/jvm-11%2B-orange?logo=openjdk&logoColor=white)](https://openjdk.org)
[![License: MIT](https://img.shields.io/github/license/FloopFloopAI/floop-kotlin-sdk)](LICENSE)

Official Kotlin SDK for the [FloopFloop](https://www.floopfloop.com) API. Build, refine, and manage FloopFloop projects from any JVM codebase — Kotlin server (Ktor / Spring), Android, or plain Java.

Same surface as the [Node](https://github.com/FloopFloopAI/floop-node-sdk), [Python](https://github.com/FloopFloopAI/floop-python-sdk), [Go](https://github.com/FloopFloopAI/floop-go-sdk), [Rust](https://github.com/FloopFloopAI/floop-rust-sdk), [Ruby](https://github.com/FloopFloopAI/floop-ruby-sdk), [PHP](https://github.com/FloopFloopAI/floop-php-sdk), and [Swift](https://github.com/FloopFloopAI/floop-swift-sdk) SDKs.

## Install

Distribution is via **JitPack** during the alpha phase; Maven Central is the planned next step.

**Gradle (Kotlin DSL):**

```kotlin
repositories {
    mavenCentral()
    maven("https://jitpack.io")
}

dependencies {
    implementation("com.github.FloopFloopAI:floop-kotlin-sdk:0.1.0-alpha.1")
}
```

**Gradle (Groovy):**

```groovy
repositories {
    mavenCentral()
    maven { url 'https://jitpack.io' }
}

dependencies {
    implementation 'com.github.FloopFloopAI:floop-kotlin-sdk:0.1.0-alpha.1'
}
```

**Maven:**

```xml
<repositories>
  <repository>
    <id>jitpack.io</id>
    <url>https://jitpack.io</url>
  </repository>
</repositories>

<dependency>
  <groupId>com.github.FloopFloopAI</groupId>
  <artifactId>floop-kotlin-sdk</artifactId>
  <version>0.1.0-alpha.1</version>
</dependency>
```

Requires Kotlin 2.0+ and Java 11+. Two runtime deps: `okhttp` (HTTP transport) and `kotlinx-serialization-json` + `kotlinx-coroutines-core`.

## Quickstart

```kotlin
import com.floopfloop.sdk.FloopFloop
import com.floopfloop.sdk.resources.CreateProjectInput
import kotlinx.coroutines.runBlocking

fun main() = runBlocking {
    val client = FloopFloop(apiKey = System.getenv("FLOOP_API_KEY"))

    val created = client.projects.create(CreateProjectInput(
        prompt = "A landing page for a cat cafe",
        subdomain = "cat-cafe",
        botType = "site",
    ))

    val live = client.projects.waitForLive(created.project.id)
    println("Live at: ${live.url ?: "—"}")
}
```

Grab an API key at [www.floopfloop.com/account/api-keys](https://www.floopfloop.com/account/api-keys). This SDK is for **server-side / desktop / Android-with-your-own-backend** use today. For first-party Android apps shipping in the Play Store, **don't embed an API key client-side** — see [Android note](#android-note) below.

## Resources

| Resource | Methods |
|---|---|
| `client.projects` | `create`, `list`, `get`, `status`, `cancel`, `reactivate`, `refine`, `conversations`, `stream` (Flow), `waitForLive` |
| `client.subdomains` | `check`, `suggest` |
| `client.secrets` | `list`, `set`, `remove` |
| `client.library` | `list`, `clone` |
| `client.usage` | `summary` |
| `client.apiKeys` | `list`, `create`, `remove` (accepts id or name) |
| `client.uploads` | `create` (presign + direct S3 PUT) |
| `client.user` | `me` |

Every method is `suspend`. Non-2xx responses throw `FloopError`.

## Streaming a build

```kotlin
client.projects.stream("my-subdomain").collect { event ->
    println("[${event.status}] step ${event.step}/${event.totalSteps} — ${event.message}")
}
```

The stream is a cold `Flow<StatusEvent>` that polls the status endpoint and emits each de-duplicated snapshot. Terminates on `live`, `failed`, `cancelled`, or `archived`. Throws `FloopError` with `code = "BUILD_FAILED" / "BUILD_CANCELLED" / "TIMEOUT"` on non-success terminals.

`waitForLive` is a convenience wrapper that returns the hydrated `Project` once the build reaches `live`.

## Error handling

```kotlin
import com.floopfloop.sdk.FloopError
import com.floopfloop.sdk.FloopErrorCode

try {
    client.projects.status("p_nonexistent")
} catch (err: FloopError) {
    if (err.code == FloopErrorCode.RATE_LIMITED && err.retryAfter != null) {
        kotlinx.coroutines.delay(err.retryAfter!!)
        // retry…
    }
    throw err
}
```

`FloopError` exposes:

- `code: String` — `"UNAUTHORIZED"`, `"FORBIDDEN"`, `"VALIDATION_ERROR"`, `"RATE_LIMITED"`, `"NOT_FOUND"`, `"CONFLICT"`, `"SERVICE_UNAVAILABLE"`, `"SERVER_ERROR"`, `"NETWORK_ERROR"`, `"TIMEOUT"`, `"BUILD_FAILED"`, `"BUILD_CANCELLED"`, `"UNKNOWN"`. The `FloopErrorCode` object exposes the same set as constants. Unknown server codes pass through verbatim.
- `status: Int` — HTTP status. `0` for transport-level failures.
- `requestId: String?` — `x-request-id` from the response. Quote it in bug reports.
- `retryAfter: Duration?` — populated from the `Retry-After` response header (delta-seconds OR HTTP-date).

## Configuration

```kotlin
import kotlin.time.Duration.Companion.seconds

val client = FloopFloop(
    apiKey = "flp_…",
    baseUrl = "https://staging.floopfloop.com",   // default: https://www.floopfloop.com
    timeout = 60.seconds,                          // default: 30s
    userAgentSuffix = "myapp/1.2.3",               // appended to floopfloop-kotlin-sdk/<version>
    httpClient = OkHttpClient(),                   // bring-your-own for proxies / interceptors
)
```

## Android note

Don't embed an API key in an Android app — anyone with the APK can extract it. For shipped Android apps, the right pattern is:

1. **Your app** talks to **your backend** (signed in via Sign in with Google / etc.).
2. **Your backend** holds the `flp_*` API key and talks to FloopFloop on the user's behalf.

A first-party Android auth flow (so users can log into FloopFloop from a FloopFloop-built mobile app directly) is a separate piece of work tracked on the platform roadmap.

If your Android app needs to talk to FloopFloop *during development* and you understand the risk, the SDK works on Android too — Android 7+ ships the OkHttp+Kotlin coroutines combo this SDK depends on.

## Development

```bash
./gradlew build
./gradlew test
```

Tests use OkHttp's `MockWebServer` for offline integration coverage — no network, no API key needed.

## License

MIT — see [LICENSE](LICENSE).
