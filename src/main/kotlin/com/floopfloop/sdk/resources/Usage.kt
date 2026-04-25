package com.floopfloop.sdk.resources

import com.floopfloop.sdk.FloopFloop
import com.floopfloop.sdk.requestJson
import kotlinx.serialization.Serializable

@Serializable
public data class UsageSummary(
    val plan: Plan? = null,
    val credits: Credits? = null,
    val builds: Builds? = null,
    val storage: Storage? = null,
) {
    @Serializable
    public data class Plan(val name: String? = null, val code: String? = null)

    @Serializable
    public data class Credits(
        val totalAvailable: Double? = null,
        val perPeriodAllowance: Double? = null,
        val perPeriodUsed: Double? = null,
        val rolloverBalance: Double? = null,
        val rolloverExpiresAt: String? = null,
    )

    @Serializable
    public data class Builds(val countThisPeriod: Int? = null, val limit: Int? = null)

    @Serializable
    public data class Storage(val bytesUsed: Long? = null, val bytesLimit: Long? = null)
}

public class Usage internal constructor(private val client: FloopFloop) {
    public suspend fun summary(): UsageSummary =
        client.requestJson("GET", "/api/v1/usage/summary")
}
