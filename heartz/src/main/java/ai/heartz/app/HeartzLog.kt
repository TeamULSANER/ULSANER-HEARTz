package ai.heartz.app

import android.util.Log
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import java.util.Locale

/**
 * 진단 로그. logcat 태그 "Heartz"와 앱 내 진단 화면에 함께 남긴다.
 * 토큰·음성·원본 좌표는 남기지 않는다. 좌표는 debug 빌드에서만 소수 4자리(약 11 m)로 기록한다.
 */
object HeartzLog {
    const val TAG = "Heartz"

    data class Entry(val atMillis: Long, val area: String, val message: String)

    private val _entries = MutableStateFlow<List<Entry>>(emptyList())
    val entries: StateFlow<List<Entry>> = _entries.asStateFlow()

    fun i(area: String, message: String) {
        Log.i(TAG, "[$area] $message")
        val e = Entry(System.currentTimeMillis(), area, message)
        _entries.update { (it + e).takeLast(300) }
    }

    fun coord(lat: Double, lng: Double): String =
        if (BuildConfig.DEBUG) String.format(Locale.US, "%.4f,%.4f", lat, lng) else "(redacted)"
}
