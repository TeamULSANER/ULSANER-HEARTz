package ai.heartz.app.platform.gleo

import ai.heartz.core.model.VoiceMode
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.put

/**
 * Gleo Interface(앱 액션) 계약. 근거: Pleos app-integration-guide(app-capability·action_callback·examples).
 * - 함수는 최대 3개(가이드 제한). 이름은 assets/schema.json과 같다.
 * - 응답 message는 영어(가이드 요구). Gleo가 이 내용으로 대답을 만든다.
 * - 7초 안에 응답해야 한다(가이드: 7초 뒤 취소).
 */
enum class GleoFunction { START_STORY_RECORDING, MANAGE_MY_STORY, HANDLE_ARRIVED_LETTER }

/** MANAGE_MY_STORY의 decision(나만 보기는 없음: 보내면 검수 후 공개) */
enum class StoryDecision { FINISH, SUBMIT, RERECORD, DISCARD }

/** HANDLE_ARRIVED_LETTER의 choice. 함수 이름은 실제 Gleo 등록 호환을 위해 그대로 두고 설명만 "story"로 쓴다 */
enum class LetterChoice { PLAY, SKIP, SEND_HEART, NO_HEART }

enum class GleoStatus(val wire: String) { SUCCESS("success"), ASKING("asking"), FAIL("fail") }

object GleoProtocol {
    const val RESPONSE_ACTION = "ai.pleos.playground.intent.action.APP_ACTION_RESPONSE"
    const val EXTRA_ACTION_PARAM = "ACTION_PARAM"
    const val EXTRA_PACKAGE = "packageName"
    const val EXTRA_RESPONSE = "response"
    const val TIMEOUT_MS = 7_000L

    fun functionFor(action: String?): GleoFunction? {
        val name = action?.substringAfterLast('.') ?: return null
        return GleoFunction.entries.firstOrNull { it.name == name }
    }

    /** 가이드 예제는 ACTION_PARAM(JSON 문자열), 설명은 개별 extra라 둘 다 읽는다. 잘못된 JSON은 빈 값. */
    fun params(actionParamJson: String?, plainExtras: Map<String, String>): Map<String, String> {
        if (actionParamJson.isNullOrBlank()) return plainExtras
        return runCatching {
            Json.parseToJsonElement(actionParamJson).jsonObject.mapValues { (_, v) ->
                (v as? JsonPrimitive)?.content ?: v.toString()
            }
        }.getOrDefault(emptyMap())
    }

    /** 값이 없으면 FINISH(녹음 끝내기), 모르는 값이면 null(아무것도 하지 않음) */
    fun storyDecision(params: Map<String, String>): StoryDecision? {
        val v = params["decision"]?.trim()?.uppercase() ?: return StoryDecision.FINISH
        return StoryDecision.entries.firstOrNull { it.name == v }
    }

    /** MANAGE_MY_STORY의 voice(선택). 없거나 모르는 값이면 null(앱이 묻는다) */
    fun voiceMode(params: Map<String, String>): VoiceMode? {
        val v = params["voice"]?.trim()?.uppercase() ?: return null
        return VoiceMode.entries.firstOrNull { it.name == v }
    }

    /** 값이 없으면 PLAY, 모르는 값이면 null */
    fun letterChoice(params: Map<String, String>): LetterChoice? {
        val v = params["choice"]?.trim()?.uppercase() ?: return LetterChoice.PLAY
        return LetterChoice.entries.firstOrNull { it.name == v }
    }

    fun response(fn: GleoFunction, status: GleoStatus, message: String): String {
        val o: JsonObject = buildJsonObject {
            put("functionName", fn.name)
            put("status", status.wire)
            put("message", message)
        }
        return o.toString()
    }
}
