package com.floopfloop.sdk.resources

import com.floopfloop.sdk.FloopError
import com.floopfloop.sdk.FloopErrorCode
import com.floopfloop.sdk.FloopFloop
import com.floopfloop.sdk.JSON_LENIENT
import com.floopfloop.sdk.rawPut
import com.floopfloop.sdk.requestJson
import kotlinx.serialization.Serializable

/** Maximum upload size (5 MB), matching the backend's `MAX_BYTES`. */
public const val MAX_UPLOAD_BYTES: Int = 5 * 1024 * 1024

private val EXT_TO_MIME = mapOf(
    ".png" to "image/png",
    ".jpg" to "image/jpeg",
    ".jpeg" to "image/jpeg",
    ".gif" to "image/gif",
    ".svg" to "image/svg+xml",
    ".webp" to "image/webp",
    ".ico" to "image/x-icon",
    ".pdf" to "application/pdf",
    ".txt" to "text/plain",
    ".csv" to "text/csv",
    ".doc" to "application/msword",
    ".docx" to "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
)

public fun guessMimeType(fileName: String): String? {
    val lower = fileName.lowercase()
    val dot = lower.lastIndexOf('.')
    if (dot < 0) return null
    return EXT_TO_MIME[lower.substring(dot)]
}

@Serializable
public data class UploadedAttachment(
    val key: String,
    val fileName: String,
    val fileType: String,
    val fileSize: Long,
) {
    /** Drop straight into [RefineInput.attachments]. */
    public fun asRefineAttachment(): RefineAttachment =
        RefineAttachment(key = key, fileName = fileName, fileType = fileType, fileSize = fileSize)
}

@Serializable
internal data class PresignBody(val fileName: String, val fileType: String, val fileSize: Long)

@Serializable
internal data class PresignResponse(val uploadUrl: String, val key: String, val fileId: String)

public class Uploads internal constructor(private val client: FloopFloop) {

    public suspend fun create(
        fileName: String,
        bytes: ByteArray,
        fileType: String? = null,
    ): UploadedAttachment {
        val resolvedType = fileType ?: guessMimeType(fileName)
        if (resolvedType == null || resolvedType !in EXT_TO_MIME.values) {
            throw FloopError(
                FloopErrorCode.VALIDATION_ERROR,
                "Unsupported file type for $fileName. Allowed: png, jpg, gif, svg, webp, ico, pdf, txt, csv, doc, docx.",
            )
        }
        if (bytes.size > MAX_UPLOAD_BYTES) {
            val mb = bytes.size / 1024 / 1024
            throw FloopError(
                FloopErrorCode.VALIDATION_ERROR,
                "$fileName is ${mb} MB — the upload limit is 5 MB.",
            )
        }

        val presignBody = JSON_LENIENT.encodeToString(
            PresignBody.serializer(),
            PresignBody(fileName, resolvedType, bytes.size.toLong()),
        )
        val presign: PresignResponse = client.requestJson("POST", "/api/v1/uploads", presignBody)

        client.rawPut(presign.uploadUrl, bytes, resolvedType)

        return UploadedAttachment(
            key = presign.key,
            fileName = fileName,
            fileType = resolvedType,
            fileSize = bytes.size.toLong(),
        )
    }
}
