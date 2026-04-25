package com.floopfloop.sdk

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.decodeFromJsonElement
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.Call
import okhttp3.Callback
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import java.io.IOException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import java.time.OffsetDateTime
import java.time.format.DateTimeFormatter
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds

internal val JSON_LENIENT: Json = Json {
    ignoreUnknownKeys = true
    isLenient = false
    encodeDefaults = false
}

internal val JSON_MEDIA_TYPE = "application/json".toMediaType()

@Serializable
internal data class ErrorEnvelopeBody(val code: String, val message: String)

/**
 * Send the OkHttp request and return the (data, response) pair, mapping
 * transport-level exceptions to typed [FloopError]s.
 */
internal suspend fun OkHttpClient.executeAsync(request: Request): Response =
    withContext(Dispatchers.IO) {
        suspendCancellableCoroutine { cont ->
            val call = newCall(request)
            cont.invokeOnCancellation {
                runCatching { call.cancel() }
            }
            call.enqueue(object : Callback {
                override fun onFailure(call: Call, e: IOException) {
                    val code = when (e) {
                        is SocketTimeoutException -> FloopErrorCode.TIMEOUT
                        is UnknownHostException -> FloopErrorCode.NETWORK_ERROR
                        else -> FloopErrorCode.NETWORK_ERROR
                    }
                    val msg = when (code) {
                        FloopErrorCode.TIMEOUT -> "request timed out"
                        else -> "could not reach ${request.url}: ${e.message}"
                    }
                    cont.resumeWithException(FloopError(code, msg, status = 0, cause = e))
                }
                override fun onResponse(call: Call, response: Response) {
                    if (cont.isActive) cont.resume(response)
                }
            })
        }
    }

/**
 * Internal helper. Parses a `Retry-After` header value per RFC 7231 —
 * accepts either delta-seconds or an HTTP-date. Returns `null` on
 * empty/unparseable.
 */
internal fun parseRetryAfter(header: String?): Duration? {
    if (header.isNullOrBlank()) return null
    val asSeconds = header.toDoubleOrNull()
    if (asSeconds != null) {
        if (asSeconds < 0) return null
        return (asSeconds * 1000).toLong().milliseconds
    }
    return runCatching {
        val date = OffsetDateTime.parse(header, DateTimeFormatter.RFC_1123_DATE_TIME)
        val delta = date.toEpochSecond() - System.currentTimeMillis() / 1000
        if (delta > 0) delta.seconds else 0.seconds
    }.getOrNull()
}

/**
 * Build a request, send it, parse the {data: ...} envelope (if present)
 * and decode the inner shape.
 */
internal suspend inline fun <reified T> FloopFloop.requestJson(
    method: String,
    path: String,
    bodyJson: String? = null,
): T {
    val raw = rawRequest(method, path, bodyJson)
    val body = raw.body?.string().orEmpty()
    return decodeUnwrappingDataEnvelope<T>(body)
}

/** Send a request that returns no body (e.g. cancel, reactivate). */
internal suspend fun FloopFloop.requestEmpty(
    method: String,
    path: String,
    bodyJson: String? = null,
) {
    rawRequest(method, path, bodyJson).body?.close()
}

internal inline fun <reified T> decodeUnwrappingDataEnvelope(body: String): T {
    if (body.isBlank()) {
        throw FloopError(FloopErrorCode.UNKNOWN, "empty response body")
    }
    val element = try {
        JSON_LENIENT.parseToJsonElement(body)
    } catch (t: Throwable) {
        throw FloopError(FloopErrorCode.UNKNOWN, "failed to decode response: ${t.message}", cause = t)
    }
    val obj = element as? JsonObject
    val inner = obj?.get("data") ?: element
    return runCatching { JSON_LENIENT.decodeFromJsonElement<T>(inner) }
        .getOrElse {
            throw FloopError(FloopErrorCode.UNKNOWN, "failed to decode response: ${it.message}", cause = it)
        }
}

internal suspend fun FloopFloop.rawRequest(
    method: String,
    path: String,
    bodyJson: String?,
): Response {
    val urlString = baseUrl + path
    val body: RequestBody? = bodyJson?.toRequestBody(JSON_MEDIA_TYPE)
    val builder = Request.Builder()
        .url(urlString)
        .header("Authorization", "Bearer $apiKey")
        .header("Accept", "application/json")
        .header("User-Agent", userAgent)
    when {
        body != null -> builder.method(method, body)
        method == "GET" -> builder.get()
        else -> builder.method(method, "".toRequestBody(JSON_MEDIA_TYPE))
    }
    val request = builder.build()
    val response = http.executeAsync(request)
    if (!response.isSuccessful) {
        val errBody = response.body?.string().orEmpty()
        val (code, message) = parseErrorEnvelope(errBody, response.code)
        val requestId = response.header("x-request-id")
        val retryAfter = parseRetryAfter(response.header("Retry-After"))
        response.close()
        throw FloopError(
            code = code,
            message = message,
            status = response.code,
            requestId = requestId,
            retryAfter = retryAfter,
        )
    }
    return response
}

/**
 * Send a raw PUT (no bearer header, no envelope). Used by uploads.create
 * for the direct S3 step.
 */
internal suspend fun FloopFloop.rawPut(
    url: String,
    body: ByteArray,
    contentType: String,
) {
    val mediaType = contentType.toMediaType()
    val request = Request.Builder()
        .url(url)
        .put(body.toRequestBody(mediaType))
        .build()
    val response = http.executeAsync(request)
    val ok = response.isSuccessful
    response.close()
    if (!ok) {
        throw FloopError(
            code = FloopErrorCode.SERVER_ERROR,
            message = "S3 PUT failed",
            status = response.code,
        )
    }
}

private fun parseErrorEnvelope(body: String, status: Int): Pair<String, String> {
    if (body.isNotBlank()) {
        try {
            val obj = JSON_LENIENT.parseToJsonElement(body).jsonObject
            val errObj = obj["error"]?.jsonObject
            if (errObj != null) {
                val code = errObj["code"]?.jsonPrimitive?.contentOrNull
                val message = errObj["message"]?.jsonPrimitive?.contentOrNull
                if (code != null && message != null) return code to message
            }
        } catch (_: Throwable) {
            // fall through to status-based default below
        }
    }
    val code = when {
        status == 401 -> FloopErrorCode.UNAUTHORIZED
        status == 403 -> FloopErrorCode.FORBIDDEN
        status == 404 -> FloopErrorCode.NOT_FOUND
        status == 409 -> FloopErrorCode.CONFLICT
        status == 422 -> FloopErrorCode.VALIDATION_ERROR
        status == 429 -> FloopErrorCode.RATE_LIMITED
        status == 503 -> FloopErrorCode.SERVICE_UNAVAILABLE
        status in 500..599 -> FloopErrorCode.SERVER_ERROR
        else -> FloopErrorCode.UNKNOWN
    }
    return code to "request failed ($status)"
}

internal fun urlEncode(s: String): String =
    java.net.URLEncoder.encode(s, "UTF-8").replace("+", "%20")
