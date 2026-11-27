package com.example.data

import com.example.data.ai.AiRepository
import com.example.data.local.MixPulseDao
import com.example.data.local.PlaylistEntity
import com.example.data.local.StarterPlaylistsSeeder
import com.example.data.local.TrackEntity
import com.example.data.local.toDomain
import com.example.data.local.toEntity
import com.example.data.model.CrossfadeCurve
import com.example.data.model.DjPlaylist
import com.example.data.model.DjTrack
import com.example.data.model.TrackSourceType
import com.example.data.model.YouTubeSearchResult
import com.example.data.youtube.YouTubeSearchRepository
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

data class GeneratedPlaylistOutcome(
    val playlist: DjPlaylist,
    val isPlaceholderAi: Boolean,
    val aiNoticeMessage: String,
    val resolvedYouTubeCount: Int
)

class PlaylistRepository(
    private val dao: MixPulseDao,
    private val aiRepository: AiRepository,
    private val youTubeRepository: YouTubeSearchRepository
) {

    fun observePlaylists(): Flow<List<DjPlaylist>> =
        dao.observeAllPlaylists().map { list -> list.map { it.toDomain() } }

    fun observePlaylist(playlistId: Long): Flow<DjPlaylist?> =
        dao.observePlaylist(playlistId).map { it?.toDomain() }

    suspend fun ensureSeeded() {
        StarterPlaylistsSeeder.seedIfEmpty(dao)
    }

    suspend fun generateVibePlaylist(
        vibePrompt: String,
        energyLevel: Int,
        trackCount: Int,
        crossfadeSeconds: Int,
        curve: CrossfadeCurve
    ): GeneratedPlaylistOutcome = coroutineScope {
        val curation = aiRepository.curatePlaylistByVibe(
            vibePrompt = vibePrompt,
            energyLevel = energyLevel,
            trackCount = trackCount,
            crossfadeSeconds = crossfadeSeconds
        )

        // Resolve embeddable YouTube tracks in parallel for each AI-curated suggestion
        val resolvedTracks = curation.suggestions.mapIndexed { index, suggestion ->
            async {
                val ytResult = youTubeRepository.findFirstEmbeddableTrack(suggestion.youtubeQuery)
                    ?: youTubeRepository.findFirstEmbeddableTrack("${suggestion.title} remix")

                if (ytResult != null) {
                    TrackEntity(
                        playlistId = 0,
                        title = ytResult.title,
                        artist = ytResult.channelName,
                        sourceType = TrackSourceType.YOUTUBE.name,
                        sourceUriOrId = ytResult.videoId,
                        thumbnailUrl = ytResult.thumbnailUrl,
                        durationSeconds = ytResult.durationSeconds.coerceAtLeast(45),
                        bpm = suggestion.bpm,
                        energy = suggestion.energy,
                        transitionNote = suggestion.transitionNote,
                        sortOrder = index
                    )
                } else {
                    // Graceful offline fallback to playable built-in studio groove stem
                    TrackEntity(
                        playlistId = 0,
                        title = suggestion.title,
                        artist = "${suggestion.artist} (Studio Stem)",
                        sourceType = TrackSourceType.BUILT_IN_GROOVE.name,
                        sourceUriOrId = suggestion.fallbackGrooveId,
                        thumbnailUrl = null,
                        durationSeconds = 36,
                        bpm = suggestion.bpm,
                        energy = suggestion.energy,
                        transitionNote = suggestion.transitionNote,
                        sortOrder = index
                    )
                }
            }
        }.awaitAll()

        val playlistEntity = PlaylistEntity(
            name = curation.playlistName,
            moodDescription = curation.moodSummary,
            vibeTag = curation.vibeTag,
            targetBpmMin = curation.targetBpmMin,
            targetBpmMax = curation.targetBpmMax,
            energyLevel = energyLevel,
            crossfadeSeconds = crossfadeSeconds,
            crossfadeCurve = curve.name,
            isAiCurated = true
        )

        val newId = dao.createPlaylistWithTracks(playlistEntity, resolvedTracks)
        val domainTracks = resolvedTracks.mapIndexed { idx, entity ->
            entity.copy(playlistId = newId).toDomain()
        }
        val ytCount = resolvedTracks.count { it.sourceType == TrackSourceType.YOUTUBE.name }

        GeneratedPlaylistOutcome(
            playlist = DjPlaylist(
                id = newId,
                name = playlistEntity.name,
                moodDescription = playlistEntity.moodDescription,
                vibeTag = playlistEntity.vibeTag,
                targetBpmMin = playlistEntity.targetBpmMin,
                targetBpmMax = playlistEntity.targetBpmMax,
                energyLevel = playlistEntity.energyLevel,
                crossfadeSeconds = playlistEntity.crossfadeSeconds,
                crossfadeCurve = curve,
                isAiCurated = true,
                createdAt = playlistEntity.createdAt,
                tracks = domainTracks
            ),
            isPlaceholderAi = curation.isPlaceholderSample,
            aiNoticeMessage = curation.noticeMessage,
            resolvedYouTubeCount = ytCount
        )
    }

    suspend fun createEmptyPlaylist(name: String, vibeTag: String, crossfadeSeconds: Int): Long {
        return dao.insertPlaylist(
            PlaylistEntity(
                name = name.trim().ifEmpty { "New DJ Set" },
                moodDescription = "Personal MixPulse DJ playlist",
                vibeTag = vibeTag.ifEmpty { "Custom Mix" },
                targetBpmMin = 115,
                targetBpmMax = 128,
                energyLevel = 75,
                crossfadeSeconds = crossfadeSeconds,
                crossfadeCurve = CrossfadeCurve.EQUAL_POWER.name,
                isAiCurated = false
            )
        )
    }

    suspend fun addYouTubeTrackToPlaylist(playlistId: Long, result: YouTubeSearchResult) {
        val nextOrder = dao.getMaxSortOrder(playlistId) + 1
        dao.insertTrack(
            TrackEntity(
                playlistId = playlistId,
                title = result.title,
                artist = result.channelName,
                sourceType = TrackSourceType.YOUTUBE.name,
                sourceUriOrId = result.videoId,
                thumbnailUrl = result.thumbnailUrl,
                durationSeconds = result.durationSeconds.coerceAtLeast(30),
                bpm = result.estimatedBpm,
                energy = 75,
                transitionNote = "YouTube track · Auto-crossfade ready",
                sortOrder = nextOrder
            )
        )
    }

    suspend fun addLocalTrackToPlaylist(
        playlistId: Long,
        title: String,
        uriString: String,
        durationSeconds: Int
    ) {
        val nextOrder = dao.getMaxSortOrder(playlistId) + 1
        dao.insertTrack(
            TrackEntity(
                playlistId = playlistId,
                title = title,
                artist = "Local File",
                sourceType = TrackSourceType.LOCAL_AUDIO.name,
                sourceUriOrId = uriString,
                thumbnailUrl = null,
                durationSeconds = durationSeconds.coerceAtLeast(15),
                bpm = 120,
                energy = 75,
                transitionNote = "Local audio · Gapless crossfade",
                sortOrder = nextOrder
            )
        )
    }

    suspend fun addTrack(track: DjTrack, playlistId: Long) {
        val nextOrder = dao.getMaxSortOrder(playlistId) + 1
        dao.insertTrack(track.toEntity(playlistId).copy(id = 0, sortOrder = nextOrder))
    }

    suspend fun removeTrack(trackId: Long) {
        dao.deleteTrackById(trackId)
    }

    suspend fun deletePlaylist(playlistId: Long) {
        dao.deletePlaylistById(playlistId)
    }

    suspend fun updateMixSettings(playlistId: Long, crossfadeSeconds: Int, curve: CrossfadeCurve) {
        dao.updateMixSettings(playlistId, crossfadeSeconds, curve.name)
    }

    suspend fun swapTrackOrder(trackA: DjTrack, trackB: DjTrack) {
        dao.updateTrackSortOrder(trackA.id, trackB.sortOrder)
        dao.updateTrackSortOrder(trackB.id, trackA.sortOrder)
    }
}
