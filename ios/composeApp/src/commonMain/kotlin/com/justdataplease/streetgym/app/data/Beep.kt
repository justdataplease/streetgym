package com.justdataplease.streetgym.app.data

import kotlin.math.PI
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sin

/**
 * The beep at the end of a rest and of a timed set, as Android's ToneGenerator plays it (TONE_PROP_BEEP: 400 Hz and
 * 1200 Hz together for 35 ms), written as a WAV file the iPhone plays and gives its notifications. Made here rather than
 * kept as a sound file, so the app holds no copy of anything.
 */
object Beep {
    const val SAMPLE_RATE = 44_100
    const val MILLIS = 35
    private val TONES = doubleArrayOf(400.0, 1_200.0)
    /** Each tone's share of full scale: together they peak just under it. */
    private const val LEVEL = 0.45
    /** A few milliseconds in and out, so the beep doesn't click. */
    private const val RAMP_MILLIS = 3

    /** 16-bit mono PCM in a RIFF WAVE file. */
    fun wav(): ByteArray {
        val samples = SAMPLE_RATE * MILLIS / 1_000
        val ramp = (SAMPLE_RATE * RAMP_MILLIS / 1_000).toDouble()
        val dataSize = samples * 2
        val out = ByteArray(HEADER + dataSize)
        var at = 0
        fun text(value: String) { value.forEach { out[at++] = it.code.toByte() } }
        fun int32(value: Int) { repeat(4) { out[at++] = (value shr (8 * it) and 0xFF).toByte() } }
        fun int16(value: Int) { repeat(2) { out[at++] = (value shr (8 * it) and 0xFF).toByte() } }
        text("RIFF"); int32(HEADER - 8 + dataSize); text("WAVE")
        text("fmt "); int32(16); int16(1); int16(1); int32(SAMPLE_RATE); int32(SAMPLE_RATE * 2); int16(2); int16(16)
        text("data"); int32(dataSize)
        for (index in 0 until samples) {
            val time = index.toDouble() / SAMPLE_RATE
            val envelope = min(1.0, min(index / ramp, (samples - 1 - index) / ramp))
            val value = TONES.sumOf { sin(2 * PI * it * time) } * LEVEL * envelope
            int16((value * Short.MAX_VALUE).roundToInt())
        }
        return out
    }

    private const val HEADER = 44
}
