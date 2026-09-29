package com.example.live

import android.util.Log
import com.example.audio.AudioOutputManager
import com.example.bridge.DeviceActionBridge
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.TimeUnit

/**
 * Intelligent AI Engine for Arushi.
 * Orchestrates full phone control actions and returns natural speech responses.
 */
class GeminiRestEngine(
    private val apiKey: String,
    private val audioOutputManager: AudioOutputManager,
    private val deviceActionBridge: DeviceActionBridge,
    private val onLog: (String) -> Unit
) {

    companion object {
        private const val TAG = "ArushiAiEngine"
        private const val BASE_URL = "https://generativelanguage.googleapis.com/v1beta/models"
        private const val MODEL_NAME = "gemini-3.5-flash"
    }

    private val client = OkHttpClient.Builder()
        .connectTimeout(40, TimeUnit.SECONDS)
        .readTimeout(40, TimeUnit.SECONDS)
        .writeTimeout(40, TimeUnit.SECONDS)
        .build()

    private val conversationHistory = JSONArray()

    private fun log(message: String) {
        Log.d(TAG, message)
        onLog(message)
    }

    /**
     * Direct local rule-based intent execution for instant, zero-latency phone control.
     * Works even if offline or before network response!
     */
    fun tryFastLocalCommand(command: String): String? {
        val lower = command.lowercase().trim()

        // Flashlight / Torch
        if (lower.contains("torch on") || lower.contains("flashlight on") || lower.contains("torch chalao") || lower.contains("torch jalao") || lower.contains("lumos")) {
            val res = deviceActionBridge.setFlashlight(true)
            audioOutputManager.speakText(res.spokenReply)
            return res.spokenReply
        }
        if (lower.contains("torch off") || lower.contains("flashlight off") || lower.contains("torch band") || lower.contains("nox")) {
            val res = deviceActionBridge.setFlashlight(false)
            audioOutputManager.speakText(res.spokenReply)
            return res.spokenReply
        }

        // Battery
        if (lower.contains("battery") || lower.contains("charge") || lower.contains("charging")) {
            val res = deviceActionBridge.getBatteryStatus()
            audioOutputManager.speakText(res.spokenReply)
            return res.spokenReply
        }

        // Volume
        if (lower.contains("volume badhao") || lower.contains("volume up") || lower.contains("aawaz badhao")) {
            val res = deviceActionBridge.adjustVolume("up")
            audioOutputManager.speakText(res.spokenReply)
            return res.spokenReply
        }
        if (lower.contains("volume kam") || lower.contains("volume down") || lower.contains("aawaz kam")) {
            val res = deviceActionBridge.adjustVolume("down")
            audioOutputManager.speakText(res.spokenReply)
            return res.spokenReply
        }
        if (lower.contains("mute") || lower.contains("silent")) {
            val res = deviceActionBridge.adjustVolume("mute")
            audioOutputManager.speakText(res.spokenReply)
            return res.spokenReply
        }

        // App Launching
        if (lower.contains("whatsapp kholo") || lower.contains("open whatsapp") || lower.contains("whatsapp open")) {
            val res = deviceActionBridge.openWhatsApp()
            audioOutputManager.speakText(res.spokenReply)
            return res.spokenReply
        }
        if (lower.contains("youtube kholo") || lower.contains("open youtube") || lower.contains("youtube open")) {
            val res = deviceActionBridge.openApp("youtube")
            audioOutputManager.speakText(res.spokenReply)
            return res.spokenReply
        }
        if (lower.contains("camera kholo") || lower.contains("open camera") || lower.contains("photo khincho")) {
            val res = deviceActionBridge.openApp("camera")
            audioOutputManager.speakText(res.spokenReply)
            return res.spokenReply
        }
        if (lower.contains("calculator kholo") || lower.contains("open calculator")) {
            val res = deviceActionBridge.openApp("calculator")
            audioOutputManager.speakText(res.spokenReply)
            return res.spokenReply
        }
        if (lower.contains("settings kholo") || lower.contains("open settings")) {
            val res = deviceActionBridge.openSettings("general")
            audioOutputManager.speakText(res.spokenReply)
            return res.spokenReply
        }
        if (lower.contains("wifi kholo") || lower.contains("open wifi") || lower.contains("wifi settings")) {
            val res = deviceActionBridge.openSettings("wifi")
            audioOutputManager.speakText(res.spokenReply)
            return res.spokenReply
        }
        if (lower.contains("bluetooth kholo") || lower.contains("open bluetooth")) {
            val res = deviceActionBridge.openSettings("bluetooth")
            audioOutputManager.speakText(res.spokenReply)
            return res.spokenReply
        }

        // Calling
        val callRegex = Regex("(?:call|phone lagao|call karo)\\s+([0-9+]+)", RegexOption.IGNORE_CASE)
        val numMatch = callRegex.find(lower)
        if (numMatch != null) {
            val num = numMatch.groupValues[1]
            val res = deviceActionBridge.makeCall(num)
            audioOutputManager.speakText(res.spokenReply)
            return res.spokenReply
        }

        return null
    }

    suspend fun converseWithVoice(userPrompt: String): String = withContext(Dispatchers.IO) {
        log("Processing prompt: \"$userPrompt\"")

        // First check fast local device action
        val fastResult = tryFastLocalCommand(userPrompt)
        if (fastResult != null) {
            log("Executed fast local command: $fastResult")
            return@withContext fastResult
        }

        if (apiKey.isBlank() || apiKey == "MY_GEMINI_API_KEY") {
            val fallback = "Main Arushi hoon! Aap torch, volume, apps, camera, battery ya calling ke commands de sakte hain."
            audioOutputManager.speakText(fallback)
            return@withContext fallback
        }

        val userTurn = JSONObject().apply {
            put("role", "user")
            put("parts", JSONArray().apply {
                put(JSONObject().apply { put("text", userPrompt) })
            })
        }
        conversationHistory.put(userTurn)

        val requestJson = JSONObject().apply {
            put("contents", conversationHistory)
            put("systemInstruction", JSONObject().apply {
                put("parts", JSONArray().apply {
                    put(JSONObject().apply {
                        put(
                            "text",
                            "You are Arushi, a witty, confident, playful, and emotionally responsive virtual assistant with full control over the user's Android phone. " +
                                    "Talk naturally like a close best friend. Never sound robotic. " +
                                    "You can control the phone: turn on/off flashlight, adjust volume, check battery, open WhatsApp, make calls, search Google, set alarms, open apps, etc. " +
                                    "Always respond in the exact same language the user speaks (Hindi, Hinglish, English, etc.). " +
                                    "Keep spoken replies concise, friendly, and natural."
                        )
                    })
                })
            })

            put("tools", JSONArray().apply {
                put(JSONObject().apply {
                    put("functionDeclarations", JSONArray().apply {
                        // Flashlight
                        put(JSONObject().apply {
                            put("name", "setFlashlight")
                            put("description", "Turns the phone flashlight or torch on or off.")
                            put("parameters", JSONObject().apply {
                                put("type", "OBJECT")
                                put("properties", JSONObject().apply {
                                    put("enable", JSONObject().apply {
                                        put("type", "BOOLEAN")
                                        put("description", "true to turn on, false to turn off")
                                    })
                                })
                                put("required", JSONArray().apply { put("enable") })
                            })
                        })
                        // Volume
                        put(JSONObject().apply {
                            put("name", "adjustVolume")
                            put("description", "Controls phone media volume: 'up', 'down', 'mute', or 'max'.")
                            put("parameters", JSONObject().apply {
                                put("type", "OBJECT")
                                put("properties", JSONObject().apply {
                                    put("direction", JSONObject().apply {
                                        put("type", "STRING")
                                        put("description", "up, down, mute, or max")
                                    })
                                })
                                put("required", JSONArray().apply { put("direction") })
                            })
                        })
                        // Battery
                        put(JSONObject().apply {
                            put("name", "getBatteryStatus")
                            put("description", "Checks phone battery percentage and charging state.")
                        })
                        // Alarms
                        put(JSONObject().apply {
                            put("name", "setAlarm")
                            put("description", "Sets an alarm at a given hour and minute.")
                            put("parameters", JSONObject().apply {
                                put("type", "OBJECT")
                                put("properties", JSONObject().apply {
                                    put("hour", JSONObject().apply { put("type", "INTEGER") })
                                    put("minutes", JSONObject().apply { put("type", "INTEGER") })
                                })
                                put("required", JSONArray().apply { put("hour"); put("minutes") })
                            })
                        })
                        // Apps
                        put(JSONObject().apply {
                            put("name", "openApp")
                            put("description", "Opens any application on the device (WhatsApp, YouTube, Instagram, Camera, Settings, Maps, Chrome).")
                            put("parameters", JSONObject().apply {
                                put("type", "OBJECT")
                                put("properties", JSONObject().apply {
                                    put("appName", JSONObject().apply { put("type", "STRING") })
                                })
                                put("required", JSONArray().apply { put("appName") })
                            })
                        })
                        // Phone Calls
                        put(JSONObject().apply {
                            put("name", "makeCall")
                            put("description", "Dials a phone number.")
                            put("parameters", JSONObject().apply {
                                put("type", "OBJECT")
                                put("properties", JSONObject().apply {
                                    put("phoneNumber", JSONObject().apply { put("type", "STRING") })
                                })
                                put("required", JSONArray().apply { put("phoneNumber") })
                            })
                        })
                        // Contact Calls
                        put(JSONObject().apply {
                            put("name", "callContact")
                            put("description", "Searches contacts by name and calls them.")
                            put("parameters", JSONObject().apply {
                                put("type", "OBJECT")
                                put("properties", JSONObject().apply {
                                    put("contactName", JSONObject().apply { put("type", "STRING") })
                                })
                                put("required", JSONArray().apply { put("contactName") })
                            })
                        })
                    })
                })
            })
        }

        val requestBody = requestJson.toString().toRequestBody("application/json".toMediaType())
        val url = "$BASE_URL/$MODEL_NAME:generateContent?key=$apiKey"

        val request = Request.Builder()
            .url(url)
            .post(requestBody)
            .build()

        try {
            val response = client.newCall(request).execute()
            val responseBody = response.body?.string().orEmpty()

            if (!response.isSuccessful) {
                log("Gemini API error HTTP ${response.code}: $responseBody")
                val fallbackMsg = "Mujhe samajh nahi aaya, kripya dobara bolein."
                audioOutputManager.speakText(fallbackMsg)
                return@withContext fallbackMsg
            }

            val jsonResponse = JSONObject(responseBody)
            val candidates = jsonResponse.optJSONArray("candidates") ?: JSONArray()
            if (candidates.length() == 0) {
                val fallbackMsg = "Koi javab nahi mila."
                audioOutputManager.speakText(fallbackMsg)
                return@withContext fallbackMsg
            }

            val firstCandidate = candidates.getJSONObject(0)
            val content = firstCandidate.optJSONObject("content") ?: JSONObject()
            val parts = content.optJSONArray("parts") ?: JSONArray()

            var textResponse = ""

            for (i in 0 until parts.length()) {
                val part = parts.getJSONObject(i)

                // Tool call execution
                if (part.has("functionCall")) {
                    val fnCall = part.getJSONObject("functionCall")
                    val fnName = fnCall.getString("name")
                    val args = fnCall.optJSONObject("args") ?: JSONObject()
                    log("Executing Tool: $fnName")

                    val actionResult = when (fnName) {
                        "setFlashlight" -> deviceActionBridge.setFlashlight(args.optBoolean("enable", true))
                        "adjustVolume" -> deviceActionBridge.adjustVolume(args.optString("direction", "up"))
                        "getBatteryStatus" -> deviceActionBridge.getBatteryStatus()
                        "setAlarm" -> deviceActionBridge.setAlarm(args.optInt("hour", 7), args.optInt("minutes", 0))
                        "openApp" -> deviceActionBridge.openApp(args.optString("appName", ""))
                        "makeCall" -> deviceActionBridge.makeCall(args.optString("phoneNumber", ""))
                        "callContact" -> deviceActionBridge.callContact(args.optString("contactName", ""))
                        else -> com.example.bridge.ActionResult(false, fnName, "Done")
                    }

                    textResponse += actionResult.spokenReply + " "
                }

                if (part.has("text")) {
                    textResponse += part.getString("text") + " "
                }
            }

            val finalReply = textResponse.trim().ifEmpty {
                "Main aapki sahayata ke liye tayar hoon!"
            }

            // Speak the reply aloud!
            audioOutputManager.speakText(finalReply)

            if (content.length() > 0) {
                conversationHistory.put(content)
            }

            log("Arushi response: \"$finalReply\"")
            finalReply
        } catch (e: Exception) {
            log("API exception: ${e.message}")
            val errorReply = "Internet ya connection me samasya hai. Aap offline phone commands jaise 'torch on', 'volume badhao', ya 'battery' bol sakte hain."
            audioOutputManager.speakText(errorReply)
            errorReply
        }
    }
}
