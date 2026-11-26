package com.example.data.model

enum class TrackSourceType {
    YOUTUBE,
    LOCAL_AUDIO,
    BUILT_IN_GROOVE
}

enum class CrossfadeCurve(val labelGr: String) {
    EQUAL_POWER("Equal Power"),
    LINEAR("Γραμμική"),
    CLUB_CUT("Club Blend")
}

enum class DeckId {
    A,
    B
}

data class DjTrack(
    val id: Long = 0,
    val playlistId: Long = 0,
    val title: String,
    val artist: String,
    val sourceType: TrackSourceType,
    val sourceUriOrId: String,
    val thumbnailUrl: String? = null,
    val durationSeconds: Int = 180,
    val bpm: Int = 120,
    val energy: Int = 70,
    val transitionNote: String = "Ομαλό crossfade στα τελευταία δευτερόλεπτα",
    val sortOrder: Int = 0
)

data class DjPlaylist(
    val id: Long = 0,
    val name: String,
    val moodDescription: String,
    val vibeTag: String,
    val targetBpmMin: Int = 115,
    val targetBpmMax: Int = 126,
    val energyLevel: Int = 75,
    val crossfadeSeconds: Int = 8,
    val crossfadeCurve: CrossfadeCurve = CrossfadeCurve.EQUAL_POWER,
    val isAiCurated: Boolean = false,
    val createdAt: Long = System.currentTimeMillis(),
    val tracks: List<DjTrack> = emptyList()
)

data class YouTubeSearchResult(
    val videoId: String,
    val title: String,
    val channelName: String,
    val durationText: String,
    val durationSeconds: Int,
    val thumbnailUrl: String,
    val estimatedBpm: Int = 120,
    val isEmbeddable: Boolean = true
)

data class VibePreset(
    val id: String,
    val title: String,
    val subtitle: String,
    val promptSeed: String,
    val bpmRange: IntRange,
    val defaultEnergy: Int,
    val defaultCrossfadeSec: Int
)

object VibePresets {
    val all = listOf(
        VibePreset(
            id = "afro_sunset",
            title = "Sunset Afro House",
            subtitle = "Ζεστά κρουστά & ρυθμός ηλιοβασιλέματος",
            promptSeed = "Afro house & melodic groove για ηλιοβασίλεμα με φίλους",
            bpmRange = 118..124,
            defaultEnergy = 72,
            defaultCrossfadeSec = 10
        ),
        VibePreset(
            id = "midnight_drive",
            title = "Μεσάνυχτα Drive",
            subtitle = "Υπνωτική ηλεκτρονική & deep basslines",
            promptSeed = "Melodic techno και deep night drive vibes",
            bpmRange = 122..128,
            defaultEnergy = 82,
            defaultCrossfadeSec = 12
        ),
        VibePreset(
            id = "greek_club",
            title = "Ελληνικό Summer Club",
            subtitle = "Ανεβαστικά ελληνικά remixes & dance",
            promptSeed = "Ανεβαστικά ελληνικά club remixes και καλοκαιρινό πάρτι",
            bpmRange = 120..128,
            defaultEnergy = 90,
            defaultCrossfadeSec = 6
        ),
        VibePreset(
            id = "rooftop_chill",
            title = "Chill Ταράτσα",
            subtitle = "Απαλό downtempo, nu-disco & lounge",
            promptSeed = "Χαλαρό lounge nu-disco για ποτό σε ταράτσα",
            bpmRange = 102..114,
            defaultEnergy = 45,
            defaultCrossfadeSec = 10
        ),
        VibePreset(
            id = "peak_workout",
            title = "Peak Energy Cardio",
            subtitle = "Δυνατό τέμπο χωρίς καμία παύση",
            promptSeed = "Δυνατή dance house και bass για γυμναστική",
            bpmRange = 126..134,
            defaultEnergy = 95,
            defaultCrossfadeSec = 5
        ),
        VibePreset(
            id = "retro_90s",
            title = "90s / 00s Party",
            subtitle = "Κλασικά dance anthems με καθαρές αλλαγές",
            promptSeed = "90s και 00s dance party classics",
            bpmRange = 116..126,
            defaultEnergy = 84,
            defaultCrossfadeSec = 7
        )
    )
}
