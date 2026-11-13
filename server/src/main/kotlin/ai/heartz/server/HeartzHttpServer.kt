package ai.heartz.server

import ai.heartz.core.model.User
import ai.heartz.core.service.ApiErrorBody
import ai.heartz.core.service.ApiErrorCode
import ai.heartz.core.service.ApiException
import ai.heartz.core.service.CreateStoryRequest
import ai.heartz.core.service.HeartzJson
import ai.heartz.core.service.HeartzService
import ai.heartz.core.service.HideAuthorRequest
import ai.heartz.core.service.ListenEventRequest
import ai.heartz.core.service.ListenStateResponse
import ai.heartz.core.service.LoginRequest
import ai.heartz.core.service.LoginResponse
import ai.heartz.core.service.ModerationRequest
import ai.heartz.core.service.PlaceList
import ai.heartz.core.service.ReportRequest
import ai.heartz.core.service.StoryList
import ai.heartz.core.service.UserList
import com.sun.net.httpserver.HttpExchange
import com.sun.net.httpserver.HttpServer
import kotlinx.serialization.KSerializer
import kotlinx.serialization.SerializationException
import java.net.InetSocketAddress
import java.security.SecureRandom
import java.util.Base64
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Executors

/**
 * HeartzService를 HTTP로 노출한다. 토큰은 테스트 로그인으로만 발급되며 메모리에만 둔다.
 * 비밀키·실명 확인은 없다(개발·시연 전용).
 */
class HeartzHttpServer(
    private val service: HeartzService,
    private val host: String = "127.0.0.1",
    private val port: Int = 8787,
    private val log: (String) -> Unit = {},
) {
    private var server: HttpServer? = null
    private val sessions = ConcurrentHashMap<String, String>()
    private val random = SecureRandom()

    val boundPort: Int get() = server?.address?.port ?: error("server not started")

    fun start() {
        val s = HttpServer.create(InetSocketAddress(host, port), 0)
        s.executor = Executors.newFixedThreadPool(8)
        s.createContext("/") { ex -> handle(ex) }
        s.start()
        server = s
    }

    fun stop() {
        server?.stop(0)
        (server?.executor as? java.util.concurrent.ExecutorService)?.shutdownNow()
        server = null
    }

    private class Reply(val code: Int, val body: ByteArray, val contentType: String)

    private fun handle(ex: HttpExchange) {
        val started = System.nanoTime()
        val reply = try {
            route(ex)
        } catch (e: ApiException) {
            error(e.code, e.message)
        } catch (e: SerializationException) {
            error(ApiErrorCode.INVALID, "malformed request body")
        } catch (e: IllegalArgumentException) {
            error(ApiErrorCode.INVALID, e.message ?: "invalid request")
        } catch (e: Exception) {
            log("internal error: ${e::class.java.simpleName}: ${e.message}")
            Reply(500, """{"code":"UNAVAILABLE","message":"internal error"}""".toByteArray(), JSON)
        }
        ex.responseHeaders.add("Content-Type", reply.contentType)
        ex.responseHeaders.add("Cache-Control", "no-store")
        ex.sendResponseHeaders(reply.code, if (reply.body.isEmpty()) -1 else reply.body.size.toLong())
        if (reply.body.isNotEmpty()) ex.responseBody.use { it.write(reply.body) }
        ex.close()
        val ms = (System.nanoTime() - started) / 1_000_000
        // 토큰·본문은 로그에 남기지 않는다
        log("${ex.requestMethod} ${ex.requestURI.path} -> ${reply.code} (${ms}ms)")
    }

    private fun route(ex: HttpExchange): Reply {
        val method = ex.requestMethod
        val parts = ex.requestURI.path.trim('/').split('/').filter { it.isNotEmpty() }
        if (parts.firstOrNull() != "v1") return notFound()
        val p = parts.drop(1)

        // 인증 없이 허용되는 경로
        when {
            method == "GET" && p == listOf("health") ->
                return text("""{"ok":true,"service":"heartz-dev-server"}""")
            method == "GET" && p == listOf("test-users") ->
                return json(UserList.serializer(), UserList(service.users().filter { it.canSignIn }))
            method == "POST" && p == listOf("auth", "test-login") -> {
                val req = read(ex, LoginRequest.serializer())
                val user = service.user(req.userId)?.takeIf { it.canSignIn }
                    ?: throw ApiException(ApiErrorCode.UNAUTHORIZED, "unknown test account")
                val token = newToken()
                sessions[token] = user.id
                return json(LoginResponse.serializer(), LoginResponse(token, user))
            }
        }

        val actor = authenticate(ex)
        return when {
            method == "GET" && p == listOf("places") -> json(PlaceList.serializer(), PlaceList(service.places()))
            method == "GET" && p == listOf("feed") -> json(StoryList.serializer(), StoryList(service.publicFeed(actor)))
            method == "GET" && p == listOf("me", "stories") -> json(StoryList.serializer(), StoryList(service.myStories(actor)))
            method == "GET" && p == listOf("me", "listen-state") ->
                json(ListenStateResponse.serializer(), ListenStateResponse(service.listenState(actor)))
            method == "POST" && p == listOf("me", "listen-events") -> {
                val req = read(ex, ListenEventRequest.serializer())
                json(ListenStateResponse.serializer(), ListenStateResponse(service.recordListen(actor, req.storyId, req.kind)))
            }
            method == "PUT" && p == listOf("me", "profile") -> {
                val req = read(ex, ai.heartz.core.service.ProfileRequest.serializer())
                json(ai.heartz.core.model.User.serializer(), service.setNickname(actor, req.nickname))
            }
            method == "POST" && p == listOf("me", "hidden-authors") -> {
                val req = read(ex, HideAuthorRequest.serializer())
                json(ListenStateResponse.serializer(), ListenStateResponse(service.hideAuthor(actor, req.authorId)))
            }
            method == "DELETE" && p.size == 3 && p[0] == "me" && p[1] == "hidden-authors" ->
                json(ListenStateResponse.serializer(), ListenStateResponse(service.unhideAuthor(actor, p[2])))
            method == "POST" && p == listOf("stories") ->
                json(ai.heartz.core.model.Story.serializer(), service.createDraft(actor, read(ex, CreateStoryRequest.serializer())))
            p.size >= 2 && p[0] == "stories" -> storyRoute(ex, method, p[1], p.drop(2), actor)
            method == "GET" && p == listOf("moderation") ->
                json(ai.heartz.core.service.ModerationOverview.serializer(), service.moderationOverview(actor))
            method == "POST" && p.size == 3 && p[0] == "moderation" && p[1] == "stories" ->
                json(
                    ai.heartz.core.service.ModerationResult.serializer(),
                    service.moderate(actor, p[2], read(ex, ModerationRequest.serializer())),
                )
            else -> notFound()
        }
    }

    private fun storyRoute(ex: HttpExchange, method: String, id: String, rest: List<String>, actor: User): Reply = when {
        method == "GET" && rest.isEmpty() -> json(ai.heartz.core.model.Story.serializer(), service.story(actor, id))
        method == "DELETE" && rest.isEmpty() -> {
            service.deleteOwn(actor, id)
            text("""{"deleted":true}""")
        }
        method == "PUT" && rest == listOf("audio") -> {
            val type = ex.requestHeaders.getFirst("Content-Type") ?: ""
            val bytes = readLimited(ex, HeartzService.MAX_AUDIO_BYTES)
            json(ai.heartz.core.service.UploadResult.serializer(), service.uploadAudio(actor, id, bytes, type.substringBefore(';').trim()))
        }
        method == "GET" && rest == listOf("audio") -> {
            val (story, bytes) = service.audio(actor, id)
            Reply(200, bytes, story.audio?.mimeType ?: "application/octet-stream")
        }
        method == "PUT" && rest == listOf("assist") -> {
            val assist = read(ex, ai.heartz.core.moderation.ReviewAssist.serializer())
            json(ai.heartz.core.model.Story.serializer(), service.attachAssist(actor, id, assist))
        }
        method == "POST" && rest == listOf("submit") ->
            json(ai.heartz.core.service.SubmitResult.serializer(), service.submit(actor, id))
        method == "POST" && rest == listOf("heart") ->
            json(ListenStateResponse.serializer(), ListenStateResponse(service.heart(actor, id, on = true)))
        method == "DELETE" && rest == listOf("heart") ->
            json(ListenStateResponse.serializer(), ListenStateResponse(service.heart(actor, id, on = false)))
        method == "POST" && rest == listOf("report") -> {
            val req = read(ex, ReportRequest.serializer())
            json(ai.heartz.core.service.Report.serializer(), service.report(actor, id, req.reason))
        }
        else -> notFound()
    }

    private fun authenticate(ex: HttpExchange): User {
        val header = ex.requestHeaders.getFirst("Authorization") ?: ""
        val token = header.removePrefix("Bearer ").trim()
        val userId = sessions[token] ?: throw ApiException(ApiErrorCode.UNAUTHORIZED, "login required")
        return service.user(userId) ?: throw ApiException(ApiErrorCode.UNAUTHORIZED, "login required")
    }

    private fun newToken(): String {
        val b = ByteArray(24)
        random.nextBytes(b)
        return Base64.getUrlEncoder().withoutPadding().encodeToString(b)
    }

    private fun readLimited(ex: HttpExchange, max: Long): ByteArray {
        val input = ex.requestBody
        val out = java.io.ByteArrayOutputStream()
        val buf = ByteArray(16 * 1024)
        var total = 0L
        while (true) {
            val n = input.read(buf)
            if (n < 0) break
            total += n
            if (total > max) {
                // 나머지 본문을 버려 연결을 정리한다
                while (input.read(buf) >= 0) Unit
                throw ApiException(ApiErrorCode.TOO_LARGE, "body exceeds $max bytes")
            }
            out.write(buf, 0, n)
        }
        return out.toByteArray()
    }

    private fun <T> read(ex: HttpExchange, serializer: KSerializer<T>): T =
        HeartzJson.decodeFromString(serializer, String(readLimited(ex, 64 * 1024)))

    private fun <T> json(serializer: KSerializer<T>, value: T) =
        Reply(200, HeartzJson.encodeToString(serializer, value).toByteArray(), JSON)

    private fun text(body: String) = Reply(200, body.toByteArray(), JSON)

    private fun error(code: ApiErrorCode, message: String) =
        Reply(code.httpStatus, HeartzJson.encodeToString(ApiErrorBody.serializer(), ApiErrorBody(code, message)).toByteArray(), JSON)

    private fun notFound() = error(ApiErrorCode.NOT_FOUND, "no such route")

    private companion object {
        const val JSON = "application/json; charset=utf-8"
    }
}
