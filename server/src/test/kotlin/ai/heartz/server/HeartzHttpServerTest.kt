package ai.heartz.server

import ai.heartz.core.model.Mood
import ai.heartz.core.model.StoryStatus
import ai.heartz.core.seed.SeedData
import ai.heartz.core.service.CreateStoryRequest
import ai.heartz.core.service.FileBlobStore
import ai.heartz.core.service.FileHeartzStore
import ai.heartz.core.service.HeartzJson
import ai.heartz.core.service.HeartzService
import ai.heartz.core.service.LoginResponse
import ai.heartz.core.service.ListenStateResponse
import ai.heartz.core.service.ModerationAction
import ai.heartz.core.service.ModerationRequest
import ai.heartz.core.service.StoryList
import ai.heartz.core.service.SubmitResult
import ai.heartz.core.service.UploadResult
import ai.heartz.core.service.UserList
import ai.heartz.core.model.Story
import kotlinx.serialization.encodeToString
import org.junit.After
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.net.HttpURLConnection
import java.net.URL

class HeartzHttpServerTest {
    @get:Rule
    val tmp = TemporaryFolder()

    private lateinit var server: HeartzHttpServer
    private val seedBytes = byteArrayOf(7, 7, 7)

    data class Resp(val code: Int, val body: ByteArray, val contentType: String?) {
        val text get() = String(body)
    }

    @Before
    fun setUp() {
        val service = HeartzService(FileHeartzStore(tmp.root.resolve("db.json")), FileBlobStore(tmp.root.resolve("blobs")))
        service.seedIfEmpty(SeedData.db(System.currentTimeMillis()), SeedData.audioFiles.keys.associateWith { seedBytes })
        server = HeartzHttpServer(service, port = 0)
        server.start()
    }

    @After
    fun tearDown() = server.stop()

    private fun call(method: String, path: String, token: String? = null, body: ByteArray? = null, type: String = "application/json"): Resp {
        val c = URL("http://127.0.0.1:${server.boundPort}$path").openConnection() as HttpURLConnection
        c.requestMethod = method
        token?.let { c.setRequestProperty("Authorization", "Bearer $it") }
        if (body != null) {
            c.doOutput = true
            c.setRequestProperty("Content-Type", type)
            c.outputStream.use { it.write(body) }
        }
        val code = c.responseCode
        val stream = if (code < 400) c.inputStream else c.errorStream
        return Resp(code, stream?.use { it.readBytes() } ?: ByteArray(0), c.contentType)
    }

    private fun login(userId: String): String {
        val r = call("POST", "/v1/auth/test-login", body = """{"userId":"$userId"}""".toByteArray())
        assertEquals(r.text, 200, r.code)
        return HeartzJson.decodeFromString(LoginResponse.serializer(), r.text).token
    }

    @Test
    fun pleosNicknameSurvivesReloginAndInvalidInputIsRejected() {
        val token = login(SeedData.DRIVER_JANG)
        val saved = call("PUT", "/v1/me/profile", token, """{"nickname":"운전자"}""".toByteArray())
        assertEquals(saved.text, 200, saved.code)
        val me = HeartzJson.decodeFromString(ai.heartz.core.model.User.serializer(), saved.text)
        assertEquals("운전자", me.displayName)
        val relogin = call("POST", "/v1/auth/test-login", body = """{"userId":"${SeedData.DRIVER_JANG}"}""".toByteArray())
        assertEquals(me, HeartzJson.decodeFromString(LoginResponse.serializer(), relogin.text).user)
        assertEquals(400, call("PUT", "/v1/me/profile", token, """{"nickname":" "}""".toByteArray()).code)
        assertEquals(401, call("PUT", "/v1/me/profile", body = """{"nickname":"운전자"}""".toByteArray()).code)
        // 예전 앱이 보내던 avatar 필드는 무시한다
        assertEquals(200, call("PUT", "/v1/me/profile", token, """{"nickname":"운전자","avatar":2}""".toByteArray()).code)
    }

    @Test
    fun requestsWithoutTokenAreUnauthorized() {
        assertEquals(401, call("GET", "/v1/feed").code)
        assertEquals(401, call("GET", "/v1/feed", token = "forged").code)
    }

    @Test
    fun testUserListIsThreeDemoDriversAndModerator() {
        val r = call("GET", "/v1/test-users")
        val users = HeartzJson.decodeFromString(UserList.serializer(), r.text).users
        assertEquals(listOf("장클로드", "김피티", "상도동메시", "운영자 (테스트)"), users.map { it.displayName })
        assertEquals(setOf(SeedData.DRIVER_JANG, SeedData.DRIVER_KIM, SeedData.DRIVER_MESSI, SeedData.MODERATOR), users.map { it.id }.toSet())
    }

    @Test
    fun unknownUserCannotLogIn() {
        assertEquals(401, call("POST", "/v1/auth/test-login", body = """{"userId":"u-listener-a"}""".toByteArray()).code)
        assertEquals(401, call("POST", "/v1/auth/test-login", body = """{"userId":"nobody"}""".toByteArray()).code)
    }

    @Test
    fun feedShowsApprovedOnlyAndServesAudio() {
        val token = login(SeedData.DRIVER_JANG)
        val feed = HeartzJson.decodeFromString(StoryList.serializer(), call("GET", "/v1/feed", token).text).stories
        assertTrue(feed.isNotEmpty())
        assertTrue(feed.all { it.status == StoryStatus.APPROVED })
        val audio = call("GET", "/v1/stories/${feed.first().id}/audio", token)
        assertEquals(200, audio.code)
        assertEquals("audio/mp4", audio.contentType)
        assertArrayEquals(seedBytes, audio.body)
        assertEquals(404, call("GET", "/v1/stories/s-seed-ichon-pending/audio", token).code)
    }

    @Test
    fun fullCycleThroughHttp() {
        val a = login(SeedData.DRIVER_JANG)
        val b = login(SeedData.DRIVER_KIM)
        val mod = login(SeedData.MODERATOR)

        val req = CreateStoryRequest("client-1", "p-ichon", Mood.WARM, 12_000)
        val created = call("POST", "/v1/stories", a, HeartzJson.encodeToString(req).toByteArray())
        assertEquals(created.text, 200, created.code)
        val story = HeartzJson.decodeFromString(Story.serializer(), created.text)

        val up = call("PUT", "/v1/stories/${story.id}/audio", a, byteArrayOf(1, 2, 3), "audio/mp4")
        assertEquals(StoryStatus.DRAFT, HeartzJson.decodeFromString(UploadResult.serializer(), up.text).status)
        val sub = call("POST", "/v1/stories/${story.id}/submit", a)
        assertEquals(StoryStatus.PENDING_REVIEW, HeartzJson.decodeFromString(SubmitResult.serializer(), sub.text).status)

        assertEquals(404, call("GET", "/v1/stories/${story.id}/audio", b).code)
        val approveBody = HeartzJson.encodeToString(ModerationRequest(ModerationAction.APPROVE)).toByteArray()
        assertEquals(403, call("POST", "/v1/moderation/stories/${story.id}", b, approveBody).code)
        assertEquals(200, call("POST", "/v1/moderation/stories/${story.id}", mod, approveBody).code)

        val audio = call("GET", "/v1/stories/${story.id}/audio", b)
        assertEquals(200, audio.code)
        assertArrayEquals(byteArrayOf(1, 2, 3), audio.body)
    }

    @Test
    fun heartRouteSetsAndClearsHeartAndRepliesRouteIsGone() {
        // 시드 사연이 없는 운전자(자기 사연에는 하트를 남길 수 없다)
        val b = login(SeedData.DRIVER_JANG)
        val id = HeartzJson.decodeFromString(StoryList.serializer(), call("GET", "/v1/feed", b).text).stories.first().id
        val on = call("POST", "/v1/stories/$id/heart", b)
        assertEquals(on.text, 200, on.code)
        assertTrue(id in HeartzJson.decodeFromString(ListenStateResponse.serializer(), on.text).state.hearted)
        assertEquals(1, HeartzJson.decodeFromString(Story.serializer(), call("GET", "/v1/stories/$id", b).text).heartCount)
        val off = call("DELETE", "/v1/stories/$id/heart", b)
        assertTrue(id !in HeartzJson.decodeFromString(ListenStateResponse.serializer(), off.text).state.hearted)
        assertEquals(404, call("GET", "/v1/stories/$id/replies", b).code)
    }

    @Test
    fun malformedJsonIsBadRequestAndUnknownRouteIs404() {
        val a = login(SeedData.DRIVER_JANG)
        assertEquals(400, call("POST", "/v1/stories", a, "{nope".toByteArray()).code)
        assertEquals(404, call("GET", "/v1/nothing", a).code)
    }

    @Test
    fun oversizedUploadIsRejectedWith413() {
        val a = login(SeedData.DRIVER_JANG)
        val req = CreateStoryRequest("big", "p-ichon", Mood.CALM, 12_000)
        val story = HeartzJson.decodeFromString(
            Story.serializer(),
            call("POST", "/v1/stories", a, HeartzJson.encodeToString(req).toByteArray()).text,
        )
        val big = ByteArray((HeartzService.MAX_AUDIO_BYTES + 10).toInt())
        assertEquals(413, call("PUT", "/v1/stories/${story.id}/audio", a, big, "audio/mp4").code)
    }

    @Test
    fun assistIsAuthorOnlyAndHiddenFromOtherListeners() {
        val a = login(SeedData.DRIVER_JANG)
        val b = login(SeedData.DRIVER_KIM)
        val mod = login(SeedData.MODERATOR)
        val req = CreateStoryRequest("assist-1", "p-ichon", Mood.WARM, 12_000)
        val story = HeartzJson.decodeFromString(
            Story.serializer(),
            call("POST", "/v1/stories", a, HeartzJson.encodeToString(req).toByteArray()).text,
        )
        call("PUT", "/v1/stories/${story.id}/audio", a, byteArrayOf(1, 2, 3), "audio/mp4")
        call("POST", "/v1/stories/${story.id}/submit", a)
        val assist = ai.heartz.core.moderation.ReviewAssist(
            ai.heartz.core.moderation.Transcript(ai.heartz.core.moderation.TranscriptStatus.DONE, "안녕하세요", engine = "test"),
            emptyList(), 1,
        )
        val body = HeartzJson.encodeToString(ai.heartz.core.moderation.ReviewAssist.serializer(), assist).toByteArray()
        assertEquals(404, call("PUT", "/v1/stories/${story.id}/assist", b, body).code)
        val ok = call("PUT", "/v1/stories/${story.id}/assist", a, body)
        assertEquals(ok.text, 200, ok.code)
        val seen = HeartzJson.decodeFromString(Story.serializer(), call("GET", "/v1/stories/${story.id}", mod).text)
        assertEquals("안녕하세요", seen.assist?.transcript?.text)
        val approveBody = HeartzJson.encodeToString(ModerationRequest(ModerationAction.APPROVE)).toByteArray()
        call("POST", "/v1/moderation/stories/${story.id}", mod, approveBody)
        val public = HeartzJson.decodeFromString(Story.serializer(), call("GET", "/v1/stories/${story.id}", b).text)
        assertEquals(null, public.assist)
    }
}
