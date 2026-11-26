package com.example.audio

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.PI
import kotlin.math.exp
import kotlin.math.pow
import kotlin.math.sin

/**
 * Generates clean, rhythmic electronic groove WAV stems in cacheDir so built-in tracks
 * and offline fallbacks play real beat-matched audio through MediaPlayer with true crossfading.
 */
object ProceduralGrooveGenerator {

    private const val SAMPLE_RATE = 22050

    suspend fun resolveGrooveFile(context: Context, grooveUri: String): File =
        withContext(Dispatchers.IO) {
            val dir = File(context.cacheDir, "grooves").apply { mkdirs() }
            val key = grooveUri.removePrefix("groove://").replace(Regex("[^a-zA-Z0-9_]"), "_")
            val target = File(dir, "$key.wav")
            if (target.exists() && target.length() > 44) {
                return@withContext target
            }

            val spec = when {
                key.contains("afro") -> GrooveSpec(bpm = 122, rootHz = 55.0, chordHz = listOf(220.0, 261.63, 329.63, 392.0), syncopated = true)
                key.contains("techno") -> GrooveSpec(bpm = 128, rootHz = 49.0, chordHz = listOf(196.0, 233.08, 293.66, 392.0), syncopated = false)
                key.contains("lofi") -> GrooveSpec(bpm = 96, rootHz = 65.41, chordHz = listOf(261.63, 311.13, 392.0, 466.16), syncopated = true)
                else -> GrooveSpec(bpm = 116, rootHz = 58.27, chordHz = listOf(233.08, 277.18, 349.23, 415.30), syncopated = false)
            }

            writeWavGroove(target, spec, durationSec = 36)
            target
        }

    private data class GrooveSpec(
        val bpm: Int,
        val rootHz: Double,
        val chordHz: List<Double>,
        val syncopated: Boolean
    )

    private fun writeWavGroove(file: File, spec: GrooveSpec, durationSec: Int) {
        val totalSamples = SAMPLE_RATE * durationSec
        val pcmBytes = ByteArray(totalSamples * 2)
        val buffer = ByteBuffer.wrap(pcmBytes).order(ByteOrder.LITTLE_ENDIAN)

        val beatDuration = 60.0 / spec.bpm
        val sixteenthDuration = beatDuration / 4.0

        // Pseudo-random deterministic noise for hi-hat
        var noiseSeed = 1337L

        for (i in 0 until totalSamples) {
            val t = i.toDouble() / SAMPLE_RATE
            val beatPos = (t % beatDuration) / beatDuration
            val barPos = (t % (beatDuration * 4.0)) / (beatDuration * 4.0)
            val sixteenthIndex = ((t / sixteenthDuration).toInt()) % 16
            val timeInSixteenth = t % sixteenthDuration

            // 1. Punchy electronic kick on every quarter beat
            val kickEnv = exp(-beatPos * 14.0)
            val kickFreq = 45.0 + 115.0 * exp(-beatPos * 28.0)
            val kick = sin(2.0 * PI * kickFreq * (beatPos * beatDuration)) * kickEnv * 0.48

            // 2. Crisp off-beat / 16th hi-hat
            noiseSeed = (noiseSeed * 1664525L + 1013904223L) and 0xFFFFFFFFL
            val whiteNoise = ((noiseSeed ushr 16).toInt() / 32768.0) - 1.0
            val hatAccent = when {
                sixteenthIndex % 4 == 2 -> 1.0
                spec.syncopated && (sixteenthIndex == 3 || sixteenthIndex == 11) -> 0.65
                else -> 0.18
            }
            val hatEnv = exp(-(timeInSixteenth / sixteenthDuration) * 10.0)
            val hiHat = whiteNoise * hatEnv * hatAccent * 0.12

            // 3. Rolling bassline
            val bassPattern = if (spec.syncopated) {
                if (sixteenthIndex in listOf(0, 3, 6, 8, 11, 14)) 1.0 else 0.35
            } else {
                if (sixteenthIndex % 2 == 1) 1.0 else 0.45
            }
            val bassStepMult = when (sixteenthIndex / 4) {
                2 -> 1.122 // whole step up
                3 -> 1.189 // minor third
                else -> 1.0
            }
            val bassFreq = spec.rootHz * bassStepMult
            val bassEnv = exp(-(timeInSixteenth / sixteenthDuration) * 4.0)
            val bass = (sin(2.0 * PI * bassFreq * t) * 0.7 +
                sin(4.0 * PI * bassFreq * t) * 0.25) * bassEnv * bassPattern * 0.28

            // 4. Warm atmospheric synth pad + arpeggio
            val arpNote = spec.chordHz[(sixteenthIndex / 2) % spec.chordHz.size]
            val padPulse = 0.5 + 0.5 * sin(2.0 * PI * barPos)
            val pad = (sin(2.0 * PI * arpNote * t) * 0.55 +
                sin(2.0 * PI * (spec.chordHz[0] * 0.5) * t) * 0.45) * (0.14 + 0.08 * padPulse)

            // Smooth fade-in/out at extreme edges (0.25s) to prevent clicks
            val edgeEnv = when {
                t < 0.25 -> (t / 0.25)
                t > durationSec - 0.25 -> ((durationSec - t) / 0.25).coerceAtLeast(0.0)
                else -> 1.0
            }

            val mixed = ((kick + hiHat + bass + pad) * edgeEnv).coerceIn(-0.98, 0.98)
            val sample16 = (mixed * 32767.0).toInt().toShort()
            buffer.putShort(sample16)
        }

        FileOutputStream(file).use { out ->
            out.write(buildWavHeader(pcmBytes.size))
            out.write(pcmBytes)
        }
    }

    private fun buildWavHeader(pcmByteLength: Int): ByteArray {
        val totalDataLen = pcmByteLength + 36
        val byteRate = SAMPLE_RATE * 2
        val header = ByteBuffer.allocate(44).order(ByteOrder.LITTLE_ENDIAN)
        header.put("RIFF".toByteArray(Charsets.US_ASCII))
        header.putInt(totalDataLen)
        header.put("WAVE".toByteArray(Charsets.US_ASCII))
        header.put("fmt ".toByteArray(Charsets.US_ASCII))
        header.putInt(16) // PCM chunk size
        header.putShort(1.toShort()) // AudioFormat = PCM
        header.putShort(1.toShort()) // NumChannels = 1
        header.putInt(SAMPLE_RATE)
        header.putInt(byteRate)
        header.putShort(2.toShort()) // BlockAlign
        header.putShort(16.toShort()) // BitsPerSample
        header.put("data".toByteArray(Charsets.US_ASCII))
        header.putInt(pcmByteLength)
        return header.array()
    }
}
