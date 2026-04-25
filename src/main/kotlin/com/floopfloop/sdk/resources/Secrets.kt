package com.floopfloop.sdk.resources

import com.floopfloop.sdk.FloopFloop
import com.floopfloop.sdk.JSON_LENIENT
import com.floopfloop.sdk.requestEmpty
import com.floopfloop.sdk.requestJson
import com.floopfloop.sdk.urlEncode
import kotlinx.serialization.Serializable

@Serializable
public data class SecretSummary(
    val key: String,
    val createdAt: String,
    val updatedAt: String,
)

@Serializable
internal data class SetSecretBody(val key: String, val value: String)

public class Secrets internal constructor(private val client: FloopFloop) {

    public suspend fun list(ref: String): List<SecretSummary> =
        client.requestJson("GET", "/api/v1/projects/${urlEncode(ref)}/secrets")

    public suspend fun set(ref: String, key: String, value: String) {
        val body = JSON_LENIENT.encodeToString(SetSecretBody.serializer(), SetSecretBody(key, value))
        client.requestEmpty("POST", "/api/v1/projects/${urlEncode(ref)}/secrets", body)
    }

    public suspend fun remove(ref: String, key: String) {
        client.requestEmpty("DELETE", "/api/v1/projects/${urlEncode(ref)}/secrets/${urlEncode(key)}")
    }
}
