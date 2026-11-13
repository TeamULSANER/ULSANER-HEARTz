package ai.heartz.app.platform.audio

import ai.heartz.app.HeartzApp
import ai.heartz.app.HeartzLog
import ai.heartz.app.MainActivity
import android.app.PendingIntent
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.session.LibraryResult
import androidx.media3.session.MediaLibraryService
import androidx.media3.session.MediaSession
import com.google.common.collect.ImmutableList
import com.google.common.util.concurrent.Futures
import com.google.common.util.concurrent.ListenableFuture
import com.google.common.util.concurrent.SettableFuture
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

/**
 * Pleos Music/Audio 가이드: MediaSession/MediaLibraryService로 재생 상태와 제어를 제공한다.
 * - 오디오 포커스: 편지는 1분 안팎의 짧은 음성이라 [LetterFocus]가 재생할 때만 AUDIOFOCUS_GAIN_TRANSIENT를 요청한다.
 *   듣던 음악은 잠시 멈췄다가 편지가 끝나면(포커스 반납) 다시 나온다. 카드 표시만으로는 요청하지 않는다.
 * - 영구 포커스 상실: 재생을 stop하여 세션을 활성 상태로 두지 않는다.
 * - 자동 재생·재생 재개(resumption)를 하지 않는다.
 */
class HeartzPlaybackService : MediaLibraryService() {
    private var session: MediaLibrarySession? = null
    private var letterFocus: LetterFocus? = null
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    override fun onCreate() {
        super.onCreate()
        val player = ExoPlayer.Builder(this)
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(C.USAGE_MEDIA)
                    .setContentType(C.AUDIO_CONTENT_TYPE_SPEECH)
                    .build(),
                /* handleAudioFocus = */ false,
            )
            .setHandleAudioBecomingNoisy(true)
            .build()
        player.addListener(StateLogger(player))
        letterFocus = LetterFocus(this, HeartzApp.graph(this).focusLeases, player) { extras -> session?.setSessionExtras(extras) }
            .also(player::addListener)
        val activity = PendingIntent.getActivity(
            this, 0, Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        session = MediaLibrarySession.Builder(this, player, LibraryCallback())
            .setSessionActivity(activity)
            .build()
        HeartzLog.i("playback", "service onCreate")
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaLibrarySession? = session

    override fun onTaskRemoved(rootIntent: Intent?) {
        // Pleos 가이드 순서: 정지 → stopSelf → super.onTaskRemoved
        session?.player?.let { it.stop(); it.clearMediaItems() }
        stopSelf()
        super.onTaskRemoved(rootIntent)
    }

    override fun onDestroy() {
        scope.cancel()
        letterFocus?.release()
        letterFocus = null
        session?.run {
            player.release()
            release()
        }
        session = null
        HeartzLog.i("playback", "service onDestroy")
        super.onDestroy()
    }

    private class StateLogger(private val player: Player) : Player.Listener {
        private val mainHandler = android.os.Handler(android.os.Looper.getMainLooper())

        override fun onIsPlayingChanged(isPlaying: Boolean) {
            HeartzLog.i("playback", "isPlaying=$isPlaying state=${player.playbackState}")
        }

        /** 끝까지 재생하면 컨트롤러가 ENDED를 먼저 받도록 한 번 넘긴 뒤 stop()으로 IDLE 전환한다(포커스 반납). */
        override fun onPlaybackStateChanged(playbackState: Int) {
            if (playbackState == Player.STATE_ENDED) {
                HeartzLog.i("playback", "ended -> stop()")
                mainHandler.post { if (player.playbackState == Player.STATE_ENDED) player.stop() }
            }
        }
    }

    /**
     * 시스템 미디어 브라우저용 트리(Pleos Music/Audio 가이드 "카테고리 세분화": 하위 카테고리 = 팟캐스트).
     * Heartz의 편지는 장소별로 회차가 쌓이는 짧은 음성 콘텐츠라 팟캐스트로 분류한다.
     *   루트(MEDIA_TYPE_FOLDER_PODCASTS)
     *    ├ 지금 이 길의 편지 (MEDIA_TYPE_PODCAST) → 회차(MEDIA_TYPE_PODCAST_EPISODE)
     *    └ 오늘 지나친 편지 (MEDIA_TYPE_PODCAST) → 회차
     * 하위 카테고리를 시스템 라이브러리에 전달하는 키는 Pleos 문서에 명시되어 있지 않다.
     * 표준 Media3 mediaType(레거시 브라우저에는 extras로 변환됨)과 [SUBCATEGORY] 루트 extra로 전달한다.
     */
    private inner class LibraryCallback : MediaLibrarySession.Callback {
        private fun folder(id: String, title: String, type: Int, subtitle: String? = null) = MediaItem.Builder()
            .setMediaId(id)
            .setMediaMetadata(
                MediaMetadata.Builder()
                    .setTitle(title)
                    .setSubtitle(subtitle)
                    .setIsBrowsable(true)
                    .setIsPlayable(false)
                    .setMediaType(type)
                    .setGenre(GENRE)
                    .build(),
            )
            .build()

        @androidx.annotation.OptIn(markerClass = [androidx.media3.common.util.UnstableApi::class])
        override fun onGetLibraryRoot(
            session: MediaLibrarySession,
            browser: MediaSession.ControllerInfo,
            params: LibraryParams?,
        ): ListenableFuture<LibraryResult<MediaItem>> {
            HeartzLog.i("playback", "library root requested by ${browser.packageName}")
            val root = folder(ROOT_ID, "HEARTz 음성 사연", MediaMetadata.MEDIA_TYPE_FOLDER_PODCASTS)
            val rootParams = LibraryParams.Builder()
                .setExtras(
                    Bundle().apply {
                        putString(EXTRA_SUBCATEGORY, SUBCATEGORY)
                        // 목록형 표시 힌트(AAOS 미디어 표준 키)
                        putInt("android.media.browse.CONTENT_STYLE_BROWSABLE_HINT", 1)
                        putInt("android.media.browse.CONTENT_STYLE_PLAYABLE_HINT", 1)
                    },
                )
                .build()
            return Futures.immediateFuture(LibraryResult.ofItem(root, rootParams))
        }

        override fun onGetChildren(
            session: MediaLibrarySession,
            browser: MediaSession.ControllerInfo,
            parentId: String,
            page: Int,
            pageSize: Int,
            params: LibraryParams?,
        ): ListenableFuture<LibraryResult<ImmutableList<MediaItem>>> {
            val graph = HeartzApp.graph(this@HeartzPlaybackService)
            val items: List<MediaItem> = when (parentId) {
                ROOT_ID -> listOf(
                    folder(NOW_ID, "지금 이 길의 사연", MediaMetadata.MEDIA_TYPE_PODCAST, "지나는 곳에 도착한 사연"),
                    folder(PASSED_ID, "오늘 지나친 사연", MediaMetadata.MEDIA_TYPE_PODCAST, "카드로 보였지만 듣지 않은 사연"),
                )
                NOW_ID -> graph.browsableLetters().filter { it.first.id == graph.currentCardStoryId.value }.map(::episode)
                PASSED_ID -> graph.browsableLetters().filter { it.first.id != graph.currentCardStoryId.value }.map(::episode)
                else -> emptyList()
            }
            return Futures.immediateFuture(LibraryResult.ofItemList(ImmutableList.copyOf(items), params))
        }

        private fun episode(t: Triple<ai.heartz.core.model.Story, String, String>): MediaItem {
            val (story, title, subtitle) = t
            val place = HeartzApp.graph(this@HeartzPlaybackService).repository.places.value.firstOrNull { it.id == story.placeId }
            return MediaItem.Builder()
                .setMediaId(story.id)
                .setMediaMetadata(
                    MediaMetadata.Builder()
                        .setTitle(title)
                        .setArtist(subtitle)
                        .setAlbumTitle(place?.displayName)
                        .setGenre(GENRE)
                        .setDurationMs(story.durationMs)
                        .setIsPlayable(true)
                        .setIsBrowsable(false)
                        .setMediaType(MediaMetadata.MEDIA_TYPE_PODCAST_EPISODE)
                        .setExtras(Bundle().apply { putString(EXTRA_STORY_ID, story.id) })
                        .build(),
                )
                .build()
        }

        /** 앱 컨트롤러는 URI를 채워 보낸다. 시스템 브라우저가 mediaId만 보내면 음원을 받아 URI를 채운다. */
        override fun onAddMediaItems(
            mediaSession: MediaSession,
            controller: MediaSession.ControllerInfo,
            mediaItems: MutableList<MediaItem>,
        ): ListenableFuture<MutableList<MediaItem>> {
            if (mediaItems.all { it.localConfiguration != null }) return Futures.immediateFuture(mediaItems)
            val result = SettableFuture.create<MutableList<MediaItem>>()
            scope.launch {
                try {
                    val graph = HeartzApp.graph(this@HeartzPlaybackService)
                    val resolved = mediaItems.map { item ->
                        if (item.localConfiguration != null) return@map item
                        val story = graph.repository.feed.value.firstOrNull { it.id == item.mediaId }
                            ?: throw IllegalStateException("unknown story")
                        val file = graph.repository.audioFile(story)
                        item.buildUpon().setUri(Uri.fromFile(file)).build()
                    }.toMutableList()
                    HeartzLog.i("playback", "system browser request resolved items=${resolved.size}")
                    result.set(resolved)
                } catch (e: Exception) {
                    result.setException(e)
                }
            }
            return result
        }

        @androidx.annotation.OptIn(markerClass = [androidx.media3.common.util.UnstableApi::class])
        override fun onPlaybackResumption(
            mediaSession: MediaSession,
            controller: MediaSession.ControllerInfo,
        ): ListenableFuture<MediaSession.MediaItemsWithStartPosition> =
            Futures.immediateFailedFuture(UnsupportedOperationException("Heartz does not auto-resume"))
    }

    companion object {
        const val ROOT_ID = "heartz-root"
        const val NOW_ID = "heartz-now"
        const val PASSED_ID = "heartz-passed"
        const val GENRE = "음성 사연"
        /** Pleos 가이드의 하위 카테고리 값(음악/팟캐스트/오디오북/라디오 중 팟캐스트) */
        const val SUBCATEGORY = "PODCAST"
        const val EXTRA_SUBCATEGORY = "ai.heartz.app.MEDIA_SUBCATEGORY"
    }
}
