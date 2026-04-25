package com.floopfloop.sdk

import kotlinx.coroutines.test.runTest
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class TransportTest {

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

    private fun client(apiKey: String = "flp_test"): FloopFloop =
        FloopFloop(apiKey = apiKey, baseUrl = server.url("/").toString().trimEnd('/'))

    @Test
    fun bearerAndDataEnvelopeUnwrap() = runTest {
        server.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setHeader("Content-Type", "application/json")
                .setBody("""{"data":{"id":"u_1","email":"p@x","name":null,"role":"user","source":"api_key"}}""")
        )
        val user = client().user.me()
        assertEquals("u_1", user.id)
        assertEquals("p@x", user.email)
        assertEquals("user", user.role)

        val req = server.takeRequest()
        assertEquals("Bearer flp_test", req.getHeader("Authorization"))
        assertEquals("application/json", req.getHeader("Accept"))
        val ua = req.getHeader("User-Agent") ?: ""
        assertTrue(ua.startsWith("floopfloop-kotlin-sdk/"), "unexpected User-Agent: $ua")
    }

    @Test
    fun userAgentSuffixAppended() = runTest {
        server.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setBody("""{"data":{"id":"u","email":null,"name":null,"role":"user","source":"api_key"}}""")
        )
        val c = FloopFloop(
            apiKey = "flp_test",
            baseUrl = server.url("/").toString().trimEnd('/'),
            userAgentSuffix = "myapp/1.2",
        )
        c.user.me()
        val req = server.takeRequest()
        val ua = req.getHeader("User-Agent") ?: ""
        assertTrue(ua.endsWith(" myapp/1.2"), "expected UA to end with ' myapp/1.2', got '$ua'")
    }

    @Test
    fun errorEnvelopeBecomesFloopError() = runTest {
        server.enqueue(
            MockResponse()
                .setResponseCode(404)
                .setHeader("x-request-id", "req_1")
                .setBody("""{"error":{"code":"NOT_FOUND","message":"no such user"}}""")
        )
        val err = assertFailsWith<FloopError> { client().user.me() }
        assertEquals(FloopErrorCode.NOT_FOUND, err.code)
        assertEquals(404, err.status)
        assertEquals("no such user", err.message)
        assertEquals("req_1", err.requestId)
    }

    @Test
    fun retryAfterDeltaSeconds() = runTest {
        server.enqueue(
            MockResponse()
                .setResponseCode(429)
                .setHeader("Retry-After", "5")
                .setBody("""{"error":{"code":"RATE_LIMITED","message":"slow"}}""")
        )
        val err = assertFailsWith<FloopError> { client().user.me() }
        assertEquals(FloopErrorCode.RATE_LIMITED, err.code)
        assertNotNull(err.retryAfter)
        assertEquals(5_000L, err.retryAfter!!.inWholeMilliseconds)
    }

    @Test
    fun unknownServerCodePassesThrough() = runTest {
        server.enqueue(
            MockResponse()
                .setResponseCode(418)
                .setBody("""{"error":{"code":"TEAPOT_MODE","message":"short and stout"}}""")
        )
        val err = assertFailsWith<FloopError> { client().user.me() }
        assertEquals("TEAPOT_MODE", err.code)
    }

    @Test
    fun nonJson5xxFallsBackToServerError() = runTest {
        server.enqueue(
            MockResponse()
                .setResponseCode(500)
                .setBody("upstream crashed")
        )
        val err = assertFailsWith<FloopError> { client().user.me() }
        assertEquals(FloopErrorCode.SERVER_ERROR, err.code)
        assertEquals(500, err.status)
    }

    @Test
    fun emptyApiKeyThrows() {
        assertFailsWith<IllegalArgumentException> {
            FloopFloop(apiKey = "")
        }
    }

    @Test
    fun baseUrlStripsTrailingSlashes() {
        val c = FloopFloop(apiKey = "flp_test", baseUrl = "https://x.example.com//")
        assertEquals("https://x.example.com", c.baseUrl)
    }
}
