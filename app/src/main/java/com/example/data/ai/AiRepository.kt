package com.example.data.ai

import kotlinx.coroutines.delay

data class CuratedTrackSuggestion(
    val title: String,
    val artist: String,
    val youtubeQuery: String,
    val bpm: Int,
    val energy: Int,
    val transitionNote: String,
    val fallbackGrooveId: String
)

data class AiCurationResult(
    val playlistName: String,
    val moodSummary: String,
    val vibeTag: String,
    val targetBpmMin: Int,
    val targetBpmMax: Int,
    val suggestions: List<CuratedTrackSuggestion>,
    val isPlaceholderSample: Boolean,
    val noticeMessage: String
)

interface AiRepository {
    suspend fun curatePlaylistByVibe(
        vibePrompt: String,
        energyLevel: Int,
        trackCount: Int,
        crossfadeSeconds: Int
    ): AiCurationResult
}

/**
 * Sample / offline heuristic implementation used when Firebase AI Logic is not enabled.
 * Always surfaces [isPlaceholderSample] = true and [noticeMessage] in the UI.
 */
class SampleAiRepository : AiRepository {

    override suspend fun curatePlaylistByVibe(
        vibePrompt: String,
        energyLevel: Int,
        trackCount: Int,
        crossfadeSeconds: Int
    ): AiCurationResult {
        delay(450)
        val normalized = vibePrompt.lowercase()

        val profile = when {
            normalized.contains("summer") || normalized.contains("greek") ||
                normalized.contains("ελλην") || normalized.contains("island") -> GreekClubProfile
            normalized.contains("afro") || normalized.contains("sunset") ||
                normalized.contains("beach") || normalized.contains("ηλιοβασ") -> AfroSunsetProfile
            normalized.contains("techno") || normalized.contains("drive") ||
                normalized.contains("midnight") || normalized.contains("club") ||
                normalized.contains("rave") -> MidnightTechnoProfile
            normalized.contains("chill") || normalized.contains("rooftop") ||
                normalized.contains("lounge") || normalized.contains("lofi") ||
                normalized.contains("drink") -> RooftopChillProfile
            normalized.contains("workout") || normalized.contains("cardio") ||
                normalized.contains("run") || normalized.contains("energy") -> PeakWorkoutProfile
            normalized.contains("90s") || normalized.contains("retro") ||
                normalized.contains("disco") || normalized.contains("party") -> RetroPartyProfile
            else -> if (energyLevel >= 75) AfroSunsetProfile else RooftopChillProfile
        }

        val count = trackCount.coerceIn(4, 10)
        val selected = profile.tracks.take(count)

        val adjustedSuggestions = selected.mapIndexed { index, item ->
            val energyStep = ((energyLevel - 50) * 0.25f).toInt()
            val bpmShift = ((energyLevel - 70) / 10)
            val note = when (index) {
                0 -> "Set opener · Clean intro cue (${crossfadeSeconds}s fade ready)"
                selected.lastIndex -> "Set closer · Extended ${crossfadeSeconds}s outro blend"
                else -> "${item.transitionNote} · Crossfade ${crossfadeSeconds}s"
            }
            item.copy(
                bpm = (item.bpm + bpmShift).coerceIn(88, 140),
                energy = (item.energy + energyStep).coerceIn(30, 99),
                transitionNote = note
            )
        }

        return AiCurationResult(
            playlistName = profile.playlistTitle,
            moodSummary = "Curated for: \"${vibePrompt.ifBlank { profile.vibeTag }}\" · Smooth gapless BPM flow",
            vibeTag = profile.vibeTag,
            targetBpmMin = adjustedSuggestions.minOf { it.bpm },
            targetBpmMax = adjustedSuggestions.maxOf { it.bpm },
            suggestions = adjustedSuggestions,
            isPlaceholderSample = true,
            noticeMessage = "Sample AI suggestions (offline fallback without Firebase). Enable Firebase for live Gemini curation."
        )
    }

    private data class VibeProfile(
        val playlistTitle: String,
        val vibeTag: String,
        val tracks: List<CuratedTrackSuggestion>
    )

    private val AfroSunsetProfile = VibeProfile(
        playlistTitle = "Sunset Afro Groove Set",
        vibeTag = "Afro House",
        tracks = listOf(
            CuratedTrackSuggestion("Keinemusik & Rampa - Les Gout", "Keinemusik", "Keinemusik Rampa Les Gout official audio", 120, 70, "Gentle percussion fade-in", "groove://sunset_deep_116"),
            CuratedTrackSuggestion("Adam Port - Move", "Adam Port & Stryv", "Adam Port Stryv Move afro house", 120, 76, "Lock bassline at -8s", "groove://afro_pulse_122"),
            CuratedTrackSuggestion("HUGEL - Morenita", "HUGEL", "HUGEL Morenita official", 123, 82, "Sync groove & clap", "groove://afro_pulse_122"),
            CuratedTrackSuggestion("Black Coffee - Drive", "Black Coffee & David Guetta", "Black Coffee Drive official", 122, 78, "Atmospheric vocal blend", "groove://sunset_deep_116"),
            CuratedTrackSuggestion("Moojo - Ms. Jackson", "Moojo", "Moojo afro house remix", 122, 84, "Cut lows on outro", "groove://afro_pulse_122"),
            CuratedTrackSuggestion("Caiiro - The Akan", "Caiiro", "Caiiro The Akan afro house", 124, 88, "Build into tribal peak", "groove://neon_techno_128"),
            CuratedTrackSuggestion("RÜFÜS DU SOL - Innerbloom", "RÜFÜS DU SOL", "RUFUS DU SOL Innerbloom", 122, 75, "Epic melodic breakdown", "groove://sunset_deep_116"),
            CuratedTrackSuggestion("Francis Mercier - Sauti", "Francis Mercier", "Francis Mercier afro house", 121, 79, "Smooth beatmatch mix", "groove://afro_pulse_122"),
            CuratedTrackSuggestion("Shimza - Calling Out Your Name", "Shimza", "Shimza afro house mix", 123, 83, "Deep bass swap", "groove://afro_pulse_122"),
            CuratedTrackSuggestion("Themba - Ashamed", "Themba", "Themba Ashamed official", 122, 74, "Melodic fade-out", "groove://midnight_lofi_96")
        )
    )

    private val GreekClubProfile = VibeProfile(
        playlistTitle = "Mediterranean Summer Club Mix",
        vibeTag = "Summer Club",
        tracks = listOf(
            CuratedTrackSuggestion("Playmen & Claydee - Fallin", "Playmen", "Playmen Fallin official audio", 122, 82, "Quick intro beatmatch", "groove://afro_pulse_122"),
            CuratedTrackSuggestion("Konstantinos Argiros - Eleftheros Remix", "K. Argiros DJ Remix", "Konstantinos Argiros club remix", 124, 88, "Energy lift on the drop", "groove://neon_techno_128"),
            CuratedTrackSuggestion("Eleni Foureira - Fuego Remix", "Eleni Foureira", "Eleni Foureira Fuego dance remix", 124, 92, "Clean dance beat swap", "groove://neon_techno_128"),
            CuratedTrackSuggestion("Marseaux & Solmeister - Club Mix", "Marseaux", "Greek pop house remix 2024", 122, 84, "Vocal overlap at 6s", "groove://afro_pulse_122"),
            CuratedTrackSuggestion("Nikos Oikonomopoulos - Summer Bootleg", "Club Bootleg", "Greek house remix summer hits", 125, 90, "Bass filter transition", "groove://neon_techno_128"),
            CuratedTrackSuggestion("Marina Satti - Zari Club Edit", "Marina Satti", "Marina Satti Zari remix", 126, 94, "Rhythmic build-up blend", "groove://neon_techno_128"),
            CuratedTrackSuggestion("Sakis Rouvas - Ola Kala Dance Edit", "Sakis Rouvas", "Greek dance party mix", 124, 88, "Peak party lift", "groove://afro_pulse_122"),
            CuratedTrackSuggestion("Josephine - Magia Remix", "Josephine", "Josephine greek club remix", 123, 86, "Smooth outro", "groove://sunset_deep_116")
        )
    )

    private val MidnightTechnoProfile = VibeProfile(
        playlistTitle = "Midnight Melodic Drive",
        vibeTag = "Melodic Techno",
        tracks = listOf(
            CuratedTrackSuggestion("Anyma & Chris Avantgarde - Consciousness", "Anyma", "Anyma Consciousness official", 124, 84, "Gradual synth open", "groove://neon_techno_128"),
            CuratedTrackSuggestion("ARTBAT - Horizon", "ARTBAT", "ARTBAT Horizon melodic techno", 125, 86, "Long 12s blend", "groove://neon_techno_128"),
            CuratedTrackSuggestion("Tale Of Us - Nova", "Tale Of Us", "Tale of Us melodic techno mix", 126, 88, "Mix in on breakdown", "groove://neon_techno_128"),
            CuratedTrackSuggestion("Stephan Bodzin - Singularity", "Stephan Bodzin", "Stephan Bodzin Singularity", 124, 85, "Keep bassline locked", "groove://afro_pulse_122"),
            CuratedTrackSuggestion("Mind Against - Walking Away", "Mind Against", "Mind Against Walking Away", 125, 87, "Hypnotic arpeggio crossfade", "groove://neon_techno_128"),
            CuratedTrackSuggestion("Miss Monique - Concorde", "Miss Monique", "Miss Monique progressive house", 126, 90, "High-impact drop transition", "groove://neon_techno_128"),
            CuratedTrackSuggestion("Ben Böhmer - Beyond Beliefs", "Ben Böhmer", "Ben Bohmer Beyond Beliefs", 122, 76, "Warm pad cooldown", "groove://sunset_deep_116"),
            CuratedTrackSuggestion("Monolink - Return to Oz (ARTBAT Remix)", "Monolink", "Monolink Return to Oz ARTBAT", 124, 82, "Atmospheric finale", "groove://sunset_deep_116")
        )
    )

    private val RooftopChillProfile = VibeProfile(
        playlistTitle = "Chill Rooftop Sessions",
        vibeTag = "Deep Lounge",
        tracks = listOf(
            CuratedTrackSuggestion("Purple Disco Machine - Hypnotized", "Purple Disco Machine", "Purple Disco Machine Hypnotized", 112, 62, "Soft nu-disco blend", "groove://sunset_deep_116"),
            CuratedTrackSuggestion("Polo & Pan - Nanã", "Polo & Pan", "Polo and Pan Nana", 108, 55, "Melodic bridge with zero beat clash", "groove://midnight_lofi_96"),
            CuratedTrackSuggestion("Khruangbin - Time (You and I)", "Khruangbin", "Khruangbin Time You and I", 104, 52, "Warm funk crossfade", "groove://midnight_lofi_96"),
            CuratedTrackSuggestion("Elderbrook - Numb", "Elderbrook", "Elderbrook Numb official", 116, 65, "Subtle tempo lift", "groove://sunset_deep_116"),
            CuratedTrackSuggestion("Disclosure - Latch (Acoustic/Chill)", "Disclosure", "Disclosure Deep House chill mix", 114, 60, "Vocal-to-pad blend", "groove://sunset_deep_116"),
            CuratedTrackSuggestion("L'Impératrice - Agitations tropicales", "L'Impératrice", "L Imperatrice Agitations tropicales", 112, 58, "Airy synth fade", "groove://midnight_lofi_96"),
            CuratedTrackSuggestion("Bonobo - Kerala", "Bonobo", "Bonobo Kerala official", 110, 56, "Organic beat outro", "groove://midnight_lofi_96"),
            CuratedTrackSuggestion("FKJ & Masego - Tadow", "FKJ", "FKJ Masego Tadow", 96, 48, "Velvet lounge closer", "groove://midnight_lofi_96")
        )
    )

    private val PeakWorkoutProfile = VibeProfile(
        playlistTitle = "Peak Energy Cardio Mix",
        vibeTag = "High Energy",
        tracks = listOf(
            CuratedTrackSuggestion("Fred again.. & Baby Keem - leavemealone", "Fred again..", "Fred again leavemealone", 132, 94, "Tight slam transition", "groove://neon_techno_128"),
            CuratedTrackSuggestion("Fisher - Losing It", "FISHER", "FISHER Losing It official", 125, 92, "Steady four-on-the-floor", "groove://neon_techno_128"),
            CuratedTrackSuggestion("Dom Dolla - Rhyme Dust", "Dom Dolla", "Dom Dolla Rhyme Dust", 128, 95, "Tech house bass switch", "groove://neon_techno_128"),
            CuratedTrackSuggestion("Peggy Gou - (It Goes Like) Nanana", "Peggy Gou", "Peggy Gou Nanana official", 130, 90, "Euphoric 90s house blend", "groove://afro_pulse_122"),
            CuratedTrackSuggestion("Calvin Harris & Ellie Goulding - Miracle", "Calvin Harris", "Calvin Harris Miracle", 134, 96, "Trance build-up crossfade", "groove://neon_techno_128"),
            CuratedTrackSuggestion("Skrillex, Fred again.. - Rumble", "Skrillex", "Skrillex Fred again Rumble", 135, 98, "Heavy drop transition", "groove://neon_techno_128"),
            CuratedTrackSuggestion("James Hype - Ferrari", "James Hype", "James Hype Ferrari official", 126, 91, "Fast club cut", "groove://afro_pulse_122"),
            CuratedTrackSuggestion("Tiësto - The Business", "Tiësto", "Tiesto The Business", 120, 86, "Cool-down outro", "groove://sunset_deep_116")
        )
    )

    private val RetroPartyProfile = VibeProfile(
        playlistTitle = "90s & 00s Club Anthems",
        vibeTag = "Retro Party",
        tracks = listOf(
            CuratedTrackSuggestion("Modjo - Lady (Hear Me Tonight)", "Modjo", "Modjo Lady Hear Me Tonight", 126, 86, "Classic French house groove", "groove://afro_pulse_122"),
            CuratedTrackSuggestion("Stardust - Music Sounds Better With You", "Stardust", "Stardust Music Sounds Better With You", 124, 88, "Guitar loop blend", "groove://afro_pulse_122"),
            CuratedTrackSuggestion("Daft Punk - One More Time", "Daft Punk", "Daft Punk One More Time", 123, 92, "Beatmatched disco kick", "groove://neon_techno_128"),
            CuratedTrackSuggestion("Bob Sinclar - Love Generation", "Bob Sinclar", "Bob Sinclar Love Generation", 126, 87, "Summer club blend", "groove://afro_pulse_122"),
            CuratedTrackSuggestion("Eric Prydz - Call On Me", "Eric Prydz", "Eric Prydz Call On Me", 126, 90, "Filter sweep blend", "groove://neon_techno_128"),
            CuratedTrackSuggestion("Moloko - Sing It Back", "Moloko", "Moloko Sing It Back remix", 123, 84, "Vocal hook overlap", "groove://sunset_deep_116"),
            CuratedTrackSuggestion("Armand Van Helden - My My My", "Armand Van Helden", "Armand Van Helden My My My", 127, 89, "Peak dance energy", "groove://neon_techno_128"),
            CuratedTrackSuggestion("Kylie Minogue - Can't Get You Out Of My Head", "Kylie Minogue", "Kylie Minogue Cant Get You Out Of My Head", 126, 85, "Pop-house outro", "groove://sunset_deep_116")
        )
    )
}
