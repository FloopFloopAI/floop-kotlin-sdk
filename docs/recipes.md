# Cookbook

Concrete `floop-kotlin-sdk` patterns you can copy-paste. Every snippet uses only the SDK's public surface — no undocumented endpoints, no private helpers.

For the basics (install, client setup, resource tour) see the [README](../README.md). This file is the **"I know the basics, now how do I actually build X"** layer.

These recipes mirror the [Node](https://github.com/FloopFloopAI/floop-node-sdk/blob/main/docs/recipes.md), [Python](https://github.com/FloopFloopAI/floop-python-sdk/blob/main/docs/recipes.md), [Go](https://github.com/FloopFloopAI/floop-go-sdk/blob/main/docs/recipes.md), [Rust](https://github.com/FloopFloopAI/floop-rust-sdk/blob/main/docs/recipes.md), [Ruby](https://github.com/FloopFloopAI/floop-ruby-sdk/blob/main/docs/recipes.md), and [PHP](https://github.com/FloopFloopAI/floop-php-sdk/blob/main/docs/recipes.md) cookbooks, translated to Kotlin idioms (coroutines, `Flow`, `kotlin.time.Duration`).

All snippets assume:

```kotlin
import com.floopfloop.sdk.FloopFloop
import com.floopfloop.sdk.FloopError
import com.floopfloop.sdk.FloopErrorCode
import kotlinx.coroutines.runBlocking
```

Wrap top-level demo code in `runBlocking { ... }`; in real apps, call the `suspend` methods from your existing coroutine scope.

---

## 1. Ship a project from prompt to live URL

The canonical one-call flow: create, wait, done. `waitForLive` throws `FloopError` with `code == FloopErrorCode.BUILD_FAILED` / `BUILD_CANCELLED` / `TIMEOUT` on non-success terminals, so a plain `try/catch` is enough.

```kotlin
import com.floopfloop.sdk.resources.CreateProjectInput
import com.floopfloop.sdk.resources.Project
import kotlin.time.Duration.Companion.seconds

suspend fun ship(client: FloopFloop, prompt: String, subdomain: String): String {
    val created = client.projects.create(CreateProjectInput(
        prompt = prompt,
        subdomain = subdomain,
        botType = "site",
    ))

    return try {
        // Polls status every 2s; bounds the total wait to 10 minutes
        // so a stuck build doesn't hang forever.
        val live: Project = client.projects.waitForLive(created.project.id)
        live.url ?: throw IllegalStateException("project is live but has no URL yet")
    } catch (err: FloopError) {
        if (err.code == FloopErrorCode.BUILD_FAILED) {
            System.err.println("build failed: ${err.message}")
        }
        throw err
    }
}

fun main() = runBlocking {
    val client = FloopFloop(apiKey = System.getenv("FLOOP_API_KEY"))
    val url = ship(
        client,
        prompt = "A single-page portfolio for a landscape photographer",
        subdomain = "landscape-portfolio",
    )
    println("Live at $url")
}
```

**Wall-clock timeout via coroutines.** Wrap the call in `withTimeout` — it integrates with `delay()` inside the polling loop, so cancellation is responsive:

```kotlin
import kotlinx.coroutines.withTimeout
import kotlin.time.Duration.Companion.minutes

val live = withTimeout(10.minutes) {
    client.projects.waitForLive(created.project.id)
}
```

`StreamOptions.maxWait` (default 600 s) is a polling-side cap; `withTimeout` is the caller-side cap. Both honoured, whichever fires first. `withTimeout` is preferred when you have an existing application-level deadline.

**When to prefer `stream` over `waitForLive`:** when you want to show progress to a user. `waitForLive` only returns at the end — no visibility into what the build is doing.

---

## 2. Watch a build progress in real time

`projects.stream(ref)` returns a cold `Flow<StatusEvent>`. The flow emits each de-duplicated status snapshot until the project reaches a terminal state (`live` / `failed` / `cancelled` / `archived`) or `StreamOptions.maxWait` elapses. Events are de-duplicated on `(status, step, progress, queuePosition)`.

```kotlin
import com.floopfloop.sdk.resources.CreateProjectInput
import kotlinx.coroutines.flow.collect

suspend fun watchBuild(client: FloopFloop) {
    val created = client.projects.create(CreateProjectInput(
        prompt = "A recipe blog with a dark theme",
        subdomain = "recipe-blog",
        botType = "site",
    ))

    try {
        client.projects.stream(created.project.id).collect { event ->
            val progress = event.progress?.let { " ${it.toInt()}%" } ?: ""
            val step     = if (event.message.isNotEmpty()) " — ${event.message}" else ""
            println("[${event.status}]$progress$step (step ${event.step}/${event.totalSteps})")
        }
    } catch (err: FloopError) {
        when (err.code) {
            FloopErrorCode.BUILD_FAILED    -> error("build failed: ${err.message}")
            FloopErrorCode.BUILD_CANCELLED -> error("user cancelled build")
            FloopErrorCode.TIMEOUT         -> error("build stalled past maxWait")
            else                           -> throw err
        }
    }

    // Reached "live" cleanly — fetch the hydrated project.
    val done = client.projects.get(created.project.id)
    println("Live at ${done.url}")
}
```

**Early abort.** Break out of the flow with a coroutine job's `cancel()`, OR throw your own exception inside the collector — the flow propagates it through the `collect` call:

```kotlin
class EnoughProgress : RuntimeException()

try {
    client.projects.stream("recipe-blog").collect { event ->
        if ((event.progress ?: 0.0) >= 50.0) throw EnoughProgress()
        println("[${event.status}] ${event.progress}%")
    }
} catch (_: EnoughProgress) {
    println("saw enough progress, moving on")
}
```

The custom-exception pattern survives refactors better than a `take(N)` operator on the flow.

---

## 3. Refine a project, even when it's mid-build

`projects.refine` returns a `RefineResult` with three mutually-exclusive nullable fields:

- `queued: Queued?` — project is currently deploying; your message is queued.
- `processing: Processing?` — your message triggered a new build immediately.
- `savedOnly: SavedOnly?` — saved as a conversation entry without triggering a build.

Exactly one is non-null on success.

```kotlin
import com.floopfloop.sdk.resources.RefineInput
import com.floopfloop.sdk.resources.RefineResult

suspend fun refineAndWait(client: FloopFloop, ref: String, message: String) {
    val res: RefineResult = client.projects.refine(ref, RefineInput(message = message))

    when {
        res.processing != null -> {
            println("Build started (deployment ${res.processing!!.deploymentId})")
            client.projects.waitForLive(ref)
        }
        res.queued != null -> {
            println("Queued behind current build (message ${res.queued!!.messageId})")
            // Poll once — when "live", your queued message has been processed.
            client.projects.waitForLive(ref)
        }
        res.savedOnly != null -> {
            println("Saved as a chat message, no build triggered")
        }
    }
}
```

**Why three nullable fields instead of a sealed class?** Kotlin's sealed class would be the natural fit, but `kotlinx.serialization` deserialisation against the JSON shape is cleaner with three `Optional` fields — they exactly mirror what's on the wire (`{"queued": true, "messageId": "..."}` vs `{"processing": true, "deploymentId": "..."}`). The SDK guarantees exactly one is non-null on success.

A future major version may swap to a sealed class once we have a representative sample of caller code to validate the migration cost.

---

## 4. Upload an image and refine with it as context

Uploads are two-step: `uploads.create()` presigns an S3 URL and does the direct PUT, returning an `UploadedAttachment`. **Drop straight into `refine`** — `UploadedAttachment.asRefineAttachment()` does the conversion for you:

```kotlin
import com.floopfloop.sdk.resources.RefineInput
import java.io.File

suspend fun refineWithMockup(client: FloopFloop, projectRef: String, imagePath: String) {
    val bytes = File(imagePath).readBytes()
    val attachment = client.uploads.create(
        fileName = File(imagePath).name,
        bytes = bytes,
        // fileType = "image/png",  // optional — guessed from the extension
    )

    client.projects.refine(
        projectRef,
        RefineInput(
            message = "Make the homepage look like this mockup.",
            attachments = listOf(attachment.asRefineAttachment()),
        ),
    )
}
```

**Supported types:** `png`, `jpg/jpeg`, `gif`, `svg`, `webp`, `ico`, `pdf`, `txt`, `csv`, `doc`, `docx`. Max 5 MB per upload (`MAX_UPLOAD_BYTES` constant). The SDK validates client-side before hitting the network, so bad inputs throw `FloopError(code = FloopErrorCode.VALIDATION_ERROR)` with no round-trip.

Attachments only flow through `refine` today — `create` doesn't accept them via the SDK. If you need to anchor a brand-new project against images, create with a prompt first, then refine with the attachments as a follow-up.

---

## 5. Rotate an API key from a CI job

Three-step rotation: create the new key, write it to your secret store, then revoke the old one. The order matters — you must revoke with a **different** key than the one making the call (the backend returns `400 VALIDATION_ERROR` if you try to revoke the key you're authenticated with).

```kotlin
suspend fun rotate(victimName: String) {
    // Use a long-lived bootstrap key (stored as a CI secret) to do the
    // rotation. Don't use the key we're about to revoke — that hits
    // the self-revoke guard.
    val bootstrap = FloopFloop(apiKey = System.getenv("FLOOP_BOOTSTRAP_KEY"))

    // 1. Find the key we want to rotate by its name. (Each name is
    //    unique per account because the dashboard enforces it; matching
    //    by name is more reliable than matching the prefix substring.)
    val keys = bootstrap.apiKeys.list()
    val victim = keys.firstOrNull { it.name == victimName }
        ?: throw IllegalStateException("key not found: $victimName")

    // 2. Mint the replacement.
    val fresh = bootstrap.apiKeys.create(name = "$victimName-new")
    writeSecret("FLOOP_API_KEY", fresh.rawKey) // your secret-store helper

    // 3. Revoke the old one. apiKeys.remove() accepts an id OR a name.
    bootstrap.apiKeys.remove(victim.id)
}

// writeSecret wires into your CI platform's secret store —
// AWS Secrets Manager, Vault, GitHub Actions `gh secret set`, etc.
suspend fun writeSecret(name: String, value: String) { /* ... */ }
```

**Can't I just reuse the bootstrap key forever?** Technically yes — if it's tightly scoped and audited. In practice, a single long-lived "rotator key" is a common compromise: it only has permission to mint/list/revoke keys, never appears in application traffic, and itself gets rotated manually on a rare cadence (annually, or on compromise).

The 5-keys-per-account cap applies to active keys, so make sure to revoke old rotations rather than accumulating them.

---

## 6. Retry with backoff on `RATE_LIMITED` and `NETWORK_ERROR`

`FloopError` carries everything you need to implement backoff correctly:

- `retryAfter: Duration?` — populated from the `Retry-After` response header on 429s (parsed from delta-seconds OR HTTP-date). `null` when not set.
- `code: String` — distinguishes retryable (`RATE_LIMITED`, `NETWORK_ERROR`, `TIMEOUT`, `SERVICE_UNAVAILABLE`, `SERVER_ERROR`) from permanent (`UNAUTHORIZED`, `FORBIDDEN`, `VALIDATION_ERROR`, `NOT_FOUND`, `CONFLICT`, `BUILD_FAILED`, `BUILD_CANCELLED`).

```kotlin
import kotlinx.coroutines.delay
import kotlin.random.Random
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds

private val retryable = setOf(
    FloopErrorCode.RATE_LIMITED,
    FloopErrorCode.NETWORK_ERROR,
    FloopErrorCode.TIMEOUT,
    FloopErrorCode.SERVICE_UNAVAILABLE,
    FloopErrorCode.SERVER_ERROR,
)

suspend fun <T> withRetry(maxAttempts: Int = 5, fn: suspend () -> T): T {
    var attempt = 0
    while (true) {
        attempt++
        try {
            return fn()
        } catch (err: FloopError) {
            if (err.code !in retryable || attempt >= maxAttempts) throw err

            // Prefer the server's hint; fall back to exponential backoff
            // with jitter capped at 30 s.
            val serverHint: Duration? = err.retryAfter
            val expo = minOf(30.seconds, (250L shl attempt.coerceAtMost(7)).milliseconds)
            val jitter = Random.nextLong(0, 250).milliseconds
            val wait = (serverHint ?: expo) + jitter

            val reqTag = err.requestId?.let { " (request $it)" } ?: ""
            System.err.println(
                "floop: ${err.code} (attempt $attempt/$maxAttempts), retrying in $wait$reqTag"
            )
            delay(wait)
        }
    }
}

// Wrap any SDK call:
suspend fun example(client: FloopFloop) {
    val projects = withRetry { client.projects.list() }
    // ...
}
```

**Don't retry everything.** `VALIDATION_ERROR`, `UNAUTHORIZED`, and `FORBIDDEN` are not going to fix themselves between attempts — retrying them just burns rate-limit budget and delays the real error reaching your logs.

**Cancellation.** `delay()` is cancellation-safe in Kotlin coroutines — if the caller's scope is cancelled mid-sleep, it throws `CancellationException` and unwinds cleanly. No special handling needed.

---

## 7. Make a small change without a full rebuild (`codeEditOnly`)

Default `refine` runs the full 6-step pipeline — replan, regenerate, redeploy. For a copy edit, a colour swap, or a typo fix that doesn't need a redesign, set `codeEditOnly = true`. The backend cuts to a 3-step in-place patch and deducts the cheaper code-edit credit cost (roughly half a refinement).

Only meaningful once the project has reached `live` at least once — on a project that hasn't deployed yet, the flag is ignored and you get a normal initial build.

```kotlin
import com.floopfloop.sdk.resources.RefineInput

suspend fun tweakHeadline(client: FloopFloop, ref: String) {
    client.projects.refine(
        ref,
        RefineInput(
            message = "Change the hero headline from 'Welcome' to 'Hello there.'",
            codeEditOnly = true,
        ),
    )
    client.projects.waitForLive(ref)
}
```

If the change actually needs a redesign or a new dependency, prefer a plain `refine` — `codeEditOnly` is for surface-level edits only. The backend won't promote a code-edit into a full refinement automatically; it just runs the 3-step patch with the limited tools it has, and you may end up paying for a second `refine` to redo the change properly.

---

## Got a pattern worth adding?

Open an issue at [floop-kotlin-sdk/issues](https://github.com/FloopFloopAI/floop-kotlin-sdk/issues) describing the use case. Recipes live in this file, not in `src/`, so they're easy to update without an SDK release.
