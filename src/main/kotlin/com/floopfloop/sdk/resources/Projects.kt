package com.floopfloop.sdk.resources

import com.floopfloop.sdk.FloopError
import com.floopfloop.sdk.FloopErrorCode
import com.floopfloop.sdk.FloopFloop
import com.floopfloop.sdk.JSON_LENIENT
import com.floopfloop.sdk.requestEmpty
import com.floopfloop.sdk.requestJson
import com.floopfloop.sdk.urlEncode
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.boolean
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

// ── Models ────────────────────────────────────────────────────────────

@Serializable
public data class Project(
    val id: String,
    val name: String,
    val subdomain: String? = null,
    val status: String,
    val botType: String? = null,
    val url: String? = null,
    val amplifyAppUrl: String? = null,
    val isPublic: Boolean = false,
    val isAuthProtected: Boolean = false,
    val teamId: String? = null,
    val createdAt: String,
    val updatedAt: String,
    val thumbnailUrl: String? = null,
)

@Serializable
public data class CreateProjectInput(
    val prompt: String,
    val name: String? = null,
    val subdomain: String? = null,
    val botType: String? = null,
    val isAuthProtected: Boolean? = null,
    val teamId: String? = null,
)

@Serializable
public data class CreatedProjectDeployment(
    val id: String,
    val status: String,
    val version: Int,
)

@Serializable
public data class CreatedProject(
    val project: Project,
    val deployment: CreatedProjectDeployment,
)

@Serializable
public data class StatusEvent(
    val step: Int = 0,
    val totalSteps: Int = 0,
    val status: String,
    val message: String = "",
    val progress: Double? = null,
    val queuePosition: Int? = null,
)

@Serializable
public data class RefineAttachment(
    val key: String,
    val fileName: String,
    val fileType: String,
    val fileSize: Long,
)

@Serializable
public data class RefineInput(
    val message: String,
    val attachments: List<RefineAttachment>? = null,
    val codeEditOnly: Boolean? = null,
)

/**
 * Three-shape response from `POST /projects/:id/refine`. Exactly one of
 * [queued], [processing], or [savedOnly] is non-null on success.
 */
public data class RefineResult(
    val queued: Queued? = null,
    val processing: Processing? = null,
    val savedOnly: SavedOnly? = null,
) {
    public data class Queued(val messageId: String)
    public data class Processing(val deploymentId: String, val queuePriority: Int)
    public class SavedOnly
}

@Serializable
public data class ConversationMessage(
    val id: String,
    val projectId: String,
    val role: String,
    val content: String,
    val status: String,
    val position: Int? = null,
    val createdAt: String,
)

@Serializable
public data class ConversationsResult(
    val messages: List<ConversationMessage>,
    val queued: List<ConversationMessage>,
    val latestVersion: Int,
)

public data class StreamOptions(
    val interval: Duration = 2.seconds,
    val maxWait: Duration = 600.seconds,
)

// ── Resource ──────────────────────────────────────────────────────────

public class Projects internal constructor(private val client: FloopFloop) {

    public suspend fun create(input: CreateProjectInput): CreatedProject {
        val body = JSON_LENIENT.encodeToString(CreateProjectInput.serializer(), input)
        return client.requestJson("POST", "/api/v1/projects", body)
    }

    public suspend fun list(teamId: String? = null): List<Project> {
        val path = if (teamId != null) "/api/v1/projects?teamId=${urlEncode(teamId)}"
                   else "/api/v1/projects"
        return client.requestJson("GET", path)
    }

    /**
     * Fetch a single project by id or subdomain. There is no dedicated
     * `GET /api/v1/projects/:id` endpoint — we filter the list. For
     * accounts with many projects this is a real cost; cache the project
     * handle and reuse it instead of re-resolving.
     */
    public suspend fun get(ref: String, teamId: String? = null): Project {
        val all = list(teamId)
        return all.firstOrNull { it.id == ref || it.subdomain == ref }
            ?: throw FloopError(FloopErrorCode.NOT_FOUND, "project not found: $ref", status = 404)
    }

    public suspend fun status(ref: String): StatusEvent =
        client.requestJson("GET", "/api/v1/projects/${urlEncode(ref)}/status")

    public suspend fun cancel(ref: String) {
        client.requestEmpty("POST", "/api/v1/projects/${urlEncode(ref)}/cancel")
    }

    public suspend fun reactivate(ref: String) {
        client.requestEmpty("POST", "/api/v1/projects/${urlEncode(ref)}/reactivate")
    }

    public suspend fun refine(ref: String, input: RefineInput): RefineResult {
        val body = JSON_LENIENT.encodeToString(RefineInput.serializer(), input)
        val raw: JsonObject = client.requestJson(
            "POST",
            "/api/v1/projects/${urlEncode(ref)}/refine",
            body,
        )
        val processing = raw["processing"]?.jsonPrimitive?.boolean
        if (processing == true) {
            val depId = raw["deploymentId"]?.jsonPrimitive?.contentOrNull
                ?: throw FloopError(FloopErrorCode.UNKNOWN, "refine: missing deploymentId")
            val prio = raw["queuePriority"]?.jsonPrimitive?.intOrNull ?: 0
            return RefineResult(processing = RefineResult.Processing(depId, prio))
        }
        val queued = raw["queued"]?.jsonPrimitive?.boolean
        if (queued != null) {
            return if (queued) {
                val msgId = raw["messageId"]?.jsonPrimitive?.contentOrNull
                    ?: throw FloopError(FloopErrorCode.UNKNOWN, "refine: missing messageId")
                RefineResult(queued = RefineResult.Queued(msgId))
            } else {
                RefineResult(savedOnly = RefineResult.SavedOnly())
            }
        }
        throw FloopError(FloopErrorCode.UNKNOWN, "refine: unrecognised response shape")
    }

    public suspend fun conversations(ref: String, limit: Int? = null): ConversationsResult {
        val path = if (limit != null && limit > 0)
            "/api/v1/projects/${urlEncode(ref)}/conversations?limit=$limit"
        else
            "/api/v1/projects/${urlEncode(ref)}/conversations"
        return client.requestJson("GET", path)
    }

    /**
     * Cold [Flow] of de-duplicated status events. Emits once per unique
     * (status, step, progress, queuePosition) tuple until a terminal
     * state (`live` / `failed` / `cancelled` / `archived`) or
     * [StreamOptions.maxWait] elapses.
     *
     * Throws [FloopError] with code `BUILD_FAILED` / `BUILD_CANCELLED`
     * / `TIMEOUT` on non-success terminals.
     */
    public fun stream(ref: String, opts: StreamOptions = StreamOptions()): Flow<StatusEvent> = flow {
        val deadlineMs = System.currentTimeMillis() + opts.maxWait.inWholeMilliseconds
        var lastKey: String? = null
        while (true) {
            if (System.currentTimeMillis() >= deadlineMs) {
                throw FloopError(
                    FloopErrorCode.TIMEOUT,
                    "stream: project $ref did not reach a terminal state within ${opts.maxWait}",
                )
            }
            val event = status(ref)
            val key = "${event.status}|${event.step}|${event.progress}|${event.queuePosition}"
            if (key != lastKey) {
                lastKey = key
                emit(event)
            }
            when (event.status) {
                "live", "archived" -> return@flow
                "failed" -> throw FloopError(
                    FloopErrorCode.BUILD_FAILED,
                    if (event.message.isEmpty()) "build failed" else event.message,
                )
                "cancelled" -> throw FloopError(
                    FloopErrorCode.BUILD_CANCELLED,
                    if (event.message.isEmpty()) "build cancelled" else event.message,
                )
            }
            delay(opts.interval)
        }
    }

    /**
     * Block until the project reaches `live`. Throws [FloopError] on the
     * non-success terminals. Returns the hydrated [Project].
     */
    public suspend fun waitForLive(ref: String, opts: StreamOptions = StreamOptions()): Project {
        // Drain the flow to drive the polling loop to completion.
        stream(ref, opts).collect { /* discard intermediate events */ }
        return get(ref)
    }
}
