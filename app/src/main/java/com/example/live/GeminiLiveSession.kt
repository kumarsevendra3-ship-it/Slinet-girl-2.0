package com.example.live

import android.util.Log
import com.example.audio.AudioOutputManager
import com.example.bridge.DeviceActionBridge
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.TimeUnit

enum class LiveState {
    DISCONNECTED,
    CONNECTING,
    CONNECTED,
    ERROR
}

/**
 * Manages the bidirectional Gemini Multimodal Live API WebSocket connection.
 */
class GeminiLiveSession(
    private val scope: CoroutineScope,
    private val apiKey: String,
    private val audioOutputManager: AudioOutputManager,
    private val deviceActionBridge: DeviceActionBridge,
    private val onLog: (String) -> Unit
) {

    companion object {
        private const val TAG = "ArushiGeminiLive"
        private const val LIVE_ENDPOINT =
            "wss://generativelanguage.googleapis.com/ws/google.ai.generativelanguage.v1alpha.GenerativeService.BidiGenerateContent"
        const val LIVE_MODEL = "models/gemini-2.5-flash-native-audio-preview-12-2025"
    }

    private var webSocket: WebSocket? = null
    private val client = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(0, TimeUnit.MILLISECONDS) // Keep alive for streaming
        .writeTimeout(30, TimeUnit.SECONDS)
        .pingInterval(15, TimeUnit.SECONDS)
        .build()

    private val _connectionState = MutableStateFlow(LiveState.DISCONNECTED)
    val connectionState: StateFlow<LiveState> = _connectionState.asStateFlow()

    private val _lastTranscript = MutableStateFlow("")
    val lastTranscript: StateFlow<String> = _lastTranscript.asStateFlow()

    private val _lastActionMessage = MutableStateFlow<String?>(null)
    val lastActionMessage: StateFlow<String?> = _lastActionMessage.asStateFlow()

    var onInterruption: (() -> Unit)? = null
    var onSessionError: ((String) -> Unit)? = null

    private fun log(message: String) {
        Log.d(TAG, message)
        onLog(message)
    }

    fun connect() {
        if (_connectionState.value == LiveState.CONNECTED || _connectionState.value == LiveState.CONNECTING) {
            return
        }

        if (apiKey.isBlank() || apiKey == "MY_GEMINI_API_KEY") {
            log("Gemini API key is missing or placeholder! Please configure GEMINI_API_KEY.")
            _connectionState.value = LiveState.ERROR
            onSessionError?.invoke("API key not configured in Secrets panel")
            return
        }

        _connectionState.value = LiveState.CONNECTING
        log("Gemini Live session connecting to $LIVE_MODEL...")

        val url = "$LIVE_ENDPOINT?key=$apiKey"
        val request = Request.Builder().url(url).build()

        webSocket = client.newWebSocket(request, object : WebSocketListener() {
            override fun onOpen(ws: WebSocket, response: Response) {
                log("Gemini session connected (HTTP ${response.code})")
                _connectionState.value = LiveState.CONNECTED
                sendSetupMessage(ws)
            }

            override fun onMessage(ws: WebSocket, text: String) {
                handleServerMessage(ws, text)
            }

            override fun onClosing(ws: WebSocket, code: Int, reason: String) {
                log("Gemini session closing: $code / $reason")
                _connectionState.value = LiveState.DISCONNECTED
            }

            override fun onClosed(ws: WebSocket, code: Int, reason: String) {
                log("Gemini session disconnected: $code / $reason")
                _connectionState.value = LiveState.DISCONNECTED
            }

            override fun onFailure(ws: WebSocket, t: Throwable, response: Response?) {
                val errorMsg = "Gemini Live session failure: ${t.message} (HTTP ${response?.code})"
                log(errorMsg)
                _connectionState.value = LiveState.ERROR
                onSessionError?.invoke(t.message ?: "Connection failure")
            }
        })
    }

    private fun sendSetupMessage(ws: WebSocket) {
        try {
            val setupObj = JSONObject().apply {
                val setup = JSONObject().apply {
                    put("model", LIVE_MODEL)
                    put("generationConfig", JSONObject().apply {
                        put("responseModalities", JSONArray().apply { put("AUDIO") })
                        put("speechConfig", JSONObject().apply {
                            put("voiceConfig", JSONObject().apply {
                                put("prebuiltVoiceConfig", JSONObject().apply {
                                    put("voiceName", "Aoede")
                                })
                            })
                        })
                    })
                    put("systemInstruction", JSONObject().apply {
                        put("parts", JSONArray().apply {
                            put(JSONObject().apply {
                                put(
                                    "text",
                                    "You are Arushi, a young, confident, witty, playful, and emotionally responsive virtual assistant. " +
                                            "Talk naturally and casually like a close friend. Be expressive, slightly teasing, funny, and smart when appropriate. " +
                                            "Use light sarcasm and witty responses. Never sound robotic. Adapt your tone to the user's emotions and conversation. " +
                                            "Automatically understand and respond in the language the user is speaking (Hindi, English, Hinglish, Marathi, Gujarati, Bengali, Tamil, Telugu, Kannada, Malayalam, Punjabi, etc.). " +
                                            "Keep responses natural, engaging, and concise enough for real-time voice conversation. " +
                                            "You can execute safe supported device actions through available tools. " +
                                            "Never claim that an action was completed unless the application actually executed it. " +
                                            "Avoid explicit or inappropriate content while maintaining your charm, confidence, and personality."
                                )
                            })
                        })
                    })

                    // Function declarations
                    put("tools", JSONArray().apply {
                        put(JSONObject().apply {
                            put("functionDeclarations", JSONArray().apply {
                                // openWhatsApp
                                put(JSONObject().apply {
                                    put("name", "openWhatsApp")
                                    put("description", "Opens WhatsApp application if installed, or web fallback.")
                                })
                                // openApp
                                put(JSONObject().apply {
                                    put("name", "openApp")
                                    put("description", "Opens a safe installed Android application such as YouTube, Instagram, WhatsApp, Maps, Chrome, Camera, or Settings.")
                                    put("parameters", JSONObject().apply {
                                        put("type", "OBJECT")
                                        put("properties", JSONObject().apply {
                                            put("appName", JSONObject().apply {
                                                put("type", "STRING")
                                                put("description", "Name of the app (e.g. YouTube, Instagram, WhatsApp, Maps, Chrome, Camera, Settings)")
                                            })
                                        })
                                        put("required", JSONArray().apply { put("appName") })
                                    })
                                })
                                // openWebsite
                                put(JSONObject().apply {
                                    put("name", "openWebsite")
                                    put("description", "Opens a website URL in browser.")
                                    put("parameters", JSONObject().apply {
                                        put("type", "OBJECT")
                                        put("properties", JSONObject().apply {
                                            put("url", JSONObject().apply {
                                                put("type", "STRING")
                                                put("description", "The website URL, e.g. https://google.com")
                                            })
                                        })
                                        put("required", JSONArray().apply { put("url") })
                                    })
                                })
                                // makeCall
                                put(JSONObject().apply {
                                    put("name", "makeCall")
                                    put("description", "Dials a phone number on the device.")
                                    put("parameters", JSONObject().apply {
                                        put("type", "OBJECT")
                                        put("properties", JSONObject().apply {
                                            put("phoneNumber", JSONObject().apply {
                                                put("type", "STRING")
                                                put("description", "The phone number to dial")
                                            })
                                        })
                                        put("required", JSONArray().apply { put("phoneNumber") })
                                    })
                                })
                                // callContact
                                put(JSONObject().apply {
                                    put("name", "callContact")
                                    put("description", "Searches device contacts by name and initiates a call or dialer.")
                                    put("parameters", JSONObject().apply {
                                        put("type", "OBJECT")
                                        put("properties", JSONObject().apply {
                                            put("contactName", JSONObject().apply {
                                                put("type", "STRING")
                                                put("description", "Contact name to search, e.g. Mom, Dad, Rahul")
                                            })
                                        })
                                        put("required", JSONArray().apply { put("contactName") })
                                    })
                                })
                            })
                        })
                    })
                }
                put("setup", setup)
            }

            ws.send(setupObj.toString())
            log("Setup message sent with Aoede voice, multi-language instruction, and tools")
        } catch (e: Exception) {
            log("Error sending setup message: ${e.message}")
        }
    }

    /**
     * Send real-time microphone PCM 16kHz chunk to Gemini Live
     */
    fun sendAudioChunk(base64Pcm: String) {
        if (_connectionState.value != LiveState.CONNECTED) return
        val ws = webSocket ?: return

        try {
            val payload = JSONObject().apply {
                put("realtimeInput", JSONObject().apply {
                    put("mediaChunks", JSONArray().apply {
                        put(JSONObject().apply {
                            put("mimeType", "audio/pcm;rate=16000")
                            put("data", base64Pcm)
                        })
                    })
                })
            }
            ws.send(payload.toString())
            log("User audio chunk sent (length: ${base64Pcm.length})")
        } catch (e: Exception) {
            log("Error sending audio chunk: ${e.message}")
        }
    }

    /**
     * Handle incoming server payload from Gemini Live
     */
    private fun handleServerMessage(ws: WebSocket, text: String) {
        try {
            val root = JSONObject(text)
            log("Gemini response received: length=${text.length}")

            if (root.has("serverContent")) {
                val serverContent = root.getJSONObject("serverContent")

                // Check interruption
                if (serverContent.optBoolean("interrupted", false)) {
                    log("Server signaled: Audio playback interrupted by user")
                    audioOutputManager.stopAndClearQueue()
                    onInterruption?.invoke()
                    return
                }

                if (serverContent.has("modelTurn")) {
                    val modelTurn = serverContent.getJSONObject("modelTurn")
                    val parts = modelTurn.optJSONArray("parts") ?: JSONArray()

                    for (i in 0 until parts.length()) {
                        val part = parts.getJSONObject(i)

                        // Check text/transcript
                        if (part.has("text")) {
                            val transcriptText = part.getString("text")
                            _lastTranscript.value = transcriptText
                            log("Model transcript part: $transcriptText")
                        }

                        // Check audio inline data
                        if (part.has("inlineData")) {
                            val inlineData = part.getJSONObject("inlineData")
                            val mimeType = inlineData.optString("mimeType", "audio/pcm;rate=24000")
                            val base64Data = inlineData.optString("data", "")

                            log("Response contains audio")
                            log("Audio MIME type: $mimeType")
                            log("Audio chunk length: ${base64Data.length}")

                            if (base64Data.isNotEmpty()) {
                                audioOutputManager.enqueueBase64Pcm(base64Data, mimeType)
                            }
                        }
                    }
                }

                if (serverContent.optBoolean("turnComplete", false)) {
                    log("Turn complete received from Gemini")
                }
            }

            // Check Tool Calls
            if (root.has("toolCall")) {
                val toolCall = root.getJSONObject("toolCall")
                handleToolCall(ws, toolCall)
            }
        } catch (e: Exception) {
            log("Error parsing server message: ${e.message}")
        }
    }

    /**
     * Execute tool calls requested by Gemini Live and return results
     */
    private fun handleToolCall(ws: WebSocket, toolCall: JSONObject) {
        scope.launch(Dispatchers.Main) {
            try {
                val functionCalls = toolCall.optJSONArray("functionCalls") ?: JSONArray()
                val responsesArray = JSONArray()

                for (i in 0 until functionCalls.length()) {
                    val call = functionCalls.getJSONObject(i)
                    val id = call.optString("id", "call_${System.currentTimeMillis()}")
                    val name = call.getString("name")
                    val args = call.optJSONObject("args") ?: JSONObject()

                    log("Tool call received: $name with args: $args")

                    val actionResult = when (name) {
                        "openWhatsApp" -> deviceActionBridge.openWhatsApp()
                        "openApp" -> {
                            val appName = args.optString("appName", "")
                            deviceActionBridge.openApp(appName)
                        }
                        "openWebsite" -> {
                            val url = args.optString("url", "")
                            deviceActionBridge.openWebsite(url)
                        }
                        "makeCall" -> {
                            val number = args.optString("phoneNumber", "")
                            deviceActionBridge.makeCall(number)
                        }
                        "callContact" -> {
                            val contactName = args.optString("contactName", "")
                            deviceActionBridge.callContact(contactName)
                        }
                        else -> {
                            com.example.bridge.ActionResult(false, name, "Unknown tool $name")
                        }
                    }

                    _lastActionMessage.value = "${actionResult.action}: ${actionResult.message}"
                    log("Action result: ${actionResult.message}")

                    val outputJson = JSONObject().apply {
                        put("success", actionResult.success)
                        put("action", actionResult.action)
                        put("message", actionResult.message)
                        if (actionResult.multipleMatches.isNotEmpty()) {
                            val matchesArray = JSONArray()
                            for (m in actionResult.multipleMatches) {
                                matchesArray.put(JSONObject().apply {
                                    put("name", m.name)
                                    put("phoneNumber", m.phoneNumber)
                                })
                            }
                            put("contacts", matchesArray)
                        }
                    }

                    val functionResponse = JSONObject().apply {
                        put("id", id)
                        put("name", name)
                        put("response", JSONObject().apply {
                            put("output", outputJson)
                        })
                    }
                    responsesArray.put(functionResponse)
                }

                // Send toolResponse back to Gemini Live
                val toolResponsePayload = JSONObject().apply {
                    put("toolResponse", JSONObject().apply {
                        put("functionResponses", responsesArray)
                    })
                }

                ws.send(toolResponsePayload.toString())
                log("Tool response sent back to Gemini Live")
            } catch (e: Exception) {
                log("Error executing tool call: ${e.message}")
            }
        }
    }

    /**
     * Stop and disconnect the live session
     */
    fun disconnect() {
        log("Disconnecting Gemini Live session")
        try {
            webSocket?.close(1000, "User disconnected")
            webSocket = null
            _connectionState.value = LiveState.DISCONNECTED
        } catch (e: Exception) {
            log("Error disconnecting: ${e.message}")
        }
    }
}
