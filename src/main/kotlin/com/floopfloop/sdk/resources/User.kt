package com.floopfloop.sdk.resources

import com.floopfloop.sdk.FloopFloop
import com.floopfloop.sdk.requestJson
import kotlinx.serialization.Serializable

@Serializable
public data class User(
    val id: String,
    val email: String? = null,
    val name: String? = null,
    val role: String? = null,
    val source: String? = null,
)

public class UserApi internal constructor(private val client: FloopFloop) {
    public suspend fun me(): User =
        client.requestJson("GET", "/api/v1/user/me")
}
