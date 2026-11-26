package com.example.data.local

import com.example.data.model.CrossfadeCurve
import com.example.data.model.DjPlaylist
import com.example.data.model.DjTrack
import com.example.data.model.TrackSourceType

data class PlaylistEntity(
    val id: Long = 0,
    val name: String,
    val moodDescription: String,
    val vibeTag: String,
    val targetBpmMin: Int,
    val targetBpmMax: Int,
    val energyLevel: Int,
    val crossfadeSeconds: Int,
    val crossfadeCurve: String,
    val isAiCurated: Boolean,
    val createdAt: Long = System.currentTimeMillis()
)

data class TrackEntity(
    val id: Long = 0,
    val playlistId: Long,
    val title: String,
    val artist: String,
    val sourceType: String,
    val sourceUriOrId: String,
    val thumbnailUrl: String?,
    val durationSeconds: Int,
    val bpm: Int,
    val energy: Int,
    val transitionNote: String,
    val sortOrder: Int
)

data class PlaylistWithTracks(
    val playlist: PlaylistEntity,
    val tracks: List<TrackEntity>
)

fun PlaylistWithTracks.toDomain(): DjPlaylist = DjPlaylist(
    id = playlist.id,
    name = playlist.name,
    moodDescription = playlist.moodDescription,
    vibeTag = playlist.vibeTag,
    targetBpmMin = playlist.targetBpmMin,
    targetBpmMax = playlist.targetBpmMax,
    energyLevel = playlist.energyLevel,
    crossfadeSeconds = playlist.crossfadeSeconds,
    crossfadeCurve = runCatching { CrossfadeCurve.valueOf(playlist.crossfadeCurve) }
        .getOrDefault(CrossfadeCurve.EQUAL_POWER),
    isAiCurated = playlist.isAiCurated,
    createdAt = playlist.createdAt,
    tracks = tracks.sortedBy { it.sortOrder }.map { it.toDomain() }
)

fun TrackEntity.toDomain(): DjTrack = DjTrack(
    id = id,
    playlistId = playlistId,
    title = title,
    artist = artist,
    sourceType = runCatching { TrackSourceType.valueOf(sourceType) }
        .getOrDefault(TrackSourceType.YOUTUBE),
    sourceUriOrId = sourceUriOrId,
    thumbnailUrl = thumbnailUrl,
    durationSeconds = durationSeconds,
    bpm = bpm,
    energy = energy,
    transitionNote = transitionNote,
    sortOrder = sortOrder
)

fun DjPlaylist.toEntity(): PlaylistEntity = PlaylistEntity(
    id = id,
    name = name,
    moodDescription = moodDescription,
    vibeTag = vibeTag,
    targetBpmMin = targetBpmMin,
    targetBpmMax = targetBpmMax,
    energyLevel = energyLevel,
    crossfadeSeconds = crossfadeSeconds,
    crossfadeCurve = crossfadeCurve.name,
    isAiCurated = isAiCurated,
    createdAt = createdAt
)

fun DjTrack.toEntity(playlistIdOverride: Long = playlistId): TrackEntity = TrackEntity(
    id = id,
    playlistId = playlistIdOverride,
    title = title,
    artist = artist,
    sourceType = sourceType.name,
    sourceUriOrId = sourceUriOrId,
    thumbnailUrl = thumbnailUrl,
    durationSeconds = durationSeconds,
    bpm = bpm,
    energy = energy,
    transitionNote = transitionNote,
    sortOrder = sortOrder
)
