package com.example.data.local

import android.content.ContentValues
import android.database.Cursor
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext

class MixPulseDao(private val dbHelper: SQLiteOpenHelper) {

    private val changeTrigger = MutableStateFlow(0L)

    private fun notifyChanged() {
        changeTrigger.value = changeTrigger.value + 1L
    }

    fun observeAllPlaylists(): Flow<List<PlaylistWithTracks>> =
        changeTrigger.map {
            withContext(Dispatchers.IO) {
                queryAllPlaylistsSync()
            }
        }

    fun observePlaylist(playlistId: Long): Flow<PlaylistWithTracks?> =
        changeTrigger.map {
            withContext(Dispatchers.IO) {
                queryAllPlaylistsSync().firstOrNull { it.playlist.id == playlistId }
            }
        }

    suspend fun getPlaylistCount(): Int = withContext(Dispatchers.IO) {
        val db = dbHelper.readableDatabase
        db.rawQuery("SELECT COUNT(*) FROM playlists", null).use { cursor ->
            if (cursor.moveToFirst()) cursor.getInt(0) else 0
        }
    }

    suspend fun insertPlaylist(playlist: PlaylistEntity): Long = withContext(Dispatchers.IO) {
        val db = dbHelper.writableDatabase
        val id = insertPlaylistInternal(db, playlist)
        notifyChanged()
        id
    }

    suspend fun insertTracks(tracks: List<TrackEntity>) = withContext(Dispatchers.IO) {
        val db = dbHelper.writableDatabase
        db.beginTransaction()
        try {
            tracks.forEach { insertTrackInternal(db, it) }
            db.setTransactionSuccessful()
        } finally {
            db.endTransaction()
        }
        notifyChanged()
    }

    suspend fun insertTrack(track: TrackEntity): Long = withContext(Dispatchers.IO) {
        val db = dbHelper.writableDatabase
        val id = insertTrackInternal(db, track)
        notifyChanged()
        id
    }

    suspend fun getMaxSortOrder(playlistId: Long): Int = withContext(Dispatchers.IO) {
        val db = dbHelper.readableDatabase
        db.rawQuery(
            "SELECT COALESCE(MAX(sortOrder), -1) FROM tracks WHERE playlistId = ?",
            arrayOf(playlistId.toString())
        ).use { cursor ->
            if (cursor.moveToFirst()) cursor.getInt(0) else -1
        }
    }

    suspend fun deleteTrackById(trackId: Long) = withContext(Dispatchers.IO) {
        val db = dbHelper.writableDatabase
        db.delete("tracks", "id = ?", arrayOf(trackId.toString()))
        notifyChanged()
    }

    suspend fun deletePlaylistById(playlistId: Long) = withContext(Dispatchers.IO) {
        val db = dbHelper.writableDatabase
        db.delete("tracks", "playlistId = ?", arrayOf(playlistId.toString()))
        db.delete("playlists", "id = ?", arrayOf(playlistId.toString()))
        notifyChanged()
    }

    suspend fun updateMixSettings(playlistId: Long, crossfadeSeconds: Int, curve: String) =
        withContext(Dispatchers.IO) {
            val db = dbHelper.writableDatabase
            val values = ContentValues().apply {
                put("crossfadeSeconds", crossfadeSeconds)
                put("crossfadeCurve", curve)
            }
            db.update("playlists", values, "id = ?", arrayOf(playlistId.toString()))
            notifyChanged()
        }

    suspend fun updateTrackSortOrder(trackId: Long, sortOrder: Int) = withContext(Dispatchers.IO) {
        val db = dbHelper.writableDatabase
        val values = ContentValues().apply {
            put("sortOrder", sortOrder)
        }
        db.update("tracks", values, "id = ?", arrayOf(trackId.toString()))
        notifyChanged()
    }

    suspend fun createPlaylistWithTracks(playlist: PlaylistEntity, tracks: List<TrackEntity>): Long =
        withContext(Dispatchers.IO) {
            val db = dbHelper.writableDatabase
            db.beginTransaction()
            val id = try {
                val newId = insertPlaylistInternal(db, playlist)
                tracks.forEachIndexed { index, track ->
                    insertTrackInternal(db, track.copy(playlistId = newId, sortOrder = index))
                }
                db.setTransactionSuccessful()
                newId
            } finally {
                db.endTransaction()
            }
            notifyChanged()
            id
        }

    private fun insertPlaylistInternal(db: SQLiteDatabase, playlist: PlaylistEntity): Long {
        val values = ContentValues().apply {
            if (playlist.id > 0L) put("id", playlist.id)
            put("name", playlist.name)
            put("moodDescription", playlist.moodDescription)
            put("vibeTag", playlist.vibeTag)
            put("targetBpmMin", playlist.targetBpmMin)
            put("targetBpmMax", playlist.targetBpmMax)
            put("energyLevel", playlist.energyLevel)
            put("crossfadeSeconds", playlist.crossfadeSeconds)
            put("crossfadeCurve", playlist.crossfadeCurve)
            put("isAiCurated", if (playlist.isAiCurated) 1 else 0)
            put("createdAt", playlist.createdAt)
        }
        return db.insertWithOnConflict("playlists", null, values, SQLiteDatabase.CONFLICT_REPLACE)
    }

    private fun insertTrackInternal(db: SQLiteDatabase, track: TrackEntity): Long {
        val values = ContentValues().apply {
            if (track.id > 0L) put("id", track.id)
            put("playlistId", track.playlistId)
            put("title", track.title)
            put("artist", track.artist)
            put("sourceType", track.sourceType)
            put("sourceUriOrId", track.sourceUriOrId)
            if (track.thumbnailUrl != null) put("thumbnailUrl", track.thumbnailUrl)
            else putNull("thumbnailUrl")
            put("durationSeconds", track.durationSeconds)
            put("bpm", track.bpm)
            put("energy", track.energy)
            put("transitionNote", track.transitionNote)
            put("sortOrder", track.sortOrder)
        }
        return db.insertWithOnConflict("tracks", null, values, SQLiteDatabase.CONFLICT_REPLACE)
    }

    private fun queryAllPlaylistsSync(): List<PlaylistWithTracks> {
        val db = dbHelper.readableDatabase
        val playlists = mutableListOf<PlaylistEntity>()
        db.rawQuery("SELECT * FROM playlists ORDER BY createdAt DESC", null).use { cursor ->
            while (cursor.moveToNext()) {
                playlists.add(cursor.toPlaylistEntity())
            }
        }

        val tracksByPlaylist = mutableMapOf<Long, MutableList<TrackEntity>>()
        db.rawQuery("SELECT * FROM tracks ORDER BY sortOrder ASC", null).use { cursor ->
            while (cursor.moveToNext()) {
                val track = cursor.toTrackEntity()
                tracksByPlaylist.getOrPut(track.playlistId) { mutableListOf() }.add(track)
            }
        }

        return playlists.map { pl ->
            PlaylistWithTracks(
                playlist = pl,
                tracks = tracksByPlaylist[pl.id].orEmpty()
            )
        }
    }

    private fun Cursor.toPlaylistEntity(): PlaylistEntity = PlaylistEntity(
        id = getLong(getColumnIndexOrThrow("id")),
        name = getString(getColumnIndexOrThrow("name")),
        moodDescription = getString(getColumnIndexOrThrow("moodDescription")),
        vibeTag = getString(getColumnIndexOrThrow("vibeTag")),
        targetBpmMin = getInt(getColumnIndexOrThrow("targetBpmMin")),
        targetBpmMax = getInt(getColumnIndexOrThrow("targetBpmMax")),
        energyLevel = getInt(getColumnIndexOrThrow("energyLevel")),
        crossfadeSeconds = getInt(getColumnIndexOrThrow("crossfadeSeconds")),
        crossfadeCurve = getString(getColumnIndexOrThrow("crossfadeCurve")),
        isAiCurated = getInt(getColumnIndexOrThrow("isAiCurated")) == 1,
        createdAt = getLong(getColumnIndexOrThrow("createdAt"))
    )

    private fun Cursor.toTrackEntity(): TrackEntity {
        val thumbIdx = getColumnIndexOrThrow("thumbnailUrl")
        return TrackEntity(
            id = getLong(getColumnIndexOrThrow("id")),
            playlistId = getLong(getColumnIndexOrThrow("playlistId")),
            title = getString(getColumnIndexOrThrow("title")),
            artist = getString(getColumnIndexOrThrow("artist")),
            sourceType = getString(getColumnIndexOrThrow("sourceType")),
            sourceUriOrId = getString(getColumnIndexOrThrow("sourceUriOrId")),
            thumbnailUrl = if (isNull(thumbIdx)) null else getString(thumbIdx),
            durationSeconds = getInt(getColumnIndexOrThrow("durationSeconds")),
            bpm = getInt(getColumnIndexOrThrow("bpm")),
            energy = getInt(getColumnIndexOrThrow("energy")),
            transitionNote = getString(getColumnIndexOrThrow("transitionNote")),
            sortOrder = getInt(getColumnIndexOrThrow("sortOrder"))
        )
    }
}
