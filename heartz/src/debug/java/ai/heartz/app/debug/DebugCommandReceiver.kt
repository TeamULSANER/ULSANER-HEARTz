package ai.heartz.app.debug

import ai.heartz.app.HeartzApp
import ai.heartz.app.HeartzLog
import ai.heartz.app.platform.speech.PcmDecoder
import ai.heartz.core.moderation.Pcm16
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

/**
 * debug 빌드 전용 시험 입력(adb broadcast). 릴리스에는 병합되지 않는다.
 * 여기서 만든 호출은 "시험 입력"이며 사용자 경로의 증거가 아니다. 결과는 SDK 콜백 로그로 확인한다.
 *   am broadcast -n ai.heartz.app/.debug.DebugCommandReceiver -a ai.heartz.app.debug.TTS --es text "안녕하세요"
 *   am broadcast -n ai.heartz.app/.debug.DebugCommandReceiver -a ai.heartz.app.debug.STT_MIC
 *   am broadcast -n ai.heartz.app/.debug.DebugCommandReceiver -a ai.heartz.app.debug.STT_ASSET --es asset test-speech/ko-sample.m4a
 */
class DebugCommandReceiver : BroadcastReceiver() {
    /**
     * 시스템 미디어 앱과 같은 레거시 경로(android.media.browse.MediaBrowser)로 Heartz 트리를 읽어
     * 각 항목의 플래그와 extras(하위 카테고리 정보가 실제로 어떻게 전달되는지)를 기록한다.
     */
    /**
     * 써드파티 앱이 내비 지도 위에 UI를 그릴 수 있는지 시험한다.
     * 1) 오버레이 권한 상태 2) 권한 설정 화면 존재 여부 3) TYPE_APPLICATION_OVERLAY 창 추가 시도(지도 영역 좌표).
     */
    private suspend fun overlayProbe(context: Context, holdMs: Long, intentX: Int = 1000) {
        val can = android.provider.Settings.canDrawOverlays(context)
        HeartzLog.i("overlay", "Settings.canDrawOverlays=$can")
        val settingsIntent = Intent(
            android.provider.Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
            android.net.Uri.parse("package:${context.packageName}"),
        )
        val handlers = context.packageManager.queryIntentActivities(settingsIntent, 0).map { it.activityInfo.packageName + "/" + it.activityInfo.name }
        HeartzLog.i("overlay", "ACTION_MANAGE_OVERLAY_PERMISSION handlers=$handlers")
        val wm = context.getSystemService(android.view.WindowManager::class.java)
        val view = android.widget.TextView(context).apply {
            text = "Heartz overlay test"
            textSize = 28f
            setTextColor(android.graphics.Color.WHITE)
            setBackgroundColor(android.graphics.Color.argb(200, 220, 40, 60))
            setPadding(40, 40, 40, 40)
        }
        val lp = android.view.WindowManager.LayoutParams(
            700, 300,
            android.view.WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            android.view.WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or android.view.WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE,
            android.graphics.PixelFormat.TRANSLUCENT,
        ).apply {
            gravity = android.view.Gravity.TOP or android.view.Gravity.START
            x = intentX // 기본 내비 지도 영역(화면 가운데) 좌표
            y = 500
        }
        val added = runCatching { wm.addView(view, lp) }
        HeartzLog.i(
            "overlay",
            "addView(TYPE_APPLICATION_OVERLAY) result=" + (added.exceptionOrNull()?.let { "${it::class.java.simpleName}: ${it.message}" } ?: "added"),
        )
        if (added.isSuccess) {
            kotlinx.coroutines.delay(1_000)
            val loc = IntArray(2).also { view.getLocationOnScreen(it) }
            HeartzLog.i("overlay", "attached=${view.isAttachedToWindow} shown=${view.isShown} onScreen=${loc.toList()} size=${view.width}x${view.height}")
            kotlinx.coroutines.delay(holdMs)
            runCatching { wm.removeView(view) }
            HeartzLog.i("overlay", "removed")
        }
    }

    private fun browseDump(context: Context) {
        val holder = arrayOfNulls<android.media.browse.MediaBrowser>(1)
        fun bundle(b: android.os.Bundle?): String =
            b?.keySet()?.sorted()?.joinToString(", ") { k -> "$k=${b.get(k)}" } ?: "(none)"
        val cb = object : android.media.browse.MediaBrowser.ConnectionCallback() {
            override fun onConnected() {
                val b = holder[0]!!
                HeartzLog.i("browse", "connected root=${b.root} rootExtras={${bundle(b.extras)}}")
                fun dump(id: String, depth: Int) {
                    b.subscribe(id, object : android.media.browse.MediaBrowser.SubscriptionCallback() {
                        override fun onChildrenLoaded(parentId: String, children: MutableList<android.media.browse.MediaBrowser.MediaItem>) {
                            HeartzLog.i("browse", "children of $parentId count=${children.size}")
                            children.forEach { c ->
                                val d = c.description
                                HeartzLog.i(
                                    "browse",
                                    "${"  ".repeat(depth)}- ${c.mediaId} title=${d.title} sub=${d.subtitle} browsable=${c.isBrowsable} playable=${c.isPlayable} extras={${bundle(d.extras)}}",
                                )
                                if (c.isBrowsable && depth < 2) dump(c.mediaId!!, depth + 1)
                            }
                        }
                    })
                }
                dump(b.root, 1)
            }
            override fun onConnectionFailed() = HeartzLog.i("browse", "connection failed")
        }
        holder[0] = android.media.browse.MediaBrowser(
            context,
            android.content.ComponentName(context, ai.heartz.app.platform.audio.HeartzPlaybackService::class.java),
            cb, null,
        )
        holder[0]!!.connect()
    }

    /** 20 ms 프레임 RMS의 10·90 백분위(잡음 바닥·말소리)와 그 차이(dB) */
    private fun levels(pcm: ShortArray, rate: Int): String {
        val n = rate / 50
        val rms = (0 until pcm.size / n).map { f ->
            var e = 0.0
            for (i in f * n until (f + 1) * n) e += pcm[i].toDouble() * pcm[i]
            kotlin.math.sqrt(e / n)
        }.sorted()
        if (rms.isEmpty()) return "empty"
        val floor = rms[rms.size / 10]
        val speech = rms[rms.size * 9 / 10]
        val db = 20 * kotlin.math.log10(speech / floor.coerceAtLeast(1.0))
        return "floor=${floor.toInt()} speech=${speech.toInt()} gap=${"%.1f".format(db)}dB"
    }

    override fun onReceive(context: Context, intent: Intent) {
        val graph = HeartzApp.graph(context)
        // 브로드캐스트는 즉시 끝내고(ANR 방지) 작업은 앱 스코프에서 이어간다
        graph.appScope.launch {
            try {
                when (intent.action) {
                    "ai.heartz.app.debug.TTS" -> {
                        val r = graph.tts.speak(intent.getStringExtra("text") ?: "하츠 음성 안내 시험입니다.")
                        HeartzLog.i("debug", "TTS result=$r")
                    }
                    "ai.heartz.app.debug.STT_MIC" -> {
                        val r = graph.stt.listen(intent.getLongExtra("timeoutMs", 10_000))
                        HeartzLog.i("debug", "STT_MIC result=$r")
                    }
                    "ai.heartz.app.debug.STT_ASSET" -> {
                        val name = intent.getStringExtra("asset") ?: "test-speech/ko-sample.m4a"
                        val pcm = withContext(Dispatchers.IO) {
                            val f = File(context.cacheDir, "debug-${name.substringAfterLast('/')}")
                            context.assets.open(name).use { input -> f.outputStream().use { input.copyTo(it) } }
                            PcmDecoder.decodeTo16kMono(f)
                        }
                        HeartzLog.i("debug", "STT_ASSET decoded samples=${pcm.size} (~${pcm.size / 16}ms)")
                        val r = graph.stt.transcribePcm(Pcm16.toLittleEndianBytes(pcm))
                        HeartzLog.i("debug", "STT_ASSET result=$r")
                    }
                    "ai.heartz.app.debug.REC_TEST" -> {
                        // 앱 MediaRecorder로 N초 녹음 → 파일 디코딩 → STT(sendAudio). 마이크 입력이 실제로 들어오는지 확인용
                        val sec = intent.getIntExtra("sec", 6)
                        val f = File(context.cacheDir, "debug-rec.m4a")
                        graph.recorder.start(f, sec * 1000 + 500, intent.getIntExtra("source", 1)) {}.getOrThrow()
                        var peak = 0
                        repeat(sec * 10) { kotlinx.coroutines.delay(100); peak = maxOf(peak, graph.recorder.amplitude()) }
                        val rec = graph.recorder.stop()
                        HeartzLog.i("debug", "REC_TEST source=${intent.getIntExtra("source", 1)} record=$rec peakAmplitude=$peak")
                        if (rec is ai.heartz.app.platform.record.RecordResult.Success) {
                            val pcm = withContext(Dispatchers.IO) { PcmDecoder.decodeTo16kMono(rec.file) }
                            val r = graph.stt.transcribePcm(Pcm16.toLittleEndianBytes(pcm))
                            HeartzLog.i("debug", "REC_TEST stt=$r")
                        }
                    }
                    "ai.heartz.app.debug.FOCUS_TEST" -> {
                        // 포커스 종류별로 다른 앱(시험 음악 앱)이 어떻게 반응하는지 확인용. usage/gain은 AudioAttributes·AudioManager 상수 값
                        val am = context.getSystemService(android.media.AudioManager::class.java)
                        val usage = intent.getIntExtra("usage", android.media.AudioAttributes.USAGE_ASSISTANT)
                        val gain = intent.getIntExtra("gain", android.media.AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK)
                        val req = android.media.AudioFocusRequest.Builder(gain)
                            .setAudioAttributes(android.media.AudioAttributes.Builder().setUsage(usage).setContentType(android.media.AudioAttributes.CONTENT_TYPE_SPEECH).build())
                            .setOnAudioFocusChangeListener { c -> HeartzLog.i("debug", "FOCUS_TEST change=$c") }
                            .build()
                        val r = am.requestAudioFocus(req)
                        HeartzLog.i("debug", "FOCUS_TEST usage=$usage gain=$gain result=$r")
                        intent.getStringExtra("text")?.let { HeartzLog.i("debug", "FOCUS_TEST tts=${graph.tts.speak(it)}") }
                        kotlinx.coroutines.delay(intent.getLongExtra("holdMs", 4_000))
                        HeartzLog.i("debug", "FOCUS_TEST abandon=${am.abandonAudioFocusRequest(req)}")
                    }
                    "ai.heartz.app.debug.NAVI_ROUTE" -> {
                        // 내비 시험 경로(서빙고). cancel=true면 취소. 내비가 실제로 안내 음성을 내는지 확인용
                        if (intent.getBooleanExtra("cancel", false)) graph.navi.cancelTestRoute()
                        else graph.repository.places.value.firstOrNull { it.id == "p-seobinggo" }?.let { p ->
                            graph.navi.requestTestRoute(p.publicLocation.lat, p.publicLocation.lng, p.displayName)
                        }
                        HeartzLog.i("debug", "NAVI_ROUTE requested cancel=${intent.getBooleanExtra("cancel", false)}")
                    }
                    "ai.heartz.app.debug.CLEAR_LISTENS" -> {
                        // 받은·들은 편지 기록만 지운다(기기 안 데모 데이터). 주행 세션은 화면의 기술 진단 버튼에서 비운다
                        HeartzLog.i("debug", "CLEAR_LISTENS ok=${graph.repository.clearListenHistoryForTesting()}")
                    }
                    "ai.heartz.app.debug.READ" -> {
                        // 변조 목소리 읽기 경로(HeartzPlayer → TtsLetterReader → Pleos TTS) 시험. 시험 입력이며 실제 사연이 아니다
                        val text = intent.getStringExtra("text") ?: "오늘 퇴근길이 길었어요. 그래도 노을이 예뻤어요."
                        val watch = launch {
                            graph.player.state.collect { s ->
                                HeartzLog.i("debug", "READ state story=${s.storyId} playing=${s.isPlaying} ended=${s.ended} idle=${s.idle} pos=${s.positionMs}/${s.durationMs} err=${s.error} focus=${s.lastFocusEvent}")
                            }
                        }
                        graph.player.play(ai.heartz.app.platform.audio.PlayRequest("debug-read", null, "시험 사연", "debug", spokenText = text, durationMs = 8_000))
                        kotlinx.coroutines.delay(intent.getLongExtra("watchMs", 20_000))
                        watch.cancel()
                    }
                    "ai.heartz.app.debug.CLEAN_TEST" -> {
                        // 잡음 줄이기 시험: 시험 음성 + 합성 잡음 → AAC → VoiceCleanup → 다시 디코딩해 잡음 바닥·말소리 크기를 비교한다
                        val name = intent.getStringExtra("asset") ?: "test-speech/ko-sample.m4a"
                        val noise = intent.getIntExtra("noise", 1_500)
                        withContext(Dispatchers.Default) {
                            val src = File(context.cacheDir, "clean-src.m4a")
                            context.assets.open(name).use { input -> src.outputStream().use { input.copyTo(it) } }
                            val (pcm, rate) = PcmDecoder.decodeMono(src)
                            val rnd = java.util.Random(1)
                            val noisy = ShortArray(pcm.size) { i -> (pcm[i] + rnd.nextGaussian() * noise).toInt().coerceIn(-32768, 32767).toShort() }
                            val f = File(context.cacheDir, "clean-test.m4a")
                            ai.heartz.app.platform.record.VoiceCleanup.encodeAac(noisy, rate, f)
                            val before = PcmDecoder.decodeMono(f).first
                            val bytesBefore = f.length()
                            val ok = ai.heartz.app.platform.record.VoiceCleanup.cleanInPlace(f)
                            val after = PcmDecoder.decodeMono(f).first
                            HeartzLog.i("debug", "CLEAN_TEST rate=$rate noise=$noise ok=$ok bytes=$bytesBefore->${f.length()} samples=${before.size}->${after.size} before=${levels(before, rate)} after=${levels(after, rate)}")
                        }
                    }
                    "ai.heartz.app.debug.BROWSE_DUMP" -> browseDump(context.applicationContext)
                    "ai.heartz.app.debug.OVERLAY_PROBE" -> overlayProbe(context.applicationContext, intent.getLongExtra("holdMs", 8_000), intent.getIntExtra("x", 1000))
                    else -> HeartzLog.i("debug", "unknown action ${intent.action}")
                }
            } catch (e: Exception) {
                HeartzLog.i("debug", "command failed ${e::class.java.simpleName} ${e.message}")
            }
        }
    }
}
