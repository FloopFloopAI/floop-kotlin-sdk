package com.floopfloop.sdk.resources

import com.floopfloop.sdk.FloopFloop
import com.floopfloop.sdk.JSON_LENIENT
import com.floopfloop.sdk.requestJson
import com.floopfloop.sdk.urlEncode
import kotlinx.serialization.Serializable

@Serializable
public data class LibraryProject(
    val id: String,
    val name: String,
    val subdomain: String? = null,
    val description: String? = null,
    val botType: String? = null,
    val url: String? = null,
    val thumbnailUrl: String? = null,
    val likes: Int? = null,
    val createdAt: String,
)

public data class LibraryListOptions(
    val botType: String? = null,
    val search: String? = null,
    val sort: String? = null,
    val page: Int? = null,
    val limit: Int? = null,
)

@Serializable
public data class ClonedProject(val project: Project)

@Serializable
internal data class CloneBody(val subdomain: String)

public class Library internal constructor(private val client: FloopFloop) {

    public suspend fun list(opts: LibraryListOptions = LibraryListOptions()): List<LibraryProject> {
        val params = mutableListOf<String>()
        opts.botType?.let { params += "botType=${urlEncode(it)}" }
        opts.search?.let  { params += "search=${urlEncode(it)}" }
        opts.sort?.let    { params += "sort=${urlEncode(it)}" }
        opts.page?.let    { params += "page=$it" }
        opts.limit?.let   { params += "limit=$it" }
        val path = if (params.isEmpty()) "/api/v1/library" else "/api/v1/library?${params.joinToString("&")}"
        return client.requestJson("GET", path)
    }

    public suspend fun clone(projectId: String, subdomain: String): ClonedProject {
        val body = JSON_LENIENT.encodeToString(CloneBody.serializer(), CloneBody(subdomain))
        return client.requestJson("POST", "/api/v1/library/${urlEncode(projectId)}/clone", body)
    }
}
