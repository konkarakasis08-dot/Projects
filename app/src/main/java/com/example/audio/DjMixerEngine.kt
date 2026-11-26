package com.example.audio

import android.content.Context
import android.media.MediaPlayer
import android.net.Uri
import android.os.Build
import android.util.Log
import com.example.data.model.CrossfadeCurve
import com.example.data.model.DeckId
import com.example.data.model.DjPlaylist
import com.example.data.model.DjTrack
import com.example.data.model.TrackSourceType
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

data class DeckState(
    val deckId: DeckId,
    val track: DjTrack? = null,
    val isPlaying: Boolean = false,
    val isBuffering: Boolean = false,
    val isCuedReady: Boolean = false,
    val positionSec: Int = 0,
    val durationSec: Int = 0,
    val pitchRate: Float = 1.0f,
    val deckGain: Float = 1.0f,
    val effectiveBpm: Int = 120,
    val errorMessage: String? = null
)

data class MixerUiState(
    val activePlaylistId: Long = 0,
    val activePlaylistName: String = "MixPulse Booth",
    val queue: List<DjTrack> = emptyList(),
    val currentQueueIndex: Int = 0,
    val activeDeck: DeckId = DeckId.A,
    val deckA: DeckState = DeckState(DeckId.A),
    val deckB: DeckState = DeckState(DeckId.B),
    val crossfader: Float = 0f, // 0f = 100% Deck A, 1f = 100% Deck B
    val masterVolume: Float = 0.92f,
    val autoMixEnabled: Boolean = true,
    val crossfadeDurationSec: Int = 8,
    val crossfadeCurve: CrossfadeCurve = CrossfadeCurve.EQUAL_POWER,
    val isCrossfading: Boolean = false,
    val crossfadeProgress: Float = 0f,
    val statusMessage: String? = null
)

data class EmbedFailureEvent(
    val deckId: DeckId,
    val track: DjTrack,
    val errorCode: Int
)

class DjMixerEngine(context: Context) : YouTubeDeckListener {

    private val appContext = context.applicationContext
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    private val _state = MutableStateFlow(MixerUiState())
    val state: StateFlow<MixerUiState> = _state.asStateFlow()

    private val _embedFailureEvents = MutableSharedFlow<EmbedFailureEvent>(extraBufferCapacity = 8)
    val embedFailureEvents: SharedFlow<EmbedFailureEvent> = _embedFailureEvents.asSharedFlow()

    private var _deckAYouTube: YouTubeDeckBridge? = null
    private var _deckBYouTube: YouTubeDeckBridge? = null

    fun getOrCreateYouTubeBridge(deckId: DeckId): YouTubeDeckBridge {
        return if (deckId == DeckId.A) {
            _deckAYouTube ?: YouTubeDeckBridge(appContext, DeckId.A, this).also { _deckAYouTube = it }
        } else {
            _deckBYouTube ?: YouTubeDeckBridge(appContext, DeckId.B, this).also { _deckBYouTube = it }
        }
    }

    private var mediaPlayerA: MediaPlayer? = null
    private var mediaPlayerB: MediaPlayer? = null

    private var crossfadeJob: Job? = null
    private var monitorJob: Job? = null

    init {
        startMonitorLoop()
    }

    private fun startMonitorLoop() {
        monitorJob?.cancel()
        monitorJob = scope.launch {
            while (isActive) {
                updateLocalPositions()
                checkAutoMixTrigger()
                delay(180L)
            }
        }
    }

    fun loadPlaylist(playlist: DjPlaylist, autoStart: Boolean = false) {
        if (playlist.tracks.isEmpty()) return
        crossfadeJob?.cancel()

        val firstTrack = playlist.tracks.first()
        val secondTrack = playlist.tracks.getOrNull(1)

        _state.update {
            it.copy(
                activePlaylistId = playlist.id,
                activePlaylistName = playlist.name,
                queue = playlist.tracks,
                currentQueueIndex = 0,
                activeDeck = DeckId.A,
                crossfader = 0f,
                isCrossfading = false,
                crossfadeProgress = 0f,
                crossfadeDurationSec = playlist.crossfadeSeconds.coerceIn(3, 16),
                crossfadeCurve = playlist.crossfadeCurve,
                statusMessage = null
            )
        }

        loadTrackIntoDeck(DeckId.A, firstTrack, autoPlay = autoStart)
        if (secondTrack != null && _state.value.autoMixEnabled) {
            loadTrackIntoDeck(DeckId.B, secondTrack, autoPlay = false)
        } else {
            clearDeck(DeckId.B)
        }
    }

    fun updateQueue(tracks: List<DjTrack>) {
        val current = _state.value
        if (tracks == current.queue) return
        val safeIdx = current.currentQueueIndex.coerceIn(0, (tracks.size - 1).coerceAtLeast(0))
        _state.update {
            it.copy(
                queue = tracks,
                currentQueueIndex = safeIdx
            )
        }
        preCueNextTrackIfNeeded()
    }

    fun loadSingleTrackToDeck(deckId: DeckId, track: DjTrack, autoPlay: Boolean = true) {
        crossfadeJob?.cancel()
        _state.update {
            val newCrossfader = if (deckId == DeckId.A) 0f else 1f
            it.copy(
                activeDeck = deckId,
                crossfader = newCrossfader,
                isCrossfading = false,
                statusMessage = null
            )
        }
        loadTrackIntoDeck(deckId, track, autoPlay = autoPlay)
    }

    fun jumpToQueueTrack(index: Int) {
        val queue = _state.value.queue
        if (index !in queue.indices) return
        val targetTrack = queue[index]
        val incomingDeck = if (_state.value.activeDeck == DeckId.A) DeckId.B else DeckId.A

        _state.update { it.copy(currentQueueIndex = index) }
        loadTrackIntoDeck(incomingDeck, targetTrack, autoPlay = false)
        triggerCrossfadeTo(incomingDeck, advanceQueueIndex = false)
    }

    fun togglePlayPause(deckId: DeckId) {
        val deck = deckState(deckId)
        if (deck.track == null) return
        val track = deck.track

        if (deck.isPlaying) {
            pauseDeck(deckId)
        } else {
            when (track.sourceType) {
                TrackSourceType.YOUTUBE -> getOrCreateYouTubeBridge(deckId).play()
                TrackSourceType.LOCAL_AUDIO, TrackSourceType.BUILT_IN_GROOVE -> {
                    val mp = mediaPlayer(deckId)
                    if (mp != null) {
                        runCatching { mp.start() }
                        updateDeck(deckId) { it.copy(isPlaying = true) }
                    } else {
                        loadTrackIntoDeck(deckId, track, autoPlay = true)
                    }
                }
            }
            applyAllDeckVolumes()
        }
    }

    fun cueRestart(deckId: DeckId) {
        seekDeck(deckId, 0)
    }

    fun seekDeck(deckId: DeckId, seconds: Int) {
        val deck = deckState(deckId)
        val track = deck.track ?: return
        val clamped = seconds.coerceIn(0, (deck.durationSec).coerceAtLeast(1))
        when (track.sourceType) {
            TrackSourceType.YOUTUBE -> getOrCreateYouTubeBridge(deckId).seekTo(clamped)
            TrackSourceType.LOCAL_AUDIO, TrackSourceType.BUILT_IN_GROOVE -> {
                runCatching { mediaPlayer(deckId)?.seekTo(clamped * 1000) }
            }
        }
        updateDeck(deckId) { it.copy(positionSec = clamped) }
    }

    fun setDeckPitch(deckId: DeckId, rate: Float) {
        val clamped = rate.coerceIn(0.80f, 1.25f)
        val deck = deckState(deckId)
        val baseBpm = deck.track?.bpm ?: 120
        val effectiveBpm = (baseBpm * clamped).toInt()
        updateDeck(deckId) { it.copy(pitchRate = clamped, effectiveBpm = effectiveBpm) }

        when (deck.track?.sourceType) {
            TrackSourceType.YOUTUBE -> getOrCreateYouTubeBridge(deckId).setPlaybackRate(clamped)
            TrackSourceType.LOCAL_AUDIO, TrackSourceType.BUILT_IN_GROOVE -> {
                mediaPlayer(deckId)?.let { mp ->
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                        runCatching {
                            val wasPlaying = mp.isPlaying
                            mp.playbackParams = mp.playbackParams.setSpeed(clamped)
                            if (!wasPlaying) mp.pause()
                        }
                    }
                }
            }
            null -> Unit
        }
    }

    fun syncIncomingBpmToActive() {
        val s = _state.value
        val active = deckState(s.activeDeck)
        val standbyId = if (s.activeDeck == DeckId.A) DeckId.B else DeckId.A
        val standby = deckState(standbyId)
        val standbyTrack = standby.track ?: return
        val targetBpm = active.effectiveBpm.toFloat()
        val ratio = (targetBpm / standbyTrack.bpm.coerceAtLeast(60)).coerceIn(0.85f, 1.20f)
        setDeckPitch(standbyId, ratio)
        _state.update {
            it.copy(statusMessage = "BPM συγχρονισμός: ${standbyTrack.title.take(22)} → ${targetBpm.toInt()} BPM")
        }
    }

    fun setCrossfader(value: Float) {
        crossfadeJob?.cancel()
        val clamped = value.coerceIn(0f, 1f)
        _state.update {
            it.copy(
                crossfader = clamped,
                isCrossfading = false,
                activeDeck = if (clamped < 0.5f) DeckId.A else DeckId.B
            )
        }
        applyAllDeckVolumes()
    }

    fun setAutoMixEnabled(enabled: Boolean) {
        _state.update { it.copy(autoMixEnabled = enabled) }
        if (enabled) {
            preCueNextTrackIfNeeded()
        }
    }

    fun setCrossfadeDuration(seconds: Int) {
        _state.update { it.copy(crossfadeDurationSec = seconds.coerceIn(3, 16)) }
    }

    fun setCrossfadeCurve(curve: CrossfadeCurve) {
        _state.update { it.copy(crossfadeCurve = curve) }
        applyAllDeckVolumes()
    }

    fun setMasterVolume(volume: Float) {
        _state.update { it.copy(masterVolume = volume.coerceIn(0f, 1f)) }
        applyAllDeckVolumes()
    }

    fun clearStatusMessage() {
        _state.update { it.copy(statusMessage = null) }
    }

    fun showStatusMessage(message: String) {
        _state.update { it.copy(statusMessage = message) }
    }

    fun markEmbedUnrecoverable(deckId: DeckId, message: String) {
        updateDeck(deckId) { it.copy(isBuffering = false, isPlaying = false, errorMessage = message) }
        _state.update { it.copy(statusMessage = message) }
    }

    fun cancelBlockedTrackAndAdvance(deckId: DeckId, blockedTrack: DjTrack, message: String) {
        val currentQueue = _state.value.queue
        val updatedQueue = currentQueue.filterNot {
            it.sourceUriOrId == blockedTrack.sourceUriOrId &&
                (blockedTrack.id == 0L || it.id == blockedTrack.id)
        }
        val wasActive = _state.value.activeDeck == deckId
        clearDeck(deckId)
        _state.update {
            val nextIndex = if (updatedQueue.isEmpty()) 0
            else it.currentQueueIndex.coerceIn(0, updatedQueue.lastIndex)
            it.copy(
                queue = updatedQueue,
                currentQueueIndex = nextIndex,
                statusMessage = message
            )
        }
        if (updatedQueue.isNotEmpty()) {
            val nextTrack = updatedQueue[_state.value.currentQueueIndex]
            loadTrackIntoDeck(deckId, nextTrack, autoPlay = wasActive)
            preCueNextTrackIfNeeded()
        }
    }

    fun mixNow() {
        val s = _state.value
        if (s.isCrossfading) return
        val targetDeck = if (s.activeDeck == DeckId.A) DeckId.B else DeckId.A
        val targetState = deckState(targetDeck)

        if (targetState.track == null) {
            val nextIdx = nextQueueIndex()
            if (nextIdx != null) {
                loadTrackIntoDeck(targetDeck, s.queue[nextIdx], autoPlay = false)
                _state.update { it.copy(currentQueueIndex = nextIdx) }
                triggerCrossfadeTo(targetDeck, advanceQueueIndex = false)
            } else {
                _state.update { it.copy(statusMessage = "Δεν υπάρχει επόμενο κομμάτι στην ουρά.") }
            }
        } else {
            triggerCrossfadeTo(targetDeck, advanceQueueIndex = true)
        }
    }

    private fun checkAutoMixTrigger() {
        val s = _state.value
        if (!s.autoMixEnabled || s.isCrossfading) return
        val active = deckState(s.activeDeck)
        if (!active.isPlaying || active.durationSec <= 0) return

        val remaining = active.durationSec - active.positionSec
        val triggerThreshold = s.crossfadeDurationSec.coerceAtMost((active.durationSec / 2).coerceAtLeast(3))

        if (remaining in 1..triggerThreshold) {
            val targetDeck = if (s.activeDeck == DeckId.A) DeckId.B else DeckId.A
            if (deckState(targetDeck).track == null) {
                val nextIdx = nextQueueIndex() ?: return
                loadTrackIntoDeck(targetDeck, s.queue[nextIdx], autoPlay = false)
            }
            triggerCrossfadeTo(targetDeck, advanceQueueIndex = true)
        }
    }

    private fun triggerCrossfadeTo(targetDeck: DeckId, advanceQueueIndex: Boolean) {
        crossfadeJob?.cancel()
        val s = _state.value
        val outgoingDeck = if (targetDeck == DeckId.A) DeckId.B else DeckId.A
        val targetTrack = deckState(targetDeck).track ?: return

        // Start incoming deck at 0 volume before ramping
        startPlaybackIfPaused(targetDeck, targetTrack)

        val startCrossfader = s.crossfader
        val endCrossfader = if (targetDeck == DeckId.A) 0f else 1f
        val durationMs = (s.crossfadeDurationSec * 1000L).coerceAtLeast(2500L)

        crossfadeJob = scope.launch {
            _state.update {
                it.copy(
                    isCrossfading = true,
                    crossfadeProgress = 0f,
                    statusMessage = "Αλλαγή χωρίς κενό → ${targetTrack.title.take(26)}"
                )
            }

            val stepMs = 50L
            val totalSteps = (durationMs / stepMs).toInt().coerceAtLeast(10)
            for (step in 1..totalSteps) {
                if (!isActive) return@launch
                val t = step.toFloat() / totalSteps
                val currentX = startCrossfader + (endCrossfader - startCrossfader) * t
                _state.update {
                    it.copy(
                        crossfader = currentX,
                        crossfadeProgress = t
                    )
                }
                applyAllDeckVolumes()
                delay(stepMs)
            }

            // Transition complete: cleanly stop outgoing deck & pre-cue next track
            pauseDeck(outgoingDeck)
            val updatedQueueIndex = if (advanceQueueIndex) {
                nextQueueIndex() ?: _state.value.currentQueueIndex
            } else {
                _state.value.currentQueueIndex
            }

            _state.update {
                it.copy(
                    activeDeck = targetDeck,
                    crossfader = endCrossfader,
                    isCrossfading = false,
                    crossfadeProgress = 0f,
                    currentQueueIndex = updatedQueueIndex
                )
            }
            applyAllDeckVolumes()
            preCueNextTrackIfNeeded()
        }
    }

    private fun preCueNextTrackIfNeeded() {
        val s = _state.value
        if (!s.autoMixEnabled || s.queue.size <= 1) return
        val standbyDeck = if (s.activeDeck == DeckId.A) DeckId.B else DeckId.A
        val nextIdx = nextQueueIndex() ?: return
        val nextTrack = s.queue[nextIdx]
        if (deckState(standbyDeck).track?.sourceUriOrId != nextTrack.sourceUriOrId) {
            loadTrackIntoDeck(standbyDeck, nextTrack, autoPlay = false)
        }
    }

    private fun nextQueueIndex(): Int? {
        val q = _state.value.queue
        if (q.size <= 1) return null
        return (_state.value.currentQueueIndex + 1) % q.size
    }

    private fun loadTrackIntoDeck(deckId: DeckId, track: DjTrack, autoPlay: Boolean) {
        releaseMediaPlayer(deckId)
        existingYouTubeBridge(deckId)?.stop()

        updateDeck(deckId) {
            DeckState(
                deckId = deckId,
                track = track,
                isPlaying = false,
                isBuffering = true,
                isCuedReady = false,
                positionSec = 0,
                durationSec = track.durationSeconds,
                pitchRate = 1.0f,
                deckGain = 1.0f,
                effectiveBpm = track.bpm,
                errorMessage = null
            )
        }

        val initialVol = computeDeckVolume(deckId)
        when (track.sourceType) {
            TrackSourceType.YOUTUBE -> {
                getOrCreateYouTubeBridge(deckId).loadVideo(
                    videoId = track.sourceUriOrId,
                    autoPlay = autoPlay,
                    volumePercent = (initialVol * 100f).toInt(),
                    playbackRate = 1.0f
                )
            }
            TrackSourceType.BUILT_IN_GROOVE -> {
                scope.launch {
                    val wavFile = runCatching {
                        ProceduralGrooveGenerator.resolveGrooveFile(appContext, track.sourceUriOrId)
                    }.getOrNull()
                    if (wavFile != null) {
                        prepareMediaPlayer(deckId, Uri.fromFile(wavFile), autoPlay, initialVol)
                    } else {
                        updateDeck(deckId) {
                            it.copy(isBuffering = false, errorMessage = "Σφάλμα φόρτωσης groove")
                        }
                    }
                }
            }
            TrackSourceType.LOCAL_AUDIO -> {
                prepareMediaPlayer(deckId, Uri.parse(track.sourceUriOrId), autoPlay, initialVol)
            }
        }
    }

    private fun prepareMediaPlayer(deckId: DeckId, uri: Uri, autoPlay: Boolean, initialVol: Float) {
        runCatching {
            val mp = MediaPlayer().apply {
                setDataSource(appContext, uri)
                setVolume(initialVol, initialVol)
                setOnPreparedListener { prepared ->
                    val durSec = (prepared.duration / 1000).coerceAtLeast(1)
                    updateDeck(deckId) {
                        it.copy(
                            isBuffering = false,
                            isCuedReady = true,
                            durationSec = durSec,
                            isPlaying = autoPlay
                        )
                    }
                    if (autoPlay) {
                        prepared.start()
                    }
                    applyAllDeckVolumes()
                }
                setOnCompletionListener {
                    handleDeckTrackEnded(deckId)
                }
                setOnErrorListener { _, what, extra ->
                    Log.e("DjMixerEngine", "MediaPlayer error deck=$deckId what=$what extra=$extra")
                    updateDeck(deckId) {
                        it.copy(isBuffering = false, isPlaying = false, errorMessage = "Αδυναμία αναπαραγωγής αρχείου")
                    }
                    true
                }
                prepareAsync()
            }
            setMediaPlayer(deckId, mp)
        }.onFailure { e ->
            Log.e("DjMixerEngine", "Failed preparing MediaPlayer", e)
            updateDeck(deckId) {
                it.copy(isBuffering = false, isPlaying = false, errorMessage = "Μη προσβάσιμο αρχείο ήχου")
            }
        }
    }

    private fun startPlaybackIfPaused(deckId: DeckId, track: DjTrack) {
        when (track.sourceType) {
            TrackSourceType.YOUTUBE -> {
                getOrCreateYouTubeBridge(deckId).play()
                updateDeck(deckId) { it.copy(isPlaying = true) }
            }
            TrackSourceType.LOCAL_AUDIO, TrackSourceType.BUILT_IN_GROOVE -> {
                val mp = mediaPlayer(deckId)
                if (mp != null && !mp.isPlaying) {
                    runCatching { mp.start() }
                    updateDeck(deckId) { it.copy(isPlaying = true) }
                } else if (mp == null) {
                    loadTrackIntoDeck(deckId, track, autoPlay = true)
                }
            }
        }
    }

    private fun pauseDeck(deckId: DeckId) {
        val track = deckState(deckId).track
        when (track?.sourceType) {
            TrackSourceType.YOUTUBE -> existingYouTubeBridge(deckId)?.pause()
            TrackSourceType.LOCAL_AUDIO, TrackSourceType.BUILT_IN_GROOVE -> {
                runCatching {
                    mediaPlayer(deckId)?.let { if (it.isPlaying) it.pause() }
                }
            }
            null -> Unit
        }
        updateDeck(deckId) { it.copy(isPlaying = false) }
    }

    private fun clearDeck(deckId: DeckId) {
        releaseMediaPlayer(deckId)
        existingYouTubeBridge(deckId)?.stop()
        updateDeck(deckId) { DeckState(deckId) }
    }

    private fun updateLocalPositions() {
        listOf(DeckId.A, DeckId.B).forEach { deckId ->
            val deck = deckState(deckId)
            val track = deck.track ?: return@forEach
            if (track.sourceType != TrackSourceType.YOUTUBE) {
                val mp = mediaPlayer(deckId) ?: return@forEach
                runCatching {
                    if (mp.isPlaying) {
                        val posSec = (mp.currentPosition / 1000).coerceAtLeast(0)
                        val durSec = (mp.duration / 1000).coerceAtLeast(deck.durationSec)
                        updateDeck(deckId) {
                            it.copy(positionSec = posSec, durationSec = durSec, isPlaying = true)
                        }
                    }
                }
            }
        }
    }

    private fun applyAllDeckVolumes() {
        listOf(DeckId.A, DeckId.B).forEach { deckId ->
            val vol = computeDeckVolume(deckId)
            val track = deckState(deckId).track
            when (track?.sourceType) {
                TrackSourceType.YOUTUBE -> existingYouTubeBridge(deckId)?.setVolume((vol * 100f).toInt())
                TrackSourceType.LOCAL_AUDIO, TrackSourceType.BUILT_IN_GROOVE -> {
                    runCatching { mediaPlayer(deckId)?.setVolume(vol, vol) }
                }
                null -> Unit
            }
        }
    }

    private fun computeDeckVolume(deckId: DeckId): Float {
        val s = _state.value
        val x = s.crossfader.coerceIn(0f, 1f)
        val deckGain = deckState(deckId).deckGain
        val curveGain = when (s.crossfadeCurve) {
            CrossfadeCurve.EQUAL_POWER -> {
                if (deckId == DeckId.A) cos(x * (PI / 2.0)).toFloat()
                else sin(x * (PI / 2.0)).toFloat()
            }
            CrossfadeCurve.LINEAR -> {
                if (deckId == DeckId.A) (1f - x) else x
            }
            CrossfadeCurve.CLUB_CUT -> {
                if (deckId == DeckId.A) {
                    if (x < 0.7f) 1f else ((1f - x) / 0.3f).coerceIn(0f, 1f)
                } else {
                    if (x > 0.3f) 1f else (x / 0.3f).coerceIn(0f, 1f)
                }
            }
        }
        return (curveGain * deckGain * s.masterVolume).coerceIn(0f, 1f)
    }

    private fun handleDeckTrackEnded(deckId: DeckId) {
        updateDeck(deckId) { it.copy(isPlaying = false) }
        val s = _state.value
        if (s.autoMixEnabled && s.activeDeck == deckId && !s.isCrossfading) {
            val nextDeck = if (deckId == DeckId.A) DeckId.B else DeckId.A
            if (deckState(nextDeck).track != null) {
                triggerCrossfadeTo(nextDeck, advanceQueueIndex = true)
            } else {
                val nextIdx = nextQueueIndex()
                if (nextIdx != null) {
                    loadTrackIntoDeck(nextDeck, s.queue[nextIdx], autoPlay = true)
                    _state.update {
                        it.copy(
                            activeDeck = nextDeck,
                            crossfader = if (nextDeck == DeckId.A) 0f else 1f,
                            currentQueueIndex = nextIdx
                        )
                    }
                    preCueNextTrackIfNeeded()
                }
            }
        }
    }

    // YouTubeDeckListener callbacks
    override fun onReady(deckId: DeckId) {
        updateDeck(deckId) { it.copy(isBuffering = false, isCuedReady = true) }
    }

    override fun onStateChanged(deckId: DeckId, stateCode: Int) {
        // YT states: -1 unstarted, 0 ended, 1 playing, 2 paused, 3 buffering, 5 cued
        when (stateCode) {
            1 -> updateDeck(deckId) { it.copy(isPlaying = true, isBuffering = false, errorMessage = null) }
            2 -> updateDeck(deckId) { it.copy(isPlaying = false, isBuffering = false) }
            3 -> updateDeck(deckId) { it.copy(isBuffering = true) }
            5 -> updateDeck(deckId) { it.copy(isBuffering = false, isCuedReady = true, isPlaying = false) }
            0 -> handleDeckTrackEnded(deckId)
        }
    }

    override fun onProgress(deckId: DeckId, currentSeconds: Double, durationSeconds: Double) {
        val pos = currentSeconds.toInt().coerceAtLeast(0)
        val dur = durationSeconds.toInt().coerceAtLeast(0)
        updateDeck(deckId) { current ->
            current.copy(
                positionSec = pos,
                durationSec = if (dur > 0) dur else current.durationSec
            )
        }
    }

    override fun onError(deckId: DeckId, errorCode: Int) {
        val failedTrack = deckState(deckId).track
        val isEmbedBlocked = errorCode in listOf(101, 150, 152, 153)
        val msg = when {
            isEmbedBlocked -> "Περιορισμός ενσωμάτωσης ($errorCode) — αναζήτηση εναλλακτικού..."
            errorCode == 2 || errorCode == 5 -> "Πρόβλημα φόρτωσης video YouTube ($errorCode)."
            else -> "Σφάλμα YouTube ($errorCode)."
        }
        updateDeck(deckId) { it.copy(isPlaying = false, isBuffering = isEmbedBlocked, errorMessage = msg) }
        _state.update { it.copy(statusMessage = msg) }
        if (isEmbedBlocked && failedTrack != null && failedTrack.sourceType == TrackSourceType.YOUTUBE) {
            _embedFailureEvents.tryEmit(EmbedFailureEvent(deckId, failedTrack, errorCode))
        }
    }

    private fun deckState(deckId: DeckId): DeckState =
        if (deckId == DeckId.A) _state.value.deckA else _state.value.deckB

    private fun updateDeck(deckId: DeckId, transform: (DeckState) -> DeckState) {
        _state.update { current ->
            if (deckId == DeckId.A) current.copy(deckA = transform(current.deckA))
            else current.copy(deckB = transform(current.deckB))
        }
    }

    private fun existingYouTubeBridge(deckId: DeckId): YouTubeDeckBridge? =
        if (deckId == DeckId.A) _deckAYouTube else _deckBYouTube

    private fun mediaPlayer(deckId: DeckId): MediaPlayer? =
        if (deckId == DeckId.A) mediaPlayerA else mediaPlayerB

    private fun setMediaPlayer(deckId: DeckId, mp: MediaPlayer?) {
        if (deckId == DeckId.A) mediaPlayerA = mp else mediaPlayerB = mp
    }

    private fun releaseMediaPlayer(deckId: DeckId) {
        runCatching {
            mediaPlayer(deckId)?.apply {
                if (isPlaying) stop()
                release()
            }
        }
        setMediaPlayer(deckId, null)
    }

    fun release() {
        monitorJob?.cancel()
        crossfadeJob?.cancel()
        releaseMediaPlayer(DeckId.A)
        releaseMediaPlayer(DeckId.B)
        _deckAYouTube?.destroy()
        _deckBYouTube?.destroy()
        _deckAYouTube = null
        _deckBYouTube = null
    }
}
