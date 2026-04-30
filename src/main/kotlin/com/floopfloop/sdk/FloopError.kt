package com.floopfloop.sdk

import kotlin.time.Duration

/**
 * Single exception type thrown by every SDK call on non-2xx responses
 * and on network / timeout failures. Inspect [code] to branch — unknown
 * server codes pass through verbatim in [code] rather than raising a
 * subclass we'd have to keep in sync.
 *
 * Mirrors the Node / Python / Go / Rust / Ruby / PHP / Swift SDK error
 * taxonomy.
 */
public class FloopError(
    /** Application error code. See [FloopErrorCode] for the known set. */
    public val code: String,

    message: String,

    /** HTTP status code. `0` for transport-level failures. */
    public val status: Int = 0,

    /**
     * The server's `x-request-id` header value, when present. Quote it in
     * bug reports — it lets support pull the trace.
     */
    public val requestId: String? = null,

    /**
     * Duration to wait before retrying, parsed from the `Retry-After`
     * response header (delta-seconds OR HTTP-date). `null` when not set.
     */
    public val retryAfter: Duration? = null,

    cause: Throwable? = null,
) : Exception(message, cause) {

    override fun toString(): String = buildString {
        append("floop: [").append(code)
        if (status > 0) append(" ").append(status)
        append("] ").append(message ?: "")
        if (requestId != null) append(" (request ").append(requestId).append(")")
    }
}

/**
 * Error codes returned by the FloopFloop API plus a few the SDK itself
 * produces for transport-level failures. Documented as constants — the
 * `code` field on [FloopError] is a plain `String`, so unknown server
 * codes pass through verbatim and callers can branch on them without an
 * SDK update.
 */
public object FloopErrorCode {
    public const val UNAUTHORIZED: String = "UNAUTHORIZED"
    public const val FORBIDDEN: String = "FORBIDDEN"
    public const val VALIDATION_ERROR: String = "VALIDATION_ERROR"
    public const val RATE_LIMITED: String = "RATE_LIMITED"
    public const val NOT_FOUND: String = "NOT_FOUND"
    public const val CONFLICT: String = "CONFLICT"
    public const val SERVICE_UNAVAILABLE: String = "SERVICE_UNAVAILABLE"
    public const val SERVER_ERROR: String = "SERVER_ERROR"
    public const val NETWORK_ERROR: String = "NETWORK_ERROR"
    public const val TIMEOUT: String = "TIMEOUT"
    public const val BUILD_FAILED: String = "BUILD_FAILED"
    public const val BUILD_CANCELLED: String = "BUILD_CANCELLED"
    public const val INSUFFICIENT_CREDITS: String = "INSUFFICIENT_CREDITS"
    public const val PAYMENT_FAILED: String = "PAYMENT_FAILED"
    public const val UNKNOWN: String = "UNKNOWN"
}
