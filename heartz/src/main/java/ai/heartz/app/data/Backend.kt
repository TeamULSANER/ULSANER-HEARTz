package ai.heartz.app.data

import ai.heartz.core.model.ListenKind
import ai.heartz.core.model.ListenState
import ai.heartz.core.model.Place
import ai.heartz.core.model.Story
import ai.heartz.core.moderation.ReviewAssist
import ai.heartz.core.model.User
import ai.heartz.core.seed.SeedData
import ai.heartz.core.service.ApiErrorBody
import ai.heartz.core.service.ApiErrorCode
import ai.heartz.core.service.ApiException
import ai.heartz.core.service.CreateStoryRequest
import ai.heartz.core.service.FileBlobStore
import ai.heartz.core.service.FileHeartzStore
import ai.heartz.core.service.HeartzJson
import ai.heartz.core.service.HeartzService
import ai.heartz.core.service.HideAuthorRequest
import ai.heartz.core.service.ListenEventRequest
import ai.heartz.core.service.ListenStateResponse
import ai.heartz.core.service.LoginRequest
import ai.heartz.core.service.LoginResponse
import ai.heartz.core.service.ModerationOverview
import ai.heartz.core.service.ModerationRequest
import ai.heartz.core.service.ModerationResult
import ai.heartz.core.service.PlaceList
import ai.heartz.core.service.Report
import ai.heartz.core.service.ReportRequest
import ai.heartz.core.service.StoryList
import ai.heartz.core.service.SubmitResult
import ai.heartz.core.service.UploadResult
import ai.heartz.core.service.UserList
import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.KSerializer
import java.io.File
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL

enum class BackendMode { LOCAL_DEMO, DEV_SERVER }

/**
 * 앱이 쓰는 Heartz API. 두 구현은 같은 core 규칙(HeartzService)을 따른다.
 * - [LocalDemoBackend]: 기기 내 저장소에서 HeartzService를 직접 실행 (서버 없이 단독 동작, 모든 계정이 한 기기에 있음)
 * - [RemoteBackend]: 로컬 개발 서버(HTTP)
 */
interface HeartzBackend {
    val mode: BackendMode
    val label: String

    suspend fun testUsers(): List<User>
    suspend fun signIn(userId: String): User
    fun signOut()

    suspend fun places(): List<Place>
    suspend fun feed(): List<Story>
    suspend fun myStories(): List<Story>
    suspend fun story(id: String): Story

    suspend fun createDraft(req: CreateStoryRequest): Story
    suspend fun uploadAudio(id: String, file: File, mimeType: String): UploadResult
    suspend fun submit(id: String): SubmitResult
    suspend fun attachAssist(id: String, assist: ReviewAssist): Story
    suspend fun delete(id: String)
    /** 음원을 [dest]에 저장한다. 접근할 수 없으면 ApiException(NOT_FOUND). */
    suspend fun downloadAudio(id: String, dest: File)

    suspend fun moderation(): ModerationOverview
    suspend fun moderate(id: String, req: ModerationRequest): ModerationResult
    suspend fun report(id: String, reason: String): Report

    suspend fun listenState(): ListenState
    suspend fun recordListen(id: String, kind: ListenKind): ListenState
    suspend fun heart(id: String, on: Boolean): ListenState
    suspend fun hideAuthor(authorId: String): ListenState
    suspend fun unhideAuthor(authorId: String): ListenState
    /** 닉네임 저장(Pleos Connect 프로필 이름을 따른다) */
    suspend fun setNickname(nickname: String): User
}

class LocalDemoBackend(context: Context) : HeartzBackend {
    private val appContext = context.applicationContext
    private val root = File(appContext.filesDir, "demo-backend")
    private val service = HeartzService(FileHeartzStore(File(root, "db.json")), FileBlobStore(File(root, "blobs")))
    @Volatile private var actor: User? = null
    @Volatile private var seeded = false

    override val mode = BackendMode.LOCAL_DEMO
    override val label = "기기 내 데모 백엔드"

    private suspend fun <T> io(block: () -> T): T = withContext(Dispatchers.IO) {
        ensureSeeded()
        block()
    }

    private fun ensureSeeded() {
        if (seeded) return
        synchronized(this) {
            if (seeded) return
            val blobs = SeedData.audioFiles.mapValues { (_, name) ->
                appContext.assets.open("audio/$name").use { it.readBytes() }
            }
            service.seedIfEmpty(SeedData.db(System.currentTimeMillis()), blobs)
            // 이전에 만든 기기 데이터의 시드 사연도 지금의 정의(음원·길이·표시)로 맞춘다. 팀원 녹음으로 바뀐 사연 포함
            service.refreshSeedStories(SeedData.db(System.currentTimeMillis()).stories, blobs)
            // 장소 목록을 서울 구별 랜드마크로 맞춘다(예전 기기 데이터 포함, 사연은 그대로)
            service.refreshSeedPlaces(ai.heartz.core.seed.SeoulLandmarks.places)
            seeded = true
        }
    }

    private fun me(): User = actor ?: throw ApiException(ApiErrorCode.UNAUTHORIZED, "로그인이 필요합니다")

    override suspend fun testUsers() = io { service.users().filter { it.canSignIn } }
    override suspend fun signIn(userId: String) = io {
        val u = service.user(userId)?.takeIf { it.canSignIn } ?: throw ApiException(ApiErrorCode.UNAUTHORIZED, "unknown test account")
        actor = u
        u
    }
    override fun signOut() { actor = null }

    override suspend fun places() = io { service.places() }
    override suspend fun feed() = io { service.publicFeed(me()) }
    override suspend fun myStories() = io { service.myStories(me()) }
    override suspend fun story(id: String) = io { service.story(me(), id) }
    override suspend fun createDraft(req: CreateStoryRequest) = io { service.createDraft(me(), req) }
    override suspend fun uploadAudio(id: String, file: File, mimeType: String) =
        io { service.uploadAudio(me(), id, file.readBytes(), mimeType) }
    override suspend fun submit(id: String) = io { service.submit(me(), id) }
    override suspend fun attachAssist(id: String, assist: ReviewAssist) = io { service.attachAssist(me(), id, assist) }
    override suspend fun delete(id: String) = io { service.deleteOwn(me(), id) }
    override suspend fun downloadAudio(id: String, dest: File) = io {
        val (_, bytes) = service.audio(me(), id)
        writeAtomically(dest, bytes)
    }
    override suspend fun moderation() = io { service.moderationOverview(me()) }
    override suspend fun moderate(id: String, req: ModerationRequest) = io { service.moderate(me(), id, req) }
    override suspend fun report(id: String, reason: String) = io { service.report(me(), id, reason) }
    override suspend fun listenState() = io { service.listenState(me()) }
    override suspend fun recordListen(id: String, kind: ListenKind) = io { service.recordListen(me(), id, kind) }
    override suspend fun heart(id: String, on: Boolean) = io { service.heart(me(), id, on) }
    override suspend fun hideAuthor(authorId: String) = io { service.hideAuthor(me(), authorId) }
    override suspend fun unhideAuthor(authorId: String) = io { service.unhideAuthor(me(), authorId) }
    override suspend fun setNickname(nickname: String) = io { service.setNickname(me(), nickname).also { actor = it } }

    /** debug 화면 전용: 이 기기 안 데모 데이터에서 지금 계정의 받은·들은 기록을 지운다 */
    suspend fun clearListenHistory() = io { service.clearListenHistory(me()) }
}

/** 로컬 개발 서버 클라이언트. 토큰은 메모리에만 두고 로그에 남기지 않는다. */
class RemoteBackend(private val baseUrl: String) : HeartzBackend {
    override val mode = BackendMode.DEV_SERVER
    override val label = "HEARTz 개발 서버 ($baseUrl)"

    @Volatile private var token: String? = null
    @Volatile private var userId: String? = null

    private class Raw(val code: Int, val body: ByteArray)

    private fun exchange(method: String, path: String, body: ByteArray?, type: String, auth: Boolean): Raw {
        val c = URL(baseUrl.trimEnd('/') + path).openConnection() as HttpURLConnection
        c.connectTimeout = 5_000
        c.readTimeout = 15_000
        c.requestMethod = method
        if (auth) token?.let { c.setRequestProperty("Authorization", "Bearer $it") }
        if (body != null) {
            c.doOutput = true
            c.setRequestProperty("Content-Type", type)
            c.setFixedLengthStreamingMode(body.size)
            c.outputStream.use { it.write(body) }
        }
        try {
            val code = c.responseCode
            val stream = if (code < 400) c.inputStream else c.errorStream
            return Raw(code, stream?.use { it.readBytes() } ?: ByteArray(0))
        } finally {
            c.disconnect()
        }
    }

    /** 401이면 같은 테스트 계정으로 한 번 다시 로그인한다(서버 재시작 시 토큰이 사라짐). */
    private suspend fun call(method: String, path: String, body: ByteArray? = null, type: String = JSON): ByteArray =
        withContext(Dispatchers.IO) {
            try {
                var r = exchange(method, path, body, type, auth = true)
                if (r.code == 401 && userId != null) {
                    relogin()
                    r = exchange(method, path, body, type, auth = true)
                }
                if (r.code >= 400) throw toApi(r)
                r.body
            } catch (e: IOException) {
                throw ApiException(ApiErrorCode.UNAVAILABLE, "개발 서버에 연결할 수 없습니다 (${e::class.java.simpleName})")
            }
        }

    private fun relogin() {
        val id = userId ?: return
        val r = exchange("POST", "/v1/auth/test-login", HeartzJson.encodeToString(LoginRequest.serializer(), LoginRequest(id)).toByteArray(), JSON, auth = false)
        if (r.code == 200) token = HeartzJson.decodeFromString(LoginResponse.serializer(), String(r.body)).token
    }

    private fun toApi(r: Raw): ApiException = runCatching {
        val b = HeartzJson.decodeFromString(ApiErrorBody.serializer(), String(r.body))
        ApiException(b.code, b.message)
    }.getOrElse { ApiException(ApiErrorCode.UNAVAILABLE, "HTTP ${r.code}") }

    private suspend fun <T> get(path: String, s: KSerializer<T>): T = HeartzJson.decodeFromString(s, String(call("GET", path)))
    private suspend fun <B, T> post(path: String, bs: KSerializer<B>, b: B, s: KSerializer<T>): T =
        HeartzJson.decodeFromString(s, String(call("POST", path, HeartzJson.encodeToString(bs, b).toByteArray())))
    private suspend fun <T> postEmpty(path: String, s: KSerializer<T>): T =
        HeartzJson.decodeFromString(s, String(call("POST", path, ByteArray(0))))

    override suspend fun testUsers() = get("/v1/test-users", UserList.serializer()).users

    override suspend fun signIn(userId: String): User = withContext(Dispatchers.IO) {
        try {
            val r = exchange("POST", "/v1/auth/test-login", HeartzJson.encodeToString(LoginRequest.serializer(), LoginRequest(userId)).toByteArray(), JSON, auth = false)
            if (r.code >= 400) throw toApi(r)
            val resp = HeartzJson.decodeFromString(LoginResponse.serializer(), String(r.body))
            token = resp.token
            this@RemoteBackend.userId = userId
            resp.user
        } catch (e: IOException) {
            throw ApiException(ApiErrorCode.UNAVAILABLE, "개발 서버에 연결할 수 없습니다 (${e::class.java.simpleName})")
        }
    }

    override fun signOut() {
        token = null
        userId = null
    }

    override suspend fun places() = get("/v1/places", PlaceList.serializer()).places
    override suspend fun feed() = get("/v1/feed", StoryList.serializer()).stories
    override suspend fun myStories() = get("/v1/me/stories", StoryList.serializer()).stories
    override suspend fun story(id: String) = get("/v1/stories/$id", Story.serializer())
    override suspend fun createDraft(req: CreateStoryRequest) = post("/v1/stories", CreateStoryRequest.serializer(), req, Story.serializer())
    override suspend fun uploadAudio(id: String, file: File, mimeType: String): UploadResult {
        val bytes = withContext(Dispatchers.IO) { file.readBytes() }
        return HeartzJson.decodeFromString(UploadResult.serializer(), String(call("PUT", "/v1/stories/$id/audio", bytes, mimeType)))
    }
    override suspend fun submit(id: String) = postEmpty("/v1/stories/$id/submit", SubmitResult.serializer())
    override suspend fun attachAssist(id: String, assist: ReviewAssist): Story {
        val body = HeartzJson.encodeToString(ReviewAssist.serializer(), assist).toByteArray()
        return HeartzJson.decodeFromString(Story.serializer(), String(call("PUT", "/v1/stories/$id/assist", body)))
    }
    override suspend fun delete(id: String) {
        call("DELETE", "/v1/stories/$id")
    }
    override suspend fun downloadAudio(id: String, dest: File) {
        val bytes = call("GET", "/v1/stories/$id/audio")
        withContext(Dispatchers.IO) { writeAtomically(dest, bytes) }
    }
    override suspend fun moderation() = get("/v1/moderation", ModerationOverview.serializer())
    override suspend fun moderate(id: String, req: ModerationRequest) =
        post("/v1/moderation/stories/$id", ModerationRequest.serializer(), req, ModerationResult.serializer())
    override suspend fun report(id: String, reason: String) =
        post("/v1/stories/$id/report", ReportRequest.serializer(), ReportRequest(reason), Report.serializer())
    override suspend fun listenState() = get("/v1/me/listen-state", ListenStateResponse.serializer()).state
    override suspend fun recordListen(id: String, kind: ListenKind) =
        post("/v1/me/listen-events", ListenEventRequest.serializer(), ListenEventRequest(id, kind), ListenStateResponse.serializer()).state
    override suspend fun heart(id: String, on: Boolean) =
        HeartzJson.decodeFromString(
            ListenStateResponse.serializer(),
            String(call(if (on) "POST" else "DELETE", "/v1/stories/$id/heart", if (on) ByteArray(0) else null)),
        ).state
    override suspend fun hideAuthor(authorId: String) =
        post("/v1/me/hidden-authors", HideAuthorRequest.serializer(), HideAuthorRequest(authorId), ListenStateResponse.serializer()).state
    override suspend fun unhideAuthor(authorId: String) =
        HeartzJson.decodeFromString(ListenStateResponse.serializer(), String(call("DELETE", "/v1/me/hidden-authors/$authorId"))).state
    override suspend fun setNickname(nickname: String): User {
        val body = HeartzJson.encodeToString(ai.heartz.core.service.ProfileRequest.serializer(), ai.heartz.core.service.ProfileRequest(nickname)).toByteArray()
        return HeartzJson.decodeFromString(User.serializer(), String(call("PUT", "/v1/me/profile", body)))
    }

    private companion object {
        const val JSON = "application/json; charset=utf-8"
    }
}

internal fun writeAtomically(dest: File, bytes: ByteArray) {
    dest.parentFile?.mkdirs()
    val tmp = File(dest.parentFile, ".${dest.name}.part")
    tmp.writeBytes(bytes)
    if (!tmp.renameTo(dest)) {
        dest.delete()
        if (!tmp.renameTo(dest)) throw IOException("cannot move audio into cache")
    }
}
