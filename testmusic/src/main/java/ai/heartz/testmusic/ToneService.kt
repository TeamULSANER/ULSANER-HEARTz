package ai.heartz.testmusic

import android.app.Activity
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import android.content.pm.ServiceInfo
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.media.MediaPlayer
import android.os.Bundle
import android.os.IBinder
import android.util.Log

/**
 * 시험 전용 음악 앱(제출물 아님). adb:
 *   am start --user 10 -n ai.heartz.testmusic/.StartActivity --es cmd play|stop
 * 음원: assets/song.ogg — "Canon in D Major", Kevin MacLeod(incompetech.com), 원곡 Johann Pachelbel,
 * CC BY 3.0 (https://creativecommons.org/licenses/by/3.0/), Wikimedia Commons에서 내려받음. 저장소에는 넣지 않는다(.gitignore).
 */
class StartActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val cmd = intent.getStringExtra("cmd") ?: "play"
        val i = Intent(this, ToneService::class.java).putExtra("cmd", cmd)
        if (cmd == "play") startForegroundService(i) else stopService(i)
        finish()
    }
}

/**
 * 일반 음악 앱처럼 포커스에 반응한다: GAIN 요청, LOSS_TRANSIENT → 일시정지, CAN_DUCK → 볼륨 20%,
 * GAIN → 재개·원래 볼륨, LOSS → 정지. 로그 태그 TestMusic.
 */
class ToneService : Service() {
    private lateinit var am: AudioManager
    private var player: MediaPlayer? = null
    private var focus: AudioFocusRequest? = null
    private var pausedByFocus = false
    private val attrs = AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_MEDIA).setContentType(AudioAttributes.CONTENT_TYPE_MUSIC).build()

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        am = getSystemService(AudioManager::class.java)
        val nm = getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(NotificationChannel("t", "test", NotificationManager.IMPORTANCE_LOW))
        val n = Notification.Builder(this, "t").setContentTitle("Test Music (Canon in D)").setSmallIcon(android.R.drawable.ic_media_play).build()
        startForeground(1, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.getStringExtra("cmd") == "play" && player == null) play()
        return START_NOT_STICKY
    }

    private fun play() {
        val req = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN)
            .setAudioAttributes(attrs)
            .setOnAudioFocusChangeListener { c ->
                Log.i(TAG, "focus change=$c")
                val p = player ?: return@setOnAudioFocusChangeListener
                when (c) {
                    AudioManager.AUDIOFOCUS_GAIN -> {
                        p.setVolume(1f, 1f)
                        if (pausedByFocus) { pausedByFocus = false; p.start() }
                        Log.i(TAG, "state=PLAYING vol=1.0")
                    }
                    AudioManager.AUDIOFOCUS_LOSS_TRANSIENT -> if (p.isPlaying) {
                        pausedByFocus = true; p.pause(); Log.i(TAG, "state=PAUSED (transient)")
                    }
                    AudioManager.AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK -> { p.setVolume(0.2f, 0.2f); Log.i(TAG, "state=DUCKED vol=0.2") }
                    AudioManager.AUDIOFOCUS_LOSS -> { Log.i(TAG, "state=STOPPED (permanent loss)"); stopSelf() }
                }
            }.build()
        Log.i(TAG, "focus request result=${am.requestAudioFocus(req)}")
        focus = req
        val p = MediaPlayer()
        assets.openFd("song.ogg").use { fd -> p.setDataSource(fd.fileDescriptor, fd.startOffset, fd.length) }
        p.setAudioAttributes(attrs)
        p.isLooping = true
        p.prepare()
        p.start()
        player = p
        Log.i(TAG, "state=PLAYING vol=1.0 (Canon in D)")
    }

    override fun onDestroy() {
        player?.release()
        player = null
        focus?.let { am.abandonAudioFocusRequest(it) }
        Log.i(TAG, "destroyed")
        super.onDestroy()
    }

    companion object { const val TAG = "TestMusic" }
}
