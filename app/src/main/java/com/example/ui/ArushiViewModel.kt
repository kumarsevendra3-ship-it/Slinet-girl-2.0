package com.example.ui

import android.app.Application
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.BuildConfig
import com.example.audio.AudioInputManager
import com.example.audio.AudioOutputManager
import com.example.bridge.ContactMatch
import com.example.bridge.DeviceActionBridge
import com.example.live.GeminiLiveSession
import com.example.live.GeminiRestEngine
import com.example.live.LiveState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.util.Locale

enum class AssistantState {
    IDLE,
    CONNECTING,
    LISTENING,
    SPEAKING,
    ERROR
}

class ArushiViewModel(application: Application) : AndroidViewModel(application) {

    companion object {
        private const val TAG = "ArushiViewModel"
        private const val PREF_KEY = "arushi_custom_gemini_key"
    }

    private val prefs = application.getSharedPreferences("arushi_prefs", Context.MODE_PRIVATE)

    val audioOutputManager = AudioOutputManager(application.applicationContext, viewModelScope)
    val deviceActionBridge = DeviceActionBridge(application.applicationContext)

    private val _assistantState = MutableStateFlow(AssistantState.IDLE)
    val assistantState: StateFlow<AssistantState> = _assistantState.asStateFlow()

    private val _logs = MutableStateFlow<List<String>>(emptyList())
    val logs: StateFlow<List<String>> = _logs.asStateFlow()

    private val _transcript = MutableStateFlow("")
    val transcript: StateFlow<String> = _transcript.asStateFlow()

    private val _userSpokenText = MutableStateFlow("")
    val userSpokenText: StateFlow<String> = _userSpokenText.asStateFlow()

    private val _lastActionNotice = MutableStateFlow<String?>(null)
    val lastActionNotice: StateFlow<String?> = _lastActionNotice.asStateFlow()

    private val _contactChoices = MutableStateFlow<List<ContactMatch>>(emptyList())
    val contactChoices: StateFlow<List<ContactMatch>> = _contactChoices.asStateFlow()

    private val _currentApiKey = MutableStateFlow(getSavedOrBuildConfigKey())
    val currentApiKey: StateFlow<String> = _currentApiKey.asStateFlow()

    private var speechRecognizer: SpeechRecognizer? = null
    private var isListeningToSpeech = false

    val isPlayingAudio = audioOutputManager.isPlaying
    val outputAmplitude = audioOutputManager.outputAmplitude

    private val audioInputManager = AudioInputManager(viewModelScope) { base64Chunk ->
        liveSession?.sendAudioChunk(base64Chunk)
    }
    val micAmplitude = audioInputManager.micAmplitude

    private var liveSession: GeminiLiveSession? = null
    private var restEngine: GeminiRestEngine? = null

    init {
        log("Arushi Assistant initialized. Engine ready.")

        audioOutputManager.onPlaybackStarted = {
            _assistantState.value = AssistantState.SPEAKING
            log("State: SPEAKING")
        }

        audioOutputManager.onPlaybackEnded = {
            if (_assistantState.value == AssistantState.SPEAKING) {
                _assistantState.value = AssistantState.LISTENING
                log("State: LISTENING")
                if (isListeningToSpeech) {
                    startListeningForSpeech()
                }
            }
        }

        audioInputManager.onUserSpeakingDetected = {
            if (_assistantState.value == AssistantState.SPEAKING) {
                log("User voice detected - interrupting playback")
                audioOutputManager.stopAndClearQueue()
                _assistantState.value = AssistantState.LISTENING
            }
        }

        initEngines(_currentApiKey.value)
        initSpeechRecognizer()
    }

    private fun getSavedOrBuildConfigKey(): String {
        val saved = prefs.getString(PREF_KEY, "") ?: ""
        if (saved.isNotBlank()) return saved
        val buildKey = BuildConfig.GEMINI_API_KEY
        return if (buildKey != "MY_GEMINI_API_KEY") buildKey else ""
    }

    fun saveCustomApiKey(key: String) {
        val cleanKey = key.trim()
        prefs.edit().putString(PREF_KEY, cleanKey).apply()
        _currentApiKey.value = cleanKey
        initEngines(cleanKey)
        log("Custom API key updated")
    }

    private fun initEngines(apiKey: String) {
        liveSession?.disconnect()

        liveSession = GeminiLiveSession(
            scope = viewModelScope,
            apiKey = apiKey,
            audioOutputManager = audioOutputManager,
            deviceActionBridge = deviceActionBridge,
            onLog = { log(it) }
        ).apply {
            onInterruption = {
                audioOutputManager.stopAndClearQueue()
                _assistantState.value = AssistantState.LISTENING
            }
            onSessionError = { errMsg ->
                log("Live session message: $errMsg")
            }
        }

        restEngine = GeminiRestEngine(
            apiKey = apiKey,
            audioOutputManager = audioOutputManager,
            deviceActionBridge = deviceActionBridge,
            onLog = { log(it) }
        )

        viewModelScope.launch {
            liveSession?.lastTranscript?.collect { text ->
                if (text.isNotBlank()) {
                    _transcript.value = text
                    audioOutputManager.speakText(text)
                }
            }
        }
        viewModelScope.launch {
            liveSession?.lastActionMessage?.collect { msg ->
                if (msg != null) {
                    _lastActionNotice.value = msg
                }
            }
        }
    }

    private fun initSpeechRecognizer() {
        viewModelScope.launch(Dispatchers.Main) {
            try {
                if (SpeechRecognizer.isRecognitionAvailable(getApplication())) {
                    speechRecognizer = SpeechRecognizer.createSpeechRecognizer(getApplication()).apply {
                        setRecognitionListener(object : RecognitionListener {
                            override fun onReadyForSpeech(params: Bundle?) {
                                log("SpeechRecognizer ready")
                            }
                            override fun onBeginningOfSpeech() {}
                            override fun onRmsChanged(rmsdB: Float) {}
                            override fun onBufferReceived(buffer: ByteArray?) {}
                            override fun onEndOfSpeech() {}
                            override fun onError(error: Int) {
                                log("SpeechRecognizer code: $error")
                                if (isListeningToSpeech && _assistantState.value == AssistantState.LISTENING) {
                                    startListeningForSpeech()
                                }
                            }
                            override fun onResults(results: Bundle?) {
                                val matches = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                                val text = matches?.firstOrNull() ?: ""
                                if (text.isNotBlank()) {
                                    _userSpokenText.value = text
                                    log("Recognized speech: \"$text\"")
                                    handleUserVoicePrompt(text)
                                } else {
                                    if (isListeningToSpeech) startListeningForSpeech()
                                }
                            }
                            override fun onPartialResults(partialResults: Bundle?) {
                                val matches = partialResults?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                                val text = matches?.firstOrNull() ?: ""
                                if (text.isNotBlank()) {
                                    _userSpokenText.value = text
                                }
                            }
                            override fun onEvent(eventType: Int, params: Bundle?) {}
                        })
                    }
                    log("SpeechRecognizer initialized")
                } else {
                    log("SpeechRecognizer service not available on this system")
                }
            } catch (e: Exception) {
                log("SpeechRecognizer init error: ${e.message}")
            }
        }
    }

    fun log(msg: String) {
        Log.d(TAG, msg)
        _logs.value = (_logs.value + "[${System.currentTimeMillis() % 100000}] $msg").takeLast(120)
    }

    fun toggleAssistant(hasMicPermission: Boolean) {
        if (!hasMicPermission) {
            log("Microphone permission required")
            _assistantState.value = AssistantState.ERROR
            return
        }

        if (_assistantState.value == AssistantState.IDLE || _assistantState.value == AssistantState.ERROR) {
            startAssistant()
        } else {
            stopAssistant()
        }
    }

    private fun startAssistant() {
        log("Activating Arushi Assistant...")
        _assistantState.value = AssistantState.CONNECTING

        audioOutputManager.ensureDeviceVolume()
        audioOutputManager.playWakeChime()

        if (_currentApiKey.value.isNotBlank()) {
            liveSession?.connect()
        }

        audioInputManager.startRecording()

        _assistantState.value = AssistantState.LISTENING
        isListeningToSpeech = true

        val greeting = "Haanji! Boliye, main aapke phone ko kaise control karoon?"
        _transcript.value = greeting
        audioOutputManager.speakText(greeting)

        startListeningForSpeech()
    }

    private fun startListeningForSpeech() {
        if (!isListeningToSpeech) return
        viewModelScope.launch(Dispatchers.Main) {
            try {
                val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                    putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                    putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale.getDefault())
                    putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
                }
                speechRecognizer?.startListening(intent)
            } catch (e: Exception) {
                log("Speech listen error: ${e.message}")
            }
        }
    }

    fun stopAssistant() {
        log("Deactivating Arushi...")
        isListeningToSpeech = false
        audioInputManager.stopRecording()
        audioOutputManager.stopAndClearQueue()
        liveSession?.disconnect()
        try {
            speechRecognizer?.stopListening()
        } catch (_: Exception) {}
        _assistantState.value = AssistantState.IDLE
        _lastActionNotice.value = null
    }

    fun handleUserVoicePrompt(prompt: String) {
        viewModelScope.launch {
            _assistantState.value = AssistantState.CONNECTING
            _userSpokenText.value = prompt

            val reply = restEngine?.converseWithVoice(prompt) ?: "Samajh nahi aaya."
            _transcript.value = reply
            _lastActionNotice.value = reply
        }
    }

    fun executeQuickAction(actionName: String) {
        viewModelScope.launch {
            audioOutputManager.playActionChime()
            when (actionName) {
                "torch" -> {
                    val res = deviceActionBridge.toggleFlashlight()
                    _lastActionNotice.value = res.message
                    _transcript.value = res.spokenReply
                    audioOutputManager.speakText(res.spokenReply)
                }
                "battery" -> {
                    val res = deviceActionBridge.getBatteryStatus()
                    _lastActionNotice.value = res.message
                    _transcript.value = res.spokenReply
                    audioOutputManager.speakText(res.spokenReply)
                }
                "volume_up" -> {
                    val res = deviceActionBridge.adjustVolume("up")
                    _lastActionNotice.value = res.message
                    _transcript.value = res.spokenReply
                    audioOutputManager.speakText(res.spokenReply)
                }
                "volume_down" -> {
                    val res = deviceActionBridge.adjustVolume("down")
                    _lastActionNotice.value = res.message
                    _transcript.value = res.spokenReply
                    audioOutputManager.speakText(res.spokenReply)
                }
                "volume_mute" -> {
                    val res = deviceActionBridge.adjustVolume("mute")
                    _lastActionNotice.value = res.message
                    _transcript.value = res.spokenReply
                    audioOutputManager.speakText(res.spokenReply)
                }
                "whatsapp" -> {
                    val res = deviceActionBridge.openWhatsApp()
                    _lastActionNotice.value = res.message
                    _transcript.value = res.spokenReply
                    audioOutputManager.speakText(res.spokenReply)
                }
                "camera" -> {
                    val res = deviceActionBridge.openApp("camera")
                    _lastActionNotice.value = res.message
                    _transcript.value = res.spokenReply
                    audioOutputManager.speakText(res.spokenReply)
                }
                "youtube" -> {
                    val res = deviceActionBridge.openApp("youtube")
                    _lastActionNotice.value = res.message
                    _transcript.value = res.spokenReply
                    audioOutputManager.speakText(res.spokenReply)
                }
                "settings" -> {
                    val res = deviceActionBridge.openSettings("general")
                    _lastActionNotice.value = res.message
                    _transcript.value = res.spokenReply
                    audioOutputManager.speakText(res.spokenReply)
                }
            }
        }
    }

    fun runSpeakerTest() {
        log("Testing Speaker & Voice output...")
        _lastActionNotice.value = "Playing 440Hz tone, chime, and voice greeting..."
        audioOutputManager.testSpeaker(440f, 900)
    }

    fun selectContact(match: ContactMatch) {
        _contactChoices.value = emptyList()
        val res = deviceActionBridge.makeCall(match.phoneNumber)
        _lastActionNotice.value = "Calling ${match.name}"
        audioOutputManager.speakText(res.spokenReply)
    }

    fun dismissContactChoices() {
        _contactChoices.value = emptyList()
    }

    fun clearLogs() {
        _logs.value = emptyList()
    }

    override fun onCleared() {
        super.onCleared()
        audioInputManager.stopRecording()
        audioOutputManager.release()
        liveSession?.disconnect()
        speechRecognizer?.destroy()
    }
}
