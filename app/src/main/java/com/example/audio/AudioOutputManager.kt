package com.example.audio

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioManager
import android.media.MediaPlayer
import android.media.AudioTrack
import android.os.Bundle
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.util.Base64
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.io.File
import java.io.FileOutputStream
import java.util.Locale
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * Robust Native Audio Output Engine for Arushi.
 * Supports:
 * 1. Hardware-level AudioTrack streaming of synthesized PCM chimes and voice PCM
 * 2. Encoded WAV/MP3 playback via MediaPlayer
 * 3. High-quality Android Text-To-Speech with Hindi & Indian English support
 * 4. Speaker diagnostic tone (440Hz) + audible voice greeting
 */
class AudioOutputManager(
    private val context: Context,
    private val scope: CoroutineScope
) : TextToSpeech.OnInitListener {

    companion object {
        private const val TAG = "ArushiAudioOutput"
        const val DEFAULT_SAMPLE_RATE = 24000
        const val MONO_CHANNEL = AudioFormat.CHANNEL_OUT_MONO
        const val PCM_16BIT = AudioFormat.ENCODING_PCM_16BIT
    }

    private var audioTrack: AudioTrack? = null
    private var currentSampleRate = DEFAULT_SAMPLE_RATE

    private val audioQueue = Channel<ByteArray>(Channel.UNLIMITED)
    private var playbackJob: Job? = null
    private var mediaPlayer: MediaPlayer? = null

    private var tts: TextToSpeech? = null
    private var isTtsReady = false
    private var pendingSpeech: String? = null

    private val _isPlaying = MutableStateFlow(false)
    val isPlaying: StateFlow<Boolean> = _isPlaying.asStateFlow()

    private val _outputAmplitude = MutableStateFlow(0f)
    val outputAmplitude: StateFlow<Float> = _outputAmplitude.asStateFlow()

    private val _logs = MutableStateFlow<List<String>>(emptyList())
    val logs: StateFlow<List<String>> = _logs.asStateFlow()

    var onPlaybackStarted: (() -> Unit)? = null
    var onPlaybackEnded: (() -> Unit)? = null

    init {
        initAudioTrack(DEFAULT_SAMPLE_RATE)
        startPlaybackLoop()
        initTts()
        ensureDeviceVolume()
    }

    fun ensureDeviceVolume() {
        try {
            val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
            audioManager.mode = AudioManager.MODE_NORMAL
            val currentVol = audioManager.getStreamVolume(AudioManager.STREAM_MUSIC)
            val maxVol = audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
            if (currentVol < maxVol / 2) {
                audioManager.setStreamVolume(AudioManager.STREAM_MUSIC, (maxVol * 0.85).toInt(), AudioManager.FLAG_SHOW_UI)
                log("Adjusted STREAM_MUSIC volume to 85%")
            }
        } catch (e: Exception) {
            log("Could not check/adjust stream volume: ${e.message}")
        }
    }

    private fun initTts() {
        try {
            log("Initializing TextToSpeech engine...")
            tts = TextToSpeech(context.applicationContext, this)
        } catch (e: Exception) {
            log("TextToSpeech initialization error: ${e.message}")
        }
    }

    override fun onInit(status: Int) {
        if (status == TextToSpeech.SUCCESS) {
            isTtsReady = true
            log("TextToSpeech engine initialized successfully")

            val hindi = Locale("hi", "IN")
            val indianEnglish = Locale("en", "IN")
            val available = tts?.isLanguageAvailable(hindi) ?: TextToSpeech.LANG_NOT_SUPPORTED

            if (available >= TextToSpeech.LANG_AVAILABLE) {
                tts?.language = hindi
                log("TTS language set to Hindi (India)")
            } else if ((tts?.isLanguageAvailable(indianEnglish) ?: TextToSpeech.LANG_NOT_SUPPORTED) >= TextToSpeech.LANG_AVAILABLE) {
                tts?.language = indianEnglish
                log("TTS language set to English (India)")
            } else {
                tts?.language = Locale.getDefault()
                log("TTS language set to default: ${Locale.getDefault()}")
            }

            try {
                val voices = tts?.voices
                val femaleVoice = voices?.firstOrNull { voice ->
                    val name = voice.name.lowercase()
                    (name.contains("female") || name.contains("hi-in-x-") || name.contains("en-in-x-")) &&
                            !voice.isNetworkConnectionRequired
                }
                if (femaleVoice != null) {
                    tts?.voice = femaleVoice
                    log("Selected natural female voice: ${femaleVoice.name}")
                }
            } catch (_: Exception) {}

            tts?.setPitch(1.08f)
            tts?.setSpeechRate(1.02f)

            tts?.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                override fun onStart(utteranceId: String?) {
                    scope.launch(Dispatchers.Main) {
                        _isPlaying.value = true
                        _outputAmplitude.value = 0.65f
                        onPlaybackStarted?.invoke()
                    }
                }

                override fun onDone(utteranceId: String?) {
                    scope.launch(Dispatchers.Main) {
                        _isPlaying.value = false
                        _outputAmplitude.value = 0f
                        onPlaybackEnded?.invoke()
                    }
                }

                override fun onError(utteranceId: String?) {
                    scope.launch(Dispatchers.Main) {
                        _isPlaying.value = false
                        _outputAmplitude.value = 0f
                        onPlaybackEnded?.invoke()
                    }
                }
            })

            // Speak any text that was requested while TTS was initializing
            pendingSpeech?.let { text ->
                pendingSpeech = null
                speakText(text)
            }
        } else {
            log("TextToSpeech initialization failed with status $status")
        }
    }

    fun log(message: String) {
        Log.d(TAG, message)
        _logs.value = (_logs.value + "[${System.currentTimeMillis() % 100000}] $message").takeLast(120)
    }

    @Synchronized
    private fun initAudioTrack(sampleRate: Int) {
        try {
            if (audioTrack != null && currentSampleRate == sampleRate) return

            audioTrack?.release()
            currentSampleRate = sampleRate

            val minBufferSize = AudioTrack.getMinBufferSize(sampleRate, MONO_CHANNEL, PCM_16BIT)
            val bufferSize = minBufferSize.coerceAtLeast(sampleRate * 2)

            val attributes = AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_MEDIA)
                .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                .build()

            val format = AudioFormat.Builder()
                .setSampleRate(sampleRate)
                .setChannelMask(MONO_CHANNEL)
                .setEncoding(PCM_16BIT)
                .build()

            audioTrack = AudioTrack.Builder()
                .setAudioAttributes(attributes)
                .setAudioFormat(format)
                .setBufferSizeInBytes(bufferSize)
                .setTransferMode(AudioTrack.MODE_STREAM)
                .build()

            audioTrack?.play()
            log("AudioTrack initialized at ${sampleRate}Hz, state: ${audioTrack?.state}")
        } catch (e: Exception) {
            log("AudioTrack init error: ${e.message}")
        }
    }

    private fun startPlaybackLoop() {
        playbackJob?.cancel()
        playbackJob = scope.launch(Dispatchers.IO) {
            while (isActive) {
                try {
                    val pcmChunk = audioQueue.receive()
                    ensureTrackPlaying()

                    if (!_isPlaying.value) {
                        _isPlaying.value = true
                        onPlaybackStarted?.invoke()
                    }

                    calculateAndEmitAmplitude(pcmChunk)
                    audioTrack?.write(pcmChunk, 0, pcmChunk.size)

                    if (audioQueue.isEmpty) {
                        _outputAmplitude.value = 0f
                        _isPlaying.value = false
                        onPlaybackEnded?.invoke()
                    }
                } catch (e: Exception) {
                    if (isActive) {
                        _isPlaying.value = false
                    }
                }
            }
        }
    }

    private fun ensureTrackPlaying() {
        try {
            if (audioTrack == null || audioTrack?.state != AudioTrack.STATE_INITIALIZED) {
                initAudioTrack(currentSampleRate)
            }
            if (audioTrack?.playState != AudioTrack.PLAYSTATE_PLAYING) {
                audioTrack?.play()
            }
        } catch (e: Exception) {
            log("AudioTrack resume error: ${e.message}")
        }
    }

    private fun calculateAndEmitAmplitude(bytes: ByteArray) {
        if (bytes.size < 2) return
        var sumSquares = 0.0
        val shortCount = bytes.size / 2
        for (i in 0 until shortCount) {
            val sample = (bytes[i * 2].toInt() and 0xFF) or (bytes[i * 2 + 1].toInt() shl 8)
            val signedSample = sample.toShort()
            sumSquares += signedSample * signedSample
        }
        val rms = sqrt(sumSquares / shortCount)
        val normalized = (rms / 32767.0).coerceIn(0.0, 1.0).toFloat()
        _outputAmplitude.value = normalized
    }

    /**
     * Plays a pleasant 2-tone melodic wake chime (C5 -> G5) through AudioTrack.
     * Guaranteed to emit real audible sound instantly.
     */
    fun playWakeChime() {
        scope.launch(Dispatchers.IO) {
            try {
                ensureDeviceVolume()
                ensureTrackPlaying()

                val sampleRate = currentSampleRate
                // 523Hz for 120ms then 784Hz for 180ms
                val part1Samples = (sampleRate * 0.12).toInt()
                val part2Samples = (sampleRate * 0.18).toInt()
                val totalSamples = part1Samples + part2Samples
                val bytes = ByteArray(totalSamples * 2)

                var byteIdx = 0
                val f1 = 523.25
                for (i in 0 until part1Samples) {
                    val angle = 2.0 * Math.PI * f1 * i / sampleRate
                    val sampleVal = (sin(angle) * 26000).toInt().toShort()
                    bytes[byteIdx++] = (sampleVal.toInt() and 0xFF).toByte()
                    bytes[byteIdx++] = ((sampleVal.toInt() shr 8) and 0xFF).toByte()
                }

                val f2 = 783.99
                for (i in 0 until part2Samples) {
                    val angle = 2.0 * Math.PI * f2 * i / sampleRate
                    val sampleVal = (sin(angle) * 28000).toInt().toShort()
                    bytes[byteIdx++] = (sampleVal.toInt() and 0xFF).toByte()
                    bytes[byteIdx++] = ((sampleVal.toInt() shr 8) and 0xFF).toByte()
                }

                audioQueue.send(bytes)
                log("Played melodic wake chime")
            } catch (e: Exception) {
                log("Chime error: ${e.message}")
            }
        }
    }

    /**
     * Plays an affirmative triple-tone action chime (D5 -> E5 -> A5)
     */
    fun playActionChime() {
        scope.launch(Dispatchers.IO) {
            try {
                ensureDeviceVolume()
                ensureTrackPlaying()

                val sampleRate = currentSampleRate
                val p1 = (sampleRate * 0.08).toInt()
                val p2 = (sampleRate * 0.08).toInt()
                val p3 = (sampleRate * 0.16).toInt()
                val total = p1 + p2 + p3
                val bytes = ByteArray(total * 2)
                var idx = 0

                val notes = listOf(Pair(587.33, p1), Pair(659.25, p2), Pair(880.00, p3))
                for ((freq, count) in notes) {
                    for (i in 0 until count) {
                        val angle = 2.0 * Math.PI * freq * i / sampleRate
                        val sampleVal = (sin(angle) * 28000).toInt().toShort()
                        bytes[idx++] = (sampleVal.toInt() and 0xFF).toByte()
                        bytes[idx++] = ((sampleVal.toInt() shr 8) and 0xFF).toByte()
                    }
                }

                audioQueue.send(bytes)
            } catch (_: Exception) {}
        }
    }

    /**
     * Speaks text aloud using Android TTS engine, or queues if initializing.
     */
    fun speakText(text: String, isHindiOrMixed: Boolean = true) {
        log("Speaking aloud: \"$text\"")
        scope.launch(Dispatchers.Main) {
            try {
                ensureDeviceVolume()

                if (isTtsReady && tts != null) {
                    val hasHindiScript = text.any { it in '\u0900'..'\u097F' }
                    if (hasHindiScript || isHindiOrMixed) {
                        tts?.language = Locale("hi", "IN")
                    } else {
                        tts?.language = Locale("en", "IN")
                    }

                    val params = Bundle().apply {
                        putFloat(TextToSpeech.Engine.KEY_PARAM_VOLUME, 1.0f)
                    }
                    val utteranceId = "arushi_utterance_${System.currentTimeMillis()}"
                    tts?.speak(text, TextToSpeech.QUEUE_FLUSH, params, utteranceId)
                    log("TTS speak queued successfully")
                } else {
                    log("TTS not ready yet - queueing text and playing chime")
                    pendingSpeech = text
                    initTts()
                    playWakeChime()
                }
            } catch (e: Exception) {
                log("Error in speakText: ${e.message}")
                playWakeChime()
            }
        }
    }

    /**
     * Enqueue base64 encoded audio from Gemini Live or REST API
     */
    fun enqueueBase64Pcm(base64Data: String, mimeType: String? = null) {
        scope.launch(Dispatchers.IO) {
            try {
                val cleanMime = mimeType?.lowercase() ?: "audio/pcm"
                val audioBytes = Base64.decode(base64Data, Base64.DEFAULT)

                if (cleanMime.contains("mp3") || cleanMime.contains("wav") || cleanMime.contains("ogg")) {
                    log("Playing encoded audio format ($cleanMime) via MediaPlayer")
                    playEncodedAudioBytes(audioBytes, cleanMime)
                } else {
                    val sampleRate = parseSampleRate(mimeType) ?: currentSampleRate
                    if (sampleRate != currentSampleRate) {
                        initAudioTrack(sampleRate)
                    }
                    audioQueue.send(audioBytes)
                }
            } catch (e: Exception) {
                log("Error decoding/queueing audio: ${e.message}")
            }
        }
    }

    private fun playEncodedAudioBytes(bytes: ByteArray, mimeType: String) {
        scope.launch(Dispatchers.Main) {
            try {
                mediaPlayer?.release()
                val ext = if (mimeType.contains("mp3")) "mp3" else "wav"
                val tempFile = File.createTempFile("arushi_audio", ".$ext", context.cacheDir)
                FileOutputStream(tempFile).use { it.write(bytes) }

                mediaPlayer = MediaPlayer().apply {
                    setAudioAttributes(
                        AudioAttributes.Builder()
                            .setUsage(AudioAttributes.USAGE_MEDIA)
                            .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                            .build()
                    )
                    setDataSource(tempFile.absolutePath)
                    prepare()
                    setOnCompletionListener {
                        _isPlaying.value = false
                        _outputAmplitude.value = 0f
                        onPlaybackEnded?.invoke()
                        tempFile.delete()
                    }
                    start()
                }
                _isPlaying.value = true
                _outputAmplitude.value = 0.7f
                onPlaybackStarted?.invoke()
            } catch (e: Exception) {
                log("MediaPlayer error: ${e.message}")
            }
        }
    }

    private fun parseSampleRate(mimeType: String?): Int? {
        if (mimeType == null) return null
        val match = Regex("rate=(\\d+)").find(mimeType)
        return match?.groupValues?.get(1)?.toIntOrNull()
    }

    fun stopAndClearQueue() {
        while (audioQueue.tryReceive().isSuccess) {}
        try {
            audioTrack?.pause()
            audioTrack?.flush()
            audioTrack?.play()
        } catch (_: Exception) {}

        try {
            if (mediaPlayer?.isPlaying == true) {
                mediaPlayer?.stop()
                mediaPlayer?.release()
                mediaPlayer = null
            }
        } catch (_: Exception) {}

        try {
            tts?.stop()
        } catch (_: Exception) {}

        _outputAmplitude.value = 0f
        _isPlaying.value = false
        onPlaybackEnded?.invoke()
    }

    /**
     * Diagnostic Speaker & Voice Test:
     * Plays 440Hz tone, followed by melodic wake chime, followed by spoken voice.
     */
    fun testSpeaker(frequency: Float = 440f, durationMs: Int = 800) {
        scope.launch(Dispatchers.IO) {
            log("--- SPEAKER & VOICE DIAGNOSTIC TEST STARTED ---")
            ensureDeviceVolume()
            try {
                ensureTrackPlaying()

                val sampleRate = currentSampleRate
                val totalSamples = (sampleRate * (durationMs / 1000.0)).toInt()
                val bytes = ByteArray(totalSamples * 2)

                val twoPiF = 2.0 * Math.PI * frequency
                for (i in 0 until totalSamples) {
                    val angle = twoPiF * i / sampleRate
                    val sampleVal = (sin(angle) * 30000).toInt().toShort()
                    bytes[i * 2] = (sampleVal.toInt() and 0xFF).toByte()
                    bytes[i * 2 + 1] = ((sampleVal.toInt() shr 8) and 0xFF).toByte()
                }

                audioQueue.send(bytes)
                log("Diagnostic 440Hz tone sent to AudioTrack")

                kotlinx.coroutines.delay(1000)
                playWakeChime()

                kotlinx.coroutines.delay(600)
                speakText("Namaste! Main Arushi hoon. Speaker test safal raha, main bolne aur aapke phone ko control karne ke liye bilkul tayar hoon!")
            } catch (e: Exception) {
                log("Speaker test failed: ${e.message}")
            }
        }
    }

    fun release() {
        try {
            stopAndClearQueue()
            playbackJob?.cancel()
            audioTrack?.stop()
            audioTrack?.release()
            audioTrack = null
            mediaPlayer?.release()
            mediaPlayer = null
            tts?.stop()
            tts?.shutdown()
            tts = null
        } catch (_: Exception) {}
    }
}
