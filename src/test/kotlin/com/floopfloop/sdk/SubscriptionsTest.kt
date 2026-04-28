package com.floopfloop.sdk

import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.boolean
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

class SubscriptionsTest {

    private lateinit var server: MockWebServer

    @BeforeTest
    fun setUp() {
        server = MockWebServer()
        server.start()
    }

    @AfterTest
    fun tearDown() {
        server.shutdown()
    }

    private fun client(): FloopFloop =
        FloopFloop(apiKey = "flp_test", baseUrl = server.url("/").toString().trimEnd('/'))

    @Test
    fun currentPopulated() = runTest {
        server.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setHeader("Content-Type", "application/json")
                .setBody(
                    """{"data":{
                      "subscription":{
                        "status":"active",
                        "billingPeriod":"monthly",
                        "currentPeriodStart":"2026-04-01T00:00:00Z",
                        "currentPeriodEnd":"2026-05-01T00:00:00Z",
                        "canceledAt":null,
                        "planName":"pro",
                        "planDisplayName":"Pro",
                        "priceMonthly":29,
                        "priceAnnual":290,
                        "monthlyCredits":500,
                        "maxProjects":50,
                        "maxStorageMb":5000,
                        "maxBandwidthMb":50000,
                        "creditRolloverMonths":1,
                        "features":{"teams":true}
                      },
                      "credits":{
                        "current":423,
                        "rolledOver":50,
                        "total":473,
                        "rolloverExpiresAt":"2026-05-01T00:00:00Z",
                        "lifetimeUsed":1234
                      }
                    }}""".trimIndent()
                )
        )

        val out = client().subscriptions.current()
        val sub = assertNotNull(out.subscription)
        assertEquals("pro", sub.planName)
        assertEquals(500, sub.monthlyCredits)
        assertEquals("monthly", sub.billingPeriod)
        assertNull(sub.canceledAt)
        // features round-trip — JsonObject preserves arbitrary backend keys
        assertEquals(true, sub.features["teams"]?.jsonPrimitive?.boolean)

        val credits = assertNotNull(out.credits)
        assertEquals(473, credits.total)
        assertEquals("2026-05-01T00:00:00Z", credits.rolloverExpiresAt)

        val req = server.takeRequest()
        assertEquals("/api/v1/subscriptions/current", req.path)
        assertEquals("GET", req.method)
    }

    @Test
    fun currentBothNull() = runTest {
        server.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setBody("""{"data":{"subscription":null,"credits":null}}""")
        )
        val out = client().subscriptions.current()
        assertNull(out.subscription)
        assertNull(out.credits)
    }
}
