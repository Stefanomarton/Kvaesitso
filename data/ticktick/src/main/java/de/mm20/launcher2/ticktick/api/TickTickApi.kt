package de.mm20.launcher2.ticktick.api

import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.plugins.defaultRequest
import io.ktor.client.request.bearerAuth
import io.ktor.client.request.delete
import io.ktor.client.request.get
import io.ktor.client.request.parameter
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

@Serializable
data class ProjectDto(
    val id: String,
    val name: String,
    val color: String? = null,
    val closed: Boolean? = null,
    val kind: String? = null,
    val sortOrder: Long? = null,
)

@Serializable
data class TaskDto(
    val id: String? = null,
    val projectId: String,
    val title: String,
    val content: String? = null,
    val desc: String? = null,
    val isAllDay: Boolean? = null,
    val startDate: String? = null,
    val dueDate: String? = null,
    val timeZone: String? = null,
    val repeatFlag: String? = null,
    val priority: Int? = null,
    val status: Int? = null,
    val tags: List<String>? = null,
    val sortOrder: Long? = null,
    val kind: String? = null,
)

@Serializable
data class ProjectDataDto(
    val project: ProjectDto? = null,
    val tasks: List<TaskDto> = emptyList(),
)

@Serializable
data class HabitDto(
    val id: String,
    val name: String,
    val iconRes: String? = null,
    val color: String? = null,
    val sortOrder: Long? = null,
    /** 0 = active, 1 = archived */
    val status: Int? = null,
    /** "Boolean" or "Real" */
    val type: String? = null,
    val goal: Double? = null,
    val step: Double? = null,
    val unit: String? = null,
    val repeatRule: String? = null,
)

@Serializable
data class CheckinEntryDto(
    val stamp: Int,
    val value: Double? = null,
    val goal: Double? = null,
    val status: Int? = null,
)

@Serializable
data class HabitCheckinDto(
    val habitId: String,
    val checkins: List<CheckinEntryDto> = emptyList(),
)

@Serializable
data class CheckinRequest(
    val stamp: Int,
    val value: Double,
    val goal: Double,
    val status: Int,
)

@Serializable
data class MoveRequest(
    val fromProjectId: String,
    val toProjectId: String,
    val taskId: String,
)

class TickTickUnauthorizedException : Exception("TickTick token rejected")
class TickTickHttpException(val status: HttpStatusCode) : Exception("TickTick HTTP ${status.value}")

/**
 * Client for the official Open API, authenticated with a personal API token.
 * Docs: https://developer.ticktick.com/docs/openapi.md
 */
class TickTickApi(private val token: () -> String?) {

    private val json = Json {
        ignoreUnknownKeys = true
        explicitNulls = false
    }

    private val client = HttpClient {
        install(ContentNegotiation) { json(json) }
        defaultRequest {
            url("https://api.ticktick.com/open/v1/")
            bearerAuth(token() ?: "")
        }
        expectSuccess = false
    }

    private suspend fun HttpResponse.check(): HttpResponse {
        if (status == HttpStatusCode.Unauthorized) throw TickTickUnauthorizedException()
        if (!status.isSuccess()) throw TickTickHttpException(status)
        return this
    }

    suspend fun projects(): List<ProjectDto> = client.get("project").check().body()

    /** Open tasks of a project. Use "inbox" for the inbox. */
    suspend fun projectData(projectId: String): ProjectDataDto =
        client.get("project/$projectId/data").check().body()

    suspend fun createTask(task: TaskDto): TaskDto = client.post("task") {
        contentType(ContentType.Application.Json)
        setBody(task)
    }.check().body()

    /** Partial update; `fields` must contain id and projectId. */
    suspend fun updateTask(taskId: String, fields: JsonObject): TaskDto = client.post("task/$taskId") {
        contentType(ContentType.Application.Json)
        setBody(fields)
    }.check().body()

    suspend fun moveTask(taskId: String, fromProjectId: String, toProjectId: String) {
        client.post("task/move") {
            contentType(ContentType.Application.Json)
            setBody(listOf(MoveRequest(fromProjectId, toProjectId, taskId)))
        }.check()
    }

    suspend fun completeTask(projectId: String, taskId: String) {
        client.post("project/$projectId/task/$taskId/complete").check()
    }

    suspend fun deleteTask(projectId: String, taskId: String) {
        client.delete("project/$projectId/task/$taskId").check()
    }

    suspend fun habits(): List<HabitDto> = client.get("habit").check().body()

    suspend fun checkins(habitIds: List<String>, from: Int, to: Int): List<HabitCheckinDto> {
        if (habitIds.isEmpty()) return emptyList()
        return client.get("habit/checkins") {
            parameter("habitIds", habitIds.joinToString(","))
            parameter("from", from)
            parameter("to", to)
        }.check().body()
    }

    suspend fun checkin(habitId: String, request: CheckinRequest) {
        client.post("habit/$habitId/checkin") {
            contentType(ContentType.Application.Json)
            setBody(request)
        }.check()
    }
}
