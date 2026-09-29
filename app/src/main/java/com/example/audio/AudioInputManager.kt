package com.example.audio

import android.annotation.SuppressLint
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.util.Base64
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlin.math.sqrt

/**
 * Manages microphone capture using [AudioRecord].
 * Captures 16kHz 16-bit mono PCM chunks and emits them to Gemini Live.
 */
class AudioInputManager(
    private val scope: CoroutineScope,
    private val onAudioChunkAvailable: (base64Chunk: String) -> Unit
) {

    companion object {
        private const val TAG = "ArushiAudioInput"
        const val SAMPLE_RATE = 16000
        const val CHANNEL_CONFIG = AudioFormat.CHANNEL_IN_MONO
        const val AUDIO_FORMAT = AudioFormat.ENCODING_PCM_16BIT
        // ~100ms chunk: 16000 samples/sec * 0.1s * 2 bytes = 3200 bytes
        const val CHUNK_SIZE_BYTES = 3200
    }

    private var audioRecord: AudioRecord? = null
    private var recordingJob: Job? = null

    private val _isRecording = MutableStateFlow(false)
    val isRecording: StateFlow<Boolean> = _isRecording.asStateFlow()

    private val _micAmplitude = MutableStateFlow(0f)
    val micAmplitude: StateFlow<Float> = _micAmplitude.asStateFlow()

    var onUserSpeakingDetected: (() -> Unit)? = null

    @SuppressLint("MissingPermission")
    fun startRecording(): Boolean {
        if (_isRecording.value) return true

        val minBufSize = AudioRecord.getMinBufferSize(SAMPLE_RATE, CHANNEL_CONFIG, AUDIO_FORMAT)
        if (minBufSize <= 0) {
            Log.e(TAG, "AudioRecord minBufferSize invalid: $minBufSize")
            return false
        }

        val bufferSize = minBufSize.coerceAtLeast(CHUNK_SIZE_BYTES * 2)

        try {
            audioRecord = AudioRecord(
                MediaRecorder.AudioSource.VOICE_RECOGNITION,
                SAMPLE_RATE,
                CHANNEL_CONFIG,
                AUDIO_FORMAT,
                bufferSize
            )

            if (audioRecord?.state != AudioRecord.STATE_INITIALIZED) {
                Log.e(TAG, "AudioRecord failed to initialize")
                audioRecord?.release()
                audioRecord = null
                return false
            }

            audioRecord?.startRecording()
            _isRecording.value = true
            Log.d(TAG, "Microphone started (16kHz, 16-bit PCM Mono)")

            startCaptureLoop()
            return true
        } catch (e: SecurityException) {
            Log.e(TAG, "Permission denied for AudioRecord: ${e.message}")
            return false
        } catch (e: Exception) {
            Log.e(TAG, "Error starting AudioRecord: ${e.message}")
            return false
        }
    }

    private fun startCaptureLoop() {
        recordingJob?.cancel()
        recordingJob = scope.launch(Dispatchers.IO) {
            val buffer = ByteArray(CHUNK_SIZE_BYTES)
            var consecutiveVoiceFrames = 0

            while (isActive && _isRecording.value) {
                val record = audioRecord ?: break
                val bytesRead = record.read(buffer, 0, buffer.size)

                if (bytesRead > 0) {
                    // Compute amplitude
                    val amp = calculateAmplitude(buffer, bytesRead)
                    _micAmplitude.value = amp

                    // Voice activity check (threshold ~ 0.08)
                    if (amp > 0.08f) {
                        consecutiveVoiceFrames++
                        if (consecutiveVoiceFrames >= 2) {
                            onUserSpeakingDetected?.invoke()
                        }
                    } else {
                        consecutiveVoiceFrames = 0
                    }

                    // Base64 encode PCM and send to Gemini Live
                    val chunkToSend = if (bytesRead == buffer.size) buffer else buffer.copyOf(bytesRead)
                    val base64 = Base64.encodeToString(chunkToSend, Base64.NO_WRAP)
                    onAudioChunkAvailable(base64)
                }
            }
        }
    }

    private fun calculateAmplitude(bytes: ByteArray, length: Int): Float {
        if (length < 2) return 0f
        var sumSquares = 0.0
        val shortCount = length / 2
        for (i in 0 until shortCount) {
            val sample = (bytes[i * 2].toInt() and 0xFF) or (bytes[i * 2 + 1].toInt() shl 8)
            val signedSample = sample.toShort()
            sumSquares += signedSample * signedSample
        }
        val rms = sqrt(sumSquares / shortCount)
        return (rms / 32767.0).coerceIn(0.0, 1.0).toFloat()
    }

    fun stopRecording() {
        if (!_isRecording.value) return
        _isRecording.value = false
        _micAmplitude.value = 0f
        recordingJob?.cancel()
        recordingJob = null
        try {
            audioRecord?.stop()
            audioRecord?.release()
            audioRecord = null
            Log.d(TAG, "Microphone stopped")
        } catch (e: Exception) {
            Log.e(TAG, "Error stopping AudioRecord: ${e.message}")
        }
    }
}
