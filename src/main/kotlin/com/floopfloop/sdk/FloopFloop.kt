package com.floopfloop.sdk

import com.floopfloop.sdk.resources.ApiKeys
import com.floopfloop.sdk.resources.Library
import com.floopfloop.sdk.resources.Projects
import com.floopfloop.sdk.resources.Secrets
import com.floopfloop.sdk.resources.Subdomains
import com.floopfloop.sdk.resources.Uploads
import com.floopfloop.sdk.resources.Usage
import com.floopfloop.sdk.resources.UserApi
import okhttp3.OkHttpClient
import java.util.concurrent.TimeUnit
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds
import kotlin.time.toJavaDuration

/**
 * Main entry point for the FloopFloop SDK.
 *
 * Construct once and reuse — OkHttp keeps a connection pool per instance,
 * so a fresh client per request loses connection-reuse and adds a TLS
 * handshake every call.
 *
 * ```kotlin
 * import com.floopfloop.sdk.FloopFloop
 * import com.floopfloop.sdk.resources.CreateProjectInput
 *
 * val client = FloopFloop(apiKey = System.getenv("FLOOP_API_KEY"))
 *
 * val created = client.projects.create(CreateProjectInput(
 *     prompt = "A landing page for a cat cafe",
 *     subdomain = "cat-cafe",
 *     botType = "site",
 * ))
 * val live = client.projects.waitForLive(created.project.id)
 * println("Live at: ${live.url ?: ""}")
 * ```
 */
public class FloopFloop(
    public val apiKey: String,
    baseUrl: String = DEFAULT_BASE_URL,
    timeout: Duration = 30.seconds,
    userAgentSuffix: String? = null,
    httpClient: OkHttpClient? = null,
) {
    public companion object {
        public const val DEFAULT_BASE_URL: String = "https://www.floopfloop.com"
    }

    init {
        require(apiKey.isNotEmpty()) { "FloopFloop: apiKey must not be empty" }
    }

    public val baseUrl: String = baseUrl.trimEnd('/')

    internal val http: OkHttpClient = httpClient ?: OkHttpClient.Builder()
        .callTimeout(timeout.toJavaDuration())
        .readTimeout(timeout.toJavaDuration())
        .writeTimeout(timeout.toJavaDuration())
        .connectTimeout(15, TimeUnit.SECONDS)
        .build()

    internal val userAgent: String = "floopfloop-kotlin-sdk/${FloopFloopSDK.VERSION}".let {
        if (userAgentSuffix != null) "$it $userAgentSuffix" else it
    }

    // Resource accessors. Lazy because most clients won't touch every resource.
    public val projects: Projects by lazy { Projects(this) }
    public val subdomains: Subdomains by lazy { Subdomains(this) }
    public val secrets: Secrets by lazy { Secrets(this) }
    public val library: Library by lazy { Library(this) }
    public val usage: Usage by lazy { Usage(this) }
    public val apiKeys: ApiKeys by lazy { ApiKeys(this) }
    public val uploads: Uploads by lazy { Uploads(this) }
    public val user: UserApi by lazy { UserApi(this) }
}
