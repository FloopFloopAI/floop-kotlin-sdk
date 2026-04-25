package com.floopfloop.sdk.resources

import com.floopfloop.sdk.FloopFloop
import com.floopfloop.sdk.requestJson
import com.floopfloop.sdk.urlEncode
import kotlinx.serialization.Serializable

@Serializable
public data class SubdomainCheckResult(
    val valid: Boolean,
    val available: Boolean,
    val error: String? = null,
)

@Serializable
public data class SubdomainSuggestResult(val suggestions: List<String>)

public class Subdomains internal constructor(private val client: FloopFloop) {
    public suspend fun check(slug: String): SubdomainCheckResult =
        client.requestJson("GET", "/api/v1/subdomains/check?slug=${urlEncode(slug)}")

    public suspend fun suggest(prompt: String): SubdomainSuggestResult =
        client.requestJson("GET", "/api/v1/subdomains/suggest?prompt=${urlEncode(prompt)}")
}
