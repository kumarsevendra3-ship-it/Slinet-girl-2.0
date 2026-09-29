package com.example

import org.junit.Assert.*
import org.junit.Test
import kotlin.math.sin

class ExampleUnitTest {
    @Test
    fun addition_isCorrect() {
        assertEquals(4, 2 + 2)
    }

    @Test
    fun pcm16BitCalculation_isValid() {
        val sampleRate = 24000
        val frequency = 440.0
        val durationSamples = 240
        val bytes = ByteArray(durationSamples * 2)

        val twoPiF = 2.0 * Math.PI * frequency
        for (i in 0 until durationSamples) {
            val angle = twoPiF * i / sampleRate
            val sampleVal = (sin(angle) * 28000).toInt().toShort()
            bytes[i * 2] = (sampleVal.toInt() and 0xFF).toByte()
            bytes[i * 2 + 1] = ((sampleVal.toInt() shr 8) and 0xFF).toByte()
        }

        assertTrue(bytes.isNotEmpty())
        assertEquals(480, bytes.size)
    }
}
