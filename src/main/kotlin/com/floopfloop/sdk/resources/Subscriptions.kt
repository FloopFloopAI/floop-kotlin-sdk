package com.floopfloop.sdk.resources

import com.floopfloop.sdk.FloopFloop
import com.floopfloop.sdk.requestJson
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonObject

/**
 * Plan + billing snapshot for the authenticated user. Returned by
 * [Subscriptions.current]. Sensitive fields (Stripe customer /
 * subscription IDs, invoice metadata) are deliberately omitted from the
 * wire shape on the backend.
 */
@Serializable
public data class SubscriptionPlan(
    val status: String,
    val billingPeriod: String? = null,
    val currentPeriodStart: String,
    val currentPeriodEnd: String,
    val canceledAt: String? = null,
    val planName: String,
    val planDisplayName: String,
    val priceMonthly: Long,
    val priceAnnual: Long,
    val monthlyCredits: Long,
    val maxProjects: Long,
    val maxStorageMb: Long,
    val maxBandwidthMb: Long,
    val creditRolloverMonths: Long,
    /**
     * Free-form feature-flag bag, modelled as [JsonObject] so callers can
     * inspect new flags via `features["teams"]?.jsonPrimitive?.boolean`
     * without us cutting a release each time the backend grows a key.
     */
    val features: JsonObject = JsonObject(emptyMap()),
)

/**
 * Credit-balance snapshot — second half of the
 * `/api/v1/subscriptions/current` response.
 */
@Serializable
public data class SubscriptionCredits(
    val current: Long,
    val rolledOver: Long,
    val total: Long,
    val rolloverExpiresAt: String? = null,
    val lifetimeUsed: Long,
)

/**
 * Response envelope for [Subscriptions.current]. Both fields are
 * independently nullable: a user may exist without an active subscription
 * (mid-signup, cancelled with no grace credits remaining). Treat `null`
 * as "no active subscription data" rather than an error.
 */
@Serializable
public data class CurrentSubscription(
    val subscription: SubscriptionPlan? = null,
    val credits: SubscriptionCredits? = null,
)

/**
 * Resource namespace for plan + credit-balance.
 *
 * Distinct from [Usage] — `usage.summary()` returns current-period
 * consumption (credits remaining + builds used + storage), while
 * `subscriptions.current()` returns the plan tier itself (price, billing
 * period, cancel state). They overlap on `monthlyCredits` and
 * `maxProjects` but serve different audiences:
 * `usage.summary()` for "am I about to hit my limits?",
 * `subscriptions.current()` for "what plan am I on, and when does it
 * renew?".
 */
public class Subscriptions internal constructor(private val client: FloopFloop) {
    public suspend fun current(): CurrentSubscription =
        client.requestJson("GET", "/api/v1/subscriptions/current")
}
