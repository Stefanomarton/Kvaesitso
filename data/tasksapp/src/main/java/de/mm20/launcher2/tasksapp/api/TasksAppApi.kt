package de.mm20.launcher2.tasksapp.api

import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.HttpResponse
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import io.ktor.http.isSuccess
import io.ktor.serialization.kotlinx.json.json
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

@Serializable
data class ListDto(
    val id: String,
    val name: String,
    val color: String? = null,
)

@Serializable
data class TaskDto(
    val id: String,
    val title: String,
    /** "" = Inbox */
    val list: String = "",
    val list_name: String? = null,
    /** todo, doing or done */
    val status: String = "todo",
    /** YYYY-MM-DD or "" */
    val due_date: String = "",
    /** HH:MM or "" */
    val due_time: String = "",
    /** 0 none … 3 high */
    val priority: Int = 0,
    val repeat: String = "",
    val tags: List<String> = emptyList(),
    val notes: String = "",
)

@Serializable
private data class TaskPage(val tasks: List<TaskDto> = emptyList())

@Serializable
private data class Auth(val token: String)

@Serializable
private data class ErrorBody(val message: String? = null)

class TasksAppUnauthorizedException : Exception("Login rejected")
class TasksAppHttpException(val status: HttpStatusCode, message: String?) :
    Exception(message ?: "HTTP ${status.value}")

data class TasksAccount(
    /** e.g. https://tasks.marton.dev */
    val url: String,
    val email: String,
    val password: String,
    /** Cloudflare Access service token; blank when the server isn't behind Access */
    val accessId: String,
    val accessSecret: String,
)

/**
 * Client for the Tasks app (PocketBase): logs in as the user and calls the server's tools
 * (`POST /api/tool/{name}`, the same tools as its MCP server).
 */
class TasksAppApi(
    private val account: () -> TasksAccount?,
    private val loadToken: () -> String?,
    private val saveToken: (String?) -> Unit,
) {

    private val json = Json {
        ignoreUnknownKeys = true
        explicitNulls = false
    }

    private val client = HttpClient {
        install(ContentNegotiation) { json(json) }
        expectSuccess = false
        // Cloudflare Access answers a missing/wrong service token with a redirect to its login page
        followRedirects = false
    }

    private suspend fun post(account: TasksAccount, path: String, body: JsonObject, token: String?): HttpResponse =
        client.post(account.url.trimEnd('/') + "/api/" + path) {
            if (account.accessId.isNotBlank()) {
                header("CF-Access-Client-Id", account.accessId)
                header("CF-Access-Client-Secret", account.accessSecret)
            }
            if (token != null) header("Authorization", token)
            contentType(ContentType.Application.Json)
            setBody(body)
        }

    private suspend fun HttpResponse.check(): HttpResponse {
        if (status == HttpStatusCode.Unauthorized || status == HttpStatusCode.Forbidden) {
            throw TasksAppUnauthorizedException()
        }
        if (status.value in 300..399) throw TasksAppHttpException(status, "Cloudflare Access rejected the service token")
        if (!status.isSuccess()) {
            throw TasksAppHttpException(status, runCatching { body<ErrorBody>().message }.getOrNull())
        }
        return this
    }

    private suspend fun login(account: TasksAccount): String {
        val auth = post(account, "collections/users/auth-with-password", buildJsonObject {
            put("identity", account.email)
            put("password", account.password)
        }, null).let {
            // PocketBase answers a wrong password with 400
            if (it.status == HttpStatusCode.BadRequest) throw TasksAppUnauthorizedException()
            it.check()
        }.body<Auth>()
        saveToken(auth.token)
        return auth.token
    }

    /** One tool call; logs in when there is no token yet or the saved one expired. */
    private suspend fun call(tool: String, args: JsonObject = JsonObject(emptyMap())): HttpResponse {
        val account = account() ?: throw TasksAppUnauthorizedException()
        val token = loadToken() ?: login(account)
        val response = post(account, "tool/$tool", args, token)
        if (response.status != HttpStatusCode.Unauthorized) return response.check()
        return post(account, "tool/$tool", args, login(account)).check()
    }

    suspend fun lists(): List<ListDto> = call("list_lists").body()

    // ponytail: list_tasks caps at 500 open tasks, page with from/to if that ever runs out
    suspend fun openTasks(): List<TaskDto> = call("list_tasks", buildJsonObject { put("limit", 500) })
        .body<TaskPage>().tasks

    suspend fun quickAdd(text: String, list: String?): TaskDto = call("quick_add", buildJsonObject {
        put("text", text)
        if (list != null) put("list", list)
    }).body()

    /** [fields] as in the server's update_task: title, notes, list, due_date, due_time, priority, clear. */
    suspend fun updateTask(fields: JsonObject): TaskDto = call("update_task", fields).body()

    /** A recurring task moves to its next date and stays open. */
    suspend fun completeTask(id: String) {
        call("complete_task", buildJsonObject { put("id", id) })
    }

    suspend fun reopenTask(id: String) {
        call("reopen_task", buildJsonObject { put("id", id) })
    }

    suspend fun deleteTask(id: String) {
        call("delete_task", buildJsonObject { put("id", id) })
    }
}
