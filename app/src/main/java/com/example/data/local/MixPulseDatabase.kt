package com.example.data.local

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import com.example.data.model.CrossfadeCurve
import com.example.data.model.TrackSourceType

class MixPulseDatabase private constructor(context: Context) :
    SQLiteOpenHelper(context.applicationContext, "mixpulse_sqlite.db", null, 1) {

    private val dao = MixPulseDao(this)

    fun mixPulseDao(): MixPulseDao = dao

    override fun onConfigure(db: SQLiteDatabase) {
        super.onConfigure(db)
        db.setForeignKeyConstraintsEnabled(true)
    }

    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS playlists (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                name TEXT NOT NULL,
                moodDescription TEXT NOT NULL,
                vibeTag TEXT NOT NULL,
                targetBpmMin INTEGER NOT NULL,
                targetBpmMax INTEGER NOT NULL,
                energyLevel INTEGER NOT NULL,
                crossfadeSeconds INTEGER NOT NULL,
                crossfadeCurve TEXT NOT NULL,
                isAiCurated INTEGER NOT NULL,
                createdAt INTEGER NOT NULL
            )
            """.trimIndent()
        )
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS tracks (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                playlistId INTEGER NOT NULL,
                title TEXT NOT NULL,
                artist TEXT NOT NULL,
                sourceType TEXT NOT NULL,
                sourceUriOrId TEXT NOT NULL,
                thumbnailUrl TEXT,
                durationSeconds INTEGER NOT NULL,
                bpm INTEGER NOT NULL,
                energy INTEGER NOT NULL,
                transitionNote TEXT NOT NULL,
                sortOrder INTEGER NOT NULL,
                FOREIGN KEY(playlistId) REFERENCES playlists(id) ON DELETE CASCADE
            )
            """.trimIndent()
        )
        db.execSQL("CREATE INDEX IF NOT EXISTS index_tracks_playlistId ON tracks(playlistId)")
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        db.execSQL("DROP TABLE IF EXISTS tracks")
        db.execSQL("DROP TABLE IF EXISTS playlists")
        onCreate(db)
    }

    companion object {
        @Volatile
        private var INSTANCE: MixPulseDatabase? = null

        fun getInstance(context: Context): MixPulseDatabase {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: MixPulseDatabase(context).also { INSTANCE = it }
            }
        }
    }
}

object StarterPlaylistsSeeder {
    suspend fun seedIfEmpty(dao: MixPulseDao) {
        if (dao.getPlaylistCount() > 0) return

        val groovePlaylistId = dao.insertPlaylist(
            PlaylistEntity(
                name = "Midnight Groove (Live Stems)",
                moodDescription = "Έτοιμο DJ σετ με ενσωματωμένα ρυθμικά stems για άμεσο τεστ crossfade χωρίς κενό",
                vibeTag = "Afro & Deep Groove",
                targetBpmMin = 116,
                targetBpmMax = 128,
                energyLevel = 78,
                crossfadeSeconds = 8,
                crossfadeCurve = CrossfadeCurve.EQUAL_POWER.name,
                isAiCurated = false
            )
        )

        dao.insertTracks(
            listOf(
                TrackEntity(
                    playlistId = groovePlaylistId,
                    title = "Aegean Horizon (Warm-Up Groove)",
                    artist = "MixPulse Studio Stem",
                    sourceType = TrackSourceType.BUILT_IN_GROOVE.name,
                    sourceUriOrId = "groove://sunset_deep_116",
                    thumbnailUrl = null,
                    durationSeconds = 36,
                    bpm = 116,
                    energy = 68,
                    transitionNote = "Ξεκίνημα με ζεστό deep bassline — έτοιμο για 8s crossfade",
                    sortOrder = 0
                ),
                TrackEntity(
                    playlistId = groovePlaylistId,
                    title = "Cyclades Tribal Pulse",
                    artist = "MixPulse Studio Stem",
                    sourceType = TrackSourceType.BUILT_IN_GROOVE.name,
                    sourceUriOrId = "groove://afro_pulse_122",
                    thumbnailUrl = null,
                    durationSeconds = 36,
                    bpm = 122,
                    energy = 80,
                    transitionNote = "Ανέβασμα κρουστών στα 122 BPM με Equal Power αλλαγή",
                    sortOrder = 1
                ),
                TrackEntity(
                    playlistId = groovePlaylistId,
                    title = "Athenian Midnight Drive",
                    artist = "MixPulse Studio Stem",
                    sourceType = TrackSourceType.BUILT_IN_GROOVE.name,
                    sourceUriOrId = "groove://neon_techno_128",
                    thumbnailUrl = null,
                    durationSeconds = 36,
                    bpm = 128,
                    energy = 92,
                    transitionNote = "Peak-time club kick & synth arpeggio",
                    sortOrder = 2
                ),
                TrackEntity(
                    playlistId = groovePlaylistId,
                    title = "Afterhours Velvet Echo",
                    artist = "MixPulse Studio Stem",
                    sourceType = TrackSourceType.BUILT_IN_GROOVE.name,
                    sourceUriOrId = "groove://midnight_lofi_96",
                    thumbnailUrl = null,
                    durationSeconds = 36,
                    bpm = 96,
                    energy = 52,
                    transitionNote = "Ομαλό σβήσιμο για χαλαρό κλείσιμο σετ",
                    sortOrder = 3
                )
            )
        )
    }
}
