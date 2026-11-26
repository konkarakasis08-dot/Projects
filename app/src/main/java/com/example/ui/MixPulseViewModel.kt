package com.example.ui

import android.app.Application
import android.content.Intent
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.provider.OpenableColumns
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.audio.DjMixerEngine
import com.example.audio.MixerUiState
import com.example.data.GeneratedPlaylistOutcome
import com.example.data.PlaylistRepository
import com.example.data.ai.SampleAiRepository
import com.example.data.local.MixPulseDatabase
import com.example.data.model.CrossfadeCurve
import com.example.data.model.DeckId
import com.example.data.model.DjPlaylist
import com.example.data.model.DjTrack
import com.example.data.model.TrackSourceType
import com.example.data.model.VibePreset
import com.example.data.model.VibePresets
import com.example.data.model.YouTubeSearchResult
import com.example.data.youtube.YouTubeSearchRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class VibeStudioUiState(
    val selectedPreset: VibePreset = VibePresets.all.first(),
    val customPrompt: String = "",
    val energyLevel: Int = 75,
    val trackCount: Int = 6,
    val crossfadeSeconds: Int = 8,
    val curve: CrossfadeCurve = CrossfadeCurve.EQUAL_POWER,
    val isGenerating: Boolean = false,
    val lastOutcome: GeneratedPlaylistOutcome? = null,
    val errorMessage: String? = null
)

data class YouTubeSearchUiState(
    val query: String = "",
    val isLoading: Boolean = false,
    val results: List<YouTubeSearchResult> = emptyList(),
    val errorMessage: String? = null,
    val targetPlaylistId: Long = 0L,
    val feedbackMessage: String? = null
)

class MixPulseViewModel(application: Application) : AndroidViewModel(application) {

    private val database = MixPulseDatabase.getInstance(application)
    private val aiRepository = SampleAiRepository()
    private val youTubeRepository = YouTubeSearchRepository(
        appOrigin = "https://${application.packageName}"
    )
    private val playlistRepository = PlaylistRepository(
        dao = database.mixPulseDao(),
        aiRepository = aiRepository,
        youTubeRepository = youTubeRepository
    )

    private val failedVideoIds = mutableSetOf<String>()

    val mixerEngine = DjMixerEngine(application)
    val mixerState: StateFlow<MixerUiState> = mixerEngine.state

    val playlists: StateFlow<List<DjPlaylist>> = playlistRepository.observePlaylists()
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    private val _vibeState = MutableStateFlow(VibeStudioUiState())
    val vibeState: StateFlow<VibeStudioUiState> = _vibeState.asStateFlow()

    private val _youtubeState = MutableStateFlow(YouTubeSearchUiState())
    val youtubeState: StateFlow<YouTubeSearchUiState> = _youtubeState.asStateFlow()

    init {
        viewModelScope.launch {
            playlistRepository.ensureSeeded()
            playlists.collect { list ->
                val activeId = mixerState.value.activePlaylistId
                if (list.isNotEmpty() && mixerState.value.queue.isEmpty()) {
                    mixerEngine.loadPlaylist(list.first(), autoStart = false)
                } else if (activeId > 0L) {
                    list.firstOrNull { it.id == activeId }?.let { activePl ->
                        mixerEngine.updateQueue(activePl.tracks)
                    }
                }
                if (list.isNotEmpty() && _youtubeState.value.targetPlaylistId == 0L) {
                    _youtubeState.update { it.copy(targetPlaylistId = list.first().id) }
                }
            }
        }
        // Perform a welcoming initial YouTube discovery search for Afro House mixes
        searchYouTube("Afro House DJ mix 2025")

        // Auto-recover if a YouTube video returns Error 150/101/152 (embed restricted)
        viewModelScope.launch {
            val consecutiveFailures = mutableMapOf<DeckId, Int>()
            mixerEngine.embedFailureEvents.collect { event ->
                failedVideoIds.add(event.track.sourceUriOrId)
                val attempts = (consecutiveFailures[event.deckId] ?: 0) + 1
                consecutiveFailures[event.deckId] = attempts

                if (attempts > 2) {
                    consecutiveFailures[event.deckId] = 0
                    if (event.track.id > 0L) {
                        playlistRepository.removeTrack(event.track.id)
                    }
                    mixerEngine.cancelBlockedTrackAndAdvance(
                        event.deckId,
                        event.track,
                        "Ακυρώθηκε τραγούδι με περιορισμό YouTube (${event.errorCode})."
                    )
                    return@collect
                }

                val alt = youTubeRepository.findFirstEmbeddableTrack(
                    query = "${event.track.title} audio remix",
                    excludeVideoIds = failedVideoIds
                ) ?: youTubeRepository.findFirstEmbeddableTrack(
                    query = "${event.track.artist} dj mix",
                    excludeVideoIds = failedVideoIds
                )
                if (alt != null) {
                    consecutiveFailures[event.deckId] = 0
                    val replacement = event.track.copy(
                        title = alt.title,
                        artist = alt.channelName,
                        sourceUriOrId = alt.videoId,
                        thumbnailUrl = alt.thumbnailUrl,
                        durationSeconds = alt.durationSeconds
                    )
                    val shouldAutoPlay = mixerState.value.activeDeck == event.deckId
                    mixerEngine.loadSingleTrackToDeck(event.deckId, replacement, autoPlay = shouldAutoPlay)
                    mixerEngine.showStatusMessage("Ακυρώθηκε το κλειδωμένο βίντεο — φορτώθηκε: ${alt.title.take(24)}")
                } else {
                    consecutiveFailures[event.deckId] = 0
                    if (event.track.id > 0L) {
                        playlistRepository.removeTrack(event.track.id)
                    }
                    mixerEngine.cancelBlockedTrackAndAdvance(
                        event.deckId,
                        event.track,
                        "Ακυρώθηκε τραγούδι με περιορισμό YouTube (${event.errorCode})."
                    )
                }
            }
        }
    }

    // --- Vibe Studio actions ---
    fun selectVibePreset(preset: VibePreset) {
        _vibeState.update {
            it.copy(
                selectedPreset = preset,
                energyLevel = preset.defaultEnergy,
                crossfadeSeconds = preset.defaultCrossfadeSec,
                errorMessage = null
            )
        }
    }

    fun updateVibePrompt(prompt: String) {
        _vibeState.update { it.copy(customPrompt = prompt, errorMessage = null) }
    }

    fun updateVibeEnergy(energy: Int) {
        _vibeState.update { it.copy(energyLevel = energy.coerceIn(25, 100)) }
    }

    fun updateVibeTrackCount(count: Int) {
        _vibeState.update { it.copy(trackCount = count.coerceIn(4, 10)) }
    }

    fun updateVibeCrossfade(seconds: Int) {
        _vibeState.update { it.copy(crossfadeSeconds = seconds.coerceIn(4, 16)) }
    }

    fun updateVibeCurve(curve: CrossfadeCurve) {
        _vibeState.update { it.copy(curve = curve) }
    }

    fun generateVibePlaylist(onReadyToOpenBooth: (DjPlaylist) -> Unit = {}) {
        val current = _vibeState.value
        if (current.isGenerating) return
        val prompt = current.customPrompt.trim().ifBlank { current.selectedPreset.promptSeed }

        viewModelScope.launch {
            _vibeState.update { it.copy(isGenerating = true, errorMessage = null) }
            try {
                val outcome = playlistRepository.generateVibePlaylist(
                    vibePrompt = prompt,
                    energyLevel = current.energyLevel,
                    trackCount = current.trackCount,
                    crossfadeSeconds = current.crossfadeSeconds,
                    curve = current.curve
                )
                _vibeState.update {
                    it.copy(isGenerating = false, lastOutcome = outcome)
                }
                mixerEngine.loadPlaylist(outcome.playlist, autoStart = false)
                onReadyToOpenBooth(outcome.playlist)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e("MixPulseVM", "Failed generating vibe playlist", e)
                _vibeState.update {
                    it.copy(
                        isGenerating = false,
                        errorMessage = e.localizedMessage ?: "Αποτυχία δημιουργίας playlist"
                    )
                }
            }
        }
    }

    // --- YouTube search & track addition ---
    fun updateYouTubeQuery(query: String) {
        _youtubeState.update { it.copy(query = query, errorMessage = null) }
    }

    fun setTargetPlaylistForYouTube(playlistId: Long) {
        _youtubeState.update { it.copy(targetPlaylistId = playlistId) }
    }

    fun searchYouTube(queryOverride: String? = null) {
        val query = (queryOverride ?: _youtubeState.value.query).trim()
        if (query.isEmpty()) return
        if (queryOverride != null) {
            _youtubeState.update { it.copy(query = queryOverride) }
        }

        viewModelScope.launch {
            _youtubeState.update { it.copy(isLoading = true, errorMessage = null) }
            val result = youTubeRepository.searchTracks(query)
            result.onSuccess { items ->
                _youtubeState.update {
                    it.copy(
                        isLoading = false,
                        results = items,
                        errorMessage = if (items.isEmpty()) "Δεν βρέθηκαν διαθέσιμα τραγούδια (όσα είχαν περιορισμό YouTube 150 αφαιρέθηκαν). Δοκίμασε π.χ. remix ή DJ mix." else null
                    )
                }
            }.onFailure { err ->
                _youtubeState.update {
                    it.copy(
                        isLoading = false,
                        errorMessage = err.localizedMessage ?: "Αδυναμία σύνδεσης με YouTube"
                    )
                }
            }
        }
    }

    fun addYouTubeTrackToPlaylist(result: YouTubeSearchResult, playlistId: Long) {
        viewModelScope.launch {
            val targetId = if (playlistId > 0L) playlistId else playlists.value.firstOrNull()?.id ?: 0L
            val finalId = if (targetId > 0L) targetId else playlistRepository.createEmptyPlaylist("My YouTube DJ Mix", "YouTube", 8)
            playlistRepository.addYouTubeTrackToPlaylist(finalId, result)

            // If this playlist is currently loaded in the Booth, refresh the engine queue smoothly
            if (mixerState.value.activePlaylistId == finalId) {
                val updated = playlists.value.firstOrNull { it.id == finalId }
                if (updated != null) {
                    val newTrack = DjTrack(
                        playlistId = finalId,
                        title = result.title,
                        artist = result.channelName,
                        sourceType = TrackSourceType.YOUTUBE,
                        sourceUriOrId = result.videoId,
                        thumbnailUrl = result.thumbnailUrl,
                        durationSeconds = result.durationSeconds,
                        bpm = result.estimatedBpm
                    )
                    // Reload queue if needed without interrupting active playback
                    if (mixerState.value.queue.isEmpty()) {
                        mixerEngine.loadPlaylist(updated.copy(tracks = updated.tracks + newTrack), autoStart = false)
                    }
                }
            }
            _youtubeState.update {
                it.copy(feedbackMessage = "Προστέθηκε: ${result.title.take(28)}")
            }
        }
    }

    fun loadYouTubeResultToDeck(result: YouTubeSearchResult, deckId: DeckId) {
        val track = DjTrack(
            title = result.title,
            artist = result.channelName,
            sourceType = TrackSourceType.YOUTUBE,
            sourceUriOrId = result.videoId,
            thumbnailUrl = result.thumbnailUrl,
            durationSeconds = result.durationSeconds,
            bpm = result.estimatedBpm,
            transitionNote = "Φορτώθηκε απευθείας από YouTube στο Deck ${deckId.name}"
        )
        mixerEngine.loadSingleTrackToDeck(deckId, track, autoPlay = true)
    }

    fun clearYouTubeFeedback() {
        _youtubeState.update { it.copy(feedbackMessage = null) }
    }

    // --- Local Audio Import (Zero-Permission SAF) ---
    fun importLocalAudio(uri: Uri, targetPlaylistId: Long? = null) {
        val ctx = getApplication<Application>()
        viewModelScope.launch {
            runCatching {
                ctx.contentResolver.takePersistableUriPermission(
                    uri,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION
                )
            }
            val meta = withContext(Dispatchers.IO) { readAudioMetadata(ctx, uri) }
            val pid = targetPlaylistId
                ?: mixerState.value.activePlaylistId.takeIf { it > 0L }
                ?: playlists.value.firstOrNull()?.id
                ?: playlistRepository.createEmptyPlaylist("Τοπική Λίστα DJ", "Local", 8)

            playlistRepository.addLocalTrackToPlaylist(
                playlistId = pid,
                title = meta.first,
                uriString = uri.toString(),
                durationSeconds = meta.second
            )
            _youtubeState.update {
                it.copy(feedbackMessage = "Προστέθηκε τοπικό κομμάτι: ${meta.first.take(26)}")
            }
        }
    }

    private fun readAudioMetadata(context: Application, uri: Uri): Pair<String, Int> {
        var displayName = "Local Track"
        context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
            val nameIdx = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
            if (cursor.moveToFirst() && nameIdx >= 0) {
                displayName = cursor.getString(nameIdx)
                    .substringBeforeLast('.')
                    .ifBlank { "Local Track" }
            }
        }
        var durationSec = 180
        val retriever = MediaMetadataRetriever()
        runCatching {
            retriever.setDataSource(context, uri)
            val title = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_TITLE)
            if (!title.isNullOrBlank()) displayName = title
            val durMs = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull()
            if (durMs != null && durMs > 0) {
                durationSec = (durMs / 1000L).toInt().coerceAtLeast(15)
            }
        }
        runCatching { retriever.release() }
        return displayName to durationSec
    }

    // --- Playlist Management & Booth Sync ---
    fun loadPlaylistInBooth(playlist: DjPlaylist, autoStart: Boolean = true) {
        mixerEngine.loadPlaylist(playlist, autoStart = autoStart)
    }

    fun createEmptyPlaylist(name: String, vibeTag: String) {
        viewModelScope.launch {
            val id = playlistRepository.createEmptyPlaylist(name, vibeTag, 8)
            _youtubeState.update { it.copy(targetPlaylistId = id) }
        }
    }

    fun deletePlaylist(playlistId: Long) {
        viewModelScope.launch {
            playlistRepository.deletePlaylist(playlistId)
        }
    }

    fun removeTrack(trackId: Long) {
        viewModelScope.launch {
            playlistRepository.removeTrack(trackId)
        }
    }

    fun updateActivePlaylistMixSettings(crossfadeSec: Int, curve: CrossfadeCurve) {
        mixerEngine.setCrossfadeDuration(crossfadeSec)
        mixerEngine.setCrossfadeCurve(curve)
        val pid = mixerState.value.activePlaylistId
        if (pid > 0L) {
            viewModelScope.launch {
                playlistRepository.updateMixSettings(pid, crossfadeSec, curve)
            }
        }
    }

    override fun onCleared() {
        super.onCleared()
        mixerEngine.release()
    }
}
