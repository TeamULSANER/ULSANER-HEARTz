package ai.heartz.app

import ai.heartz.app.platform.gleo.GleoFunction
import ai.heartz.app.platform.gleo.GleoProtocol
import ai.heartz.app.platform.gleo.GleoStatus
import ai.heartz.app.platform.gleo.LetterChoice
import ai.heartz.app.platform.gleo.StoryDecision
import ai.heartz.core.model.VoiceMode
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/** Gleo 앱 액션 계약(가이드 문서 기준)의 앱 측 파싱·응답 형식 테스트. 실제 Gleo 호출 증거가 아니다. */
class GleoProtocolTest {
    @Test
    fun actionNameMapsWithOrWithoutPackagePrefix() {
        assertEquals(GleoFunction.START_STORY_RECORDING, GleoProtocol.functionFor("START_STORY_RECORDING"))
        assertEquals(GleoFunction.MANAGE_MY_STORY, GleoProtocol.functionFor("ai.heartz.app.MANAGE_MY_STORY"))
        assertEquals(GleoFunction.HANDLE_ARRIVED_LETTER, GleoProtocol.functionFor("HANDLE_ARRIVED_LETTER"))
        assertNull(GleoProtocol.functionFor("android.intent.action.MAIN"))
        assertNull(GleoProtocol.functionFor(null))
    }

    @Test
    fun parametersComeFromActionParamJsonOrPlainExtras() {
        assertEquals("DISCARD", GleoProtocol.params("""{"decision":"DISCARD"}""", emptyMap())["decision"])
        assertEquals("SKIP", GleoProtocol.params(null, mapOf("choice" to "SKIP"))["choice"])
        assertEquals(emptyMap<String, String>(), GleoProtocol.params("{not json", emptyMap()))
    }

    @Test
    fun storyDecisionIsReadAndUnknownIsNull() {
        assertEquals(StoryDecision.DISCARD, GleoProtocol.storyDecision(mapOf("decision" to "discard")))
        assertEquals(StoryDecision.SUBMIT, GleoProtocol.storyDecision(mapOf("decision" to "SUBMIT")))
        assertEquals(StoryDecision.RERECORD, GleoProtocol.storyDecision(mapOf("decision" to "rerecord")))
        assertNull(GleoProtocol.storyDecision(mapOf("decision" to "KEEP_PRIVATE")))
        assertEquals(StoryDecision.FINISH, GleoProtocol.storyDecision(emptyMap()))
        assertNull(GleoProtocol.storyDecision(mapOf("decision" to "PUBLISH_NOW")))
    }

    @Test
    fun voiceIsOptionalAndUnknownIsIgnored() {
        assertEquals(VoiceMode.MODULATED, GleoProtocol.voiceMode(mapOf("voice" to "modulated")))
        assertEquals(VoiceMode.ORIGINAL, GleoProtocol.voiceMode(mapOf("voice" to "ORIGINAL")))
        assertNull(GleoProtocol.voiceMode(emptyMap()))
        assertNull(GleoProtocol.voiceMode(mapOf("voice" to "ROBOT")))
    }

    @Test
    fun letterChoiceDefaultsToPlay() {
        assertEquals(LetterChoice.PLAY, GleoProtocol.letterChoice(emptyMap()))
        assertEquals(LetterChoice.SEND_HEART, GleoProtocol.letterChoice(mapOf("choice" to "SEND_HEART")))
        assertNull(GleoProtocol.letterChoice(mapOf("choice" to "LIKE")))
    }

    @Test
    fun responseHasRequiredFields() {
        val json = Json.parseToJsonElement(GleoProtocol.response(GleoFunction.START_STORY_RECORDING, GleoStatus.SUCCESS, "Recording started.")).jsonObject
        assertEquals("START_STORY_RECORDING", json["functionName"]!!.jsonPrimitive.content)
        assertEquals("success", json["status"]!!.jsonPrimitive.content)
        assertEquals("Recording started.", json["message"]!!.jsonPrimitive.content)
    }

    /** assets/schema.json이 코드의 함수·파라미터 값과 같고 가이드 제한(함수 3개 이하)을 지키는지 */
    @Test
    fun schemaMatchesCode() {
        val f = listOf(File("src/main/assets/schema.json"), File("heartz/src/main/assets/schema.json")).first { it.isFile }
        val schema = Json.parseToJsonElement(f.readText()).jsonObject
        val fns = schema["actionCalls"]!!.jsonArray.map { it.jsonObject["function"]!!.jsonObject }
        assertTrue(fns.size <= 3)
        assertEquals(GleoFunction.entries.map { it.name }.toSet(), fns.map { it.jsonObject["name"]!!.jsonPrimitive.content }.toSet())
        fun enumOf(fn: String, param: String) = fns.single { it["name"]!!.jsonPrimitive.content == fn }["parameters"]!!.jsonObject["properties"]!!
            .jsonObject[param]!!.jsonObject["enum"]!!.jsonArray.map { it.jsonPrimitive.content }.toSet()
        assertEquals(StoryDecision.entries.map { it.name }.toSet(), enumOf("MANAGE_MY_STORY", "decision"))
        assertEquals(VoiceMode.entries.map { it.name }.toSet(), enumOf("MANAGE_MY_STORY", "voice"))
        // 사용자에게 들리는 설명에는 "편지(letter)"가 아니라 "사연(story)"을 쓴다(함수 이름은 등록 호환 때문에 유지)
        assertTrue(fns.none { it["description"]!!.jsonPrimitive.content.contains("letter", ignoreCase = true) })
        assertEquals(LetterChoice.entries.map { it.name }.toSet(), enumOf("HANDLE_ARRIVED_LETTER", "choice"))
    }
}
