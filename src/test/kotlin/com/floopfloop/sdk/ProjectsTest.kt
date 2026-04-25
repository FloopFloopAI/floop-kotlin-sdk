package com.floopfloop.sdk

import com.floopfloop.sdk.resources.CreateProjectInput
import com.floopfloop.sdk.resources.RefineInput
import kotlinx.coroutines.test.runTest
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull
import kotlin.test.assertNull

class ProjectsTest {

    private lateinit var server: MockWebServer

    @BeforeTest fun setUp() { server = MockWebServer(); server.start() }
    @AfterTest fun tearDown() { server.shutdown() }

    private fun client(): FloopFloop =
        FloopFloop(apiKey = "flp_test", baseUrl = server.url("/").toString().trimEnd('/'))

    @Test
    fun createReturnsProject() = runTest {
        server.enqueue(MockResponse().setBody("""
            {"data":{
              "project":{"id":"p_1","name":"Test","subdomain":"test","status":"queued",
                        "botType":"site","url":null,"amplifyAppUrl":null,
                        "isPublic":false,"isAuthProtected":true,"teamId":null,
                        "createdAt":"2026-04-25T00:00:00Z","updatedAt":"2026-04-25T00:00:00Z",
                        "thumbnailUrl":null},
              "deployment":{"id":"d_1","status":"pending","version":1}
            }}
        """.trimIndent()))

        val created = client().projects.create(CreateProjectInput(prompt = "demo", subdomain = "test"))
        assertEquals("p_1", created.project.id)
        assertEquals("test", created.project.subdomain)
        assertEquals(1, created.deployment.version)
    }

    @Test
    fun getFiltersList() = runTest {
        server.enqueue(MockResponse().setBody("""
            {"data":[
              {"id":"p_1","name":"A","subdomain":"a","status":"live","botType":"site","url":"https://a.x","amplifyAppUrl":null,"isPublic":false,"isAuthProtected":true,"teamId":null,"createdAt":"2026-04-25T00:00:00Z","updatedAt":"2026-04-25T00:00:00Z","thumbnailUrl":null},
              {"id":"p_2","name":"B","subdomain":"b","status":"queued","botType":"site","url":null,"amplifyAppUrl":null,"isPublic":false,"isAuthProtected":true,"teamId":null,"createdAt":"2026-04-25T00:00:00Z","updatedAt":"2026-04-25T00:00:00Z","thumbnailUrl":null}
            ]}
        """.trimIndent()))
        val p = client().projects.get("b")
        assertEquals("p_2", p.id)
    }

    @Test
    fun getMissingThrowsNotFound() = runTest {
        server.enqueue(MockResponse().setBody("""{"data":[]}"""))
        val err = assertFailsWith<FloopError> { client().projects.get("nope") }
        assertEquals(FloopErrorCode.NOT_FOUND, err.code)
        assertEquals(404, err.status)
    }

    @Test
    fun refineProcessingShape() = runTest {
        server.enqueue(MockResponse().setBody("""{"processing":true,"deploymentId":"d_42","queuePriority":3}"""))
        val res = client().projects.refine("test", RefineInput(message = "tweak"))
        assertNotNull(res.processing)
        assertEquals("d_42", res.processing!!.deploymentId)
        assertEquals(3, res.processing!!.queuePriority)
        assertNull(res.queued)
        assertNull(res.savedOnly)
    }

    @Test
    fun refineQueuedShape() = runTest {
        server.enqueue(MockResponse().setBody("""{"queued":true,"messageId":"m_7"}"""))
        val res = client().projects.refine("test", RefineInput(message = "tweak"))
        assertNotNull(res.queued)
        assertEquals("m_7", res.queued!!.messageId)
    }

    @Test
    fun refineSavedOnlyShape() = runTest {
        server.enqueue(MockResponse().setBody("""{"queued":false}"""))
        val res = client().projects.refine("test", RefineInput(message = "tweak"))
        assertNotNull(res.savedOnly)
        assertNull(res.queued)
    }
}
