package com.floopfloop.sdk.resources

import com.floopfloop.sdk.FloopError
import com.floopfloop.sdk.FloopErrorCode
import com.floopfloop.sdk.FloopFloop
import com.floopfloop.sdk.JSON_LENIENT
import com.floopfloop.sdk.requestEmpty
import com.floopfloop.sdk.requestJson
import com.floopfloop.sdk.urlEncode
import kotlinx.serialization.Serializable

@Serializable
public data class ApiKeySummary(
    val id: String,
    val name: String,
    val keyPrefix: String,
    val lastUsedAt: String? = null,
    val createdAt: String,
)

@Serializable
public data class IssuedApiKey(
    val id: String,
    val rawKey: String,
    val keyPrefix: String,
)

@Serializable
internal data class CreateApiKeyBody(val name: String)

@Serializable
internal data class ApiKeysListEnvelope(val keys: List<ApiKeySummary>)

public class ApiKeys internal constructor(private val client: FloopFloop) {

    public suspend fun list(): List<ApiKeySummary> {
        val env: ApiKeysListEnvelope = client.requestJson("GET", "/api/v1/api-keys")
        return env.keys
    }

    /**
     * Mint a new API key. The returned [IssuedApiKey.rawKey] is the
     * **only** time the full secret is exposed — surface it once and
     * never persist it.
     */
    public suspend fun create(name: String): IssuedApiKey {
        val body = JSON_LENIENT.encodeToString(CreateApiKeyBody.serializer(), CreateApiKeyBody(name))
        return client.requestJson("POST", "/api/v1/api-keys", body)
    }

    /**
     * Revoke an API key by id or human-readable name. Lists first to
     * accept either form, then DELETEs by id.
     */
    public suspend fun remove(idOrName: String) {
        val all = list()
        val match = all.firstOrNull { it.id == idOrName || it.name == idOrName }
            ?: throw FloopError(FloopErrorCode.NOT_FOUND, "API key not found: $idOrName", status = 404)
        client.requestEmpty("DELETE", "/api/v1/api-keys/${urlEncode(match.id)}")
    }
}
