package com.example.bridge

import android.app.SearchManager
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraManager
import android.media.AudioManager
import android.net.Uri
import android.os.BatteryManager
import android.provider.AlarmClock
import android.provider.ContactsContract
import android.provider.MediaStore
import android.provider.Settings
import android.util.Log

data class ContactMatch(val name: String, val phoneNumber: String)

data class ActionResult(
    val success: Boolean,
    val action: String,
    val message: String,
    val spokenReply: String = message,
    val multipleMatches: List<ContactMatch> = emptyList(),
    val directExecution: Boolean = true
)

/**
 * Full Phone Control Device Action Bridge for Arushi.
 * Enables control over:
 * - Flashlight / Torch (On/Off/Toggle)
 * - Volume, Mute, Ringer Modes (Silent/Vibrate/Normal)
 * - Battery percentage & charging status
 * - Setting Alarms and Timers
 * - Sending WhatsApp messages & SMS
 * - Making phone calls & calling contacts by name
 * - Google Maps navigation & directions
 * - Quick settings (Wi-Fi, Bluetooth, Display, Sound, Battery)
 * - Opening any installed or supported application
 * - Web search
 */
class DeviceActionBridge(private val context: Context) {

    companion object {
        private const val TAG = "ArushiDeviceBridge"
    }

    private var isTorchOn = false

    // -------------------------------------------------------------------------
    // 1. FLASHLIGHT / TORCH CONTROL
    // -------------------------------------------------------------------------

    fun setFlashlight(enable: Boolean): ActionResult {
        Log.d(TAG, "Executing setFlashlight: enable=$enable")
        return try {
            val cameraManager = context.getSystemService(Context.CAMERA_SERVICE) as CameraManager
            val cameraId = cameraManager.cameraIdList.firstOrNull { id ->
                val chars = cameraManager.getCameraCharacteristics(id)
                chars.get(CameraCharacteristics.FLASH_INFO_AVAILABLE) == true
            }

            if (cameraId == null) {
                return ActionResult(false, "setFlashlight", "Flashlight hardware is not available on this device.")
            }

            cameraManager.setTorchMode(cameraId, enable)
            isTorchOn = enable
            val msg = if (enable) "Flashlight turned on." else "Flashlight turned off."
            val hindiMsg = if (enable) "Maine flashlight on kar di hai!" else "Flashlight band kar di gayi hai."
            ActionResult(true, "setFlashlight", msg, hindiMsg)
        } catch (e: Exception) {
            ActionResult(false, "setFlashlight", "Could not control flashlight: ${e.message}")
        }
    }

    fun toggleFlashlight(): ActionResult {
        return setFlashlight(!isTorchOn)
    }

    // -------------------------------------------------------------------------
    // 2. VOLUME & SOUND CONTROL
    // -------------------------------------------------------------------------

    fun adjustVolume(direction: String): ActionResult {
        Log.d(TAG, "Executing adjustVolume: direction=$direction")
        return try {
            val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
            when (direction.lowercase()) {
                "up", "increase", "raise" -> {
                    audioManager.adjustStreamVolume(AudioManager.STREAM_MUSIC, AudioManager.ADJUST_RAISE, AudioManager.FLAG_SHOW_UI)
                    ActionResult(true, "adjustVolume", "Volume increased.", "Volume badha diya hai.")
                }
                "down", "decrease", "lower" -> {
                    audioManager.adjustStreamVolume(AudioManager.STREAM_MUSIC, AudioManager.ADJUST_LOWER, AudioManager.FLAG_SHOW_UI)
                    ActionResult(true, "adjustVolume", "Volume decreased.", "Volume kam kar diya hai.")
                }
                "mute" -> {
                    audioManager.setStreamVolume(AudioManager.STREAM_MUSIC, 0, AudioManager.FLAG_SHOW_UI)
                    ActionResult(true, "adjustVolume", "Media volume muted.", "Volume mute kar diya hai.")
                }
                "max" -> {
                    val maxVol = audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
                    audioManager.setStreamVolume(AudioManager.STREAM_MUSIC, maxVol, AudioManager.FLAG_SHOW_UI)
                    ActionResult(true, "adjustVolume", "Volume set to maximum.", "Volume full kar diya hai!")
                }
                else -> {
                    audioManager.adjustStreamVolume(AudioManager.STREAM_MUSIC, AudioManager.ADJUST_SAME, AudioManager.FLAG_SHOW_UI)
                    ActionResult(true, "adjustVolume", "Volume unchanged.")
                }
            }
        } catch (e: Exception) {
            ActionResult(false, "adjustVolume", "Failed to adjust volume: ${e.message}")
        }
    }

    fun setRingerMode(mode: String): ActionResult {
        Log.d(TAG, "Executing setRingerMode: mode=$mode")
        return try {
            val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
            when (mode.lowercase()) {
                "silent" -> {
                    audioManager.ringerMode = AudioManager.RINGER_MODE_SILENT
                    ActionResult(true, "setRingerMode", "Phone set to silent mode.", "Phone ko silent mode par kar diya hai.")
                }
                "vibrate" -> {
                    audioManager.ringerMode = AudioManager.RINGER_MODE_VIBRATE
                    ActionResult(true, "setRingerMode", "Phone set to vibrate mode.", "Phone vibration mode par set ho gaya hai.")
                }
                "normal", "ring" -> {
                    audioManager.ringerMode = AudioManager.RINGER_MODE_NORMAL
                    ActionResult(true, "setRingerMode", "Phone set to normal ringing mode.", "Phone normal ringing mode par hai.")
                }
                else -> ActionResult(false, "setRingerMode", "Unknown ringer mode: $mode")
            }
        } catch (e: Exception) {
            ActionResult(false, "setRingerMode", "Cannot change ringer mode (Permission needed): ${e.message}")
        }
    }

    // -------------------------------------------------------------------------
    // 3. BATTERY STATUS
    // -------------------------------------------------------------------------

    fun getBatteryStatus(): ActionResult {
        Log.d(TAG, "Executing getBatteryStatus")
        return try {
            val batteryFilter = IntentFilter(Intent.ACTION_BATTERY_CHANGED)
            val batteryStatus = context.registerReceiver(null, batteryFilter)
            val level = batteryStatus?.getIntExtra(BatteryManager.EXTRA_LEVEL, -1) ?: -1
            val scale = batteryStatus?.getIntExtra(BatteryManager.EXTRA_SCALE, -1) ?: -1
            val batteryPct = if (level >= 0 && scale > 0) (level * 100 / scale.toFloat()).toInt() else 0

            val status = batteryStatus?.getIntExtra(BatteryManager.EXTRA_STATUS, -1) ?: -1
            val isCharging = status == BatteryManager.BATTERY_STATUS_CHARGING ||
                    status == BatteryManager.BATTERY_STATUS_FULL

            val message = "Battery is at $batteryPct%." + if (isCharging) " Currently charging." else ""
            val hindiMessage = "Aapke phone ki battery $batteryPct pratishat hai" +
                    if (isCharging) " aur phone charge ho raha hai." else "."

            ActionResult(true, "getBatteryStatus", message, hindiMessage)
        } catch (e: Exception) {
            ActionResult(false, "getBatteryStatus", "Could not check battery: ${e.message}")
        }
    }

    // -------------------------------------------------------------------------
    // 4. ALARMS & TIMERS
    // -------------------------------------------------------------------------

    fun setAlarm(hour: Int, minutes: Int, message: String = "Arushi Alarm"): ActionResult {
        Log.d(TAG, "Executing setAlarm: $hour:$minutes, msg=$message")
        return try {
            val intent = Intent(AlarmClock.ACTION_SET_ALARM).apply {
                putExtra(AlarmClock.EXTRA_HOUR, hour)
                putExtra(AlarmClock.EXTRA_MINUTES, minutes)
                putExtra(AlarmClock.EXTRA_MESSAGE, message)
                putExtra(AlarmClock.EXTRA_SKIP_UI, false)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
            val reply = "Maine $hour bajke $minutes minute par alarm set kar diya hai."
            ActionResult(true, "setAlarm", "Alarm set for $hour:$minutes.", reply)
        } catch (e: Exception) {
            ActionResult(false, "setAlarm", "Could not set alarm: ${e.message}")
        }
    }

    fun setTimer(seconds: Int, message: String = "Timer"): ActionResult {
        Log.d(TAG, "Executing setTimer: ${seconds}s")
        return try {
            val intent = Intent(AlarmClock.ACTION_SET_TIMER).apply {
                putExtra(AlarmClock.EXTRA_LENGTH, seconds)
                putExtra(AlarmClock.EXTRA_MESSAGE, message)
                putExtra(AlarmClock.EXTRA_SKIP_UI, false)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
            val minutes = seconds / 60
            val reply = if (minutes > 0) "Maine $minutes minute ka timer shuru kar diya hai!" else "Maine $seconds second ka timer shuru kar diya hai!"
            ActionResult(true, "setTimer", "Timer set for $seconds seconds.", reply)
        } catch (e: Exception) {
            ActionResult(false, "setTimer", "Could not set timer: ${e.message}")
        }
    }

    // -------------------------------------------------------------------------
    // 5. WHATSAPP & SMS
    // -------------------------------------------------------------------------

    fun openWhatsApp(): ActionResult {
        val pm = context.packageManager
        val whatsappIntent = pm.getLaunchIntentForPackage("com.whatsapp")
        return if (whatsappIntent != null) {
            whatsappIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(whatsappIntent)
            ActionResult(true, "openWhatsApp", "WhatsApp opened successfully.", "WhatsApp khol diya hai.")
        } else {
            val webIntent = Intent(Intent.ACTION_VIEW, Uri.parse("https://wa.me/")).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            try {
                context.startActivity(webIntent)
                ActionResult(true, "openWhatsApp", "Opened WhatsApp Web.", "WhatsApp web khol diya gaya hai.")
            } catch (e: Exception) {
                ActionResult(false, "openWhatsApp", "WhatsApp is not installed.")
            }
        }
    }

    fun sendWhatsAppMessage(phoneNumber: String?, message: String): ActionResult {
        Log.d(TAG, "Executing sendWhatsAppMessage: num=$phoneNumber, msg=$message")
        return try {
            val cleanNumber = phoneNumber?.replace(Regex("[^0-9]"), "") ?: ""
            val encodedMsg = Uri.encode(message)
            val uri = if (cleanNumber.isNotEmpty()) {
                Uri.parse("https://wa.me/$cleanNumber?text=$encodedMsg")
            } else {
                Uri.parse("whatsapp://send?text=$encodedMsg")
            }

            val intent = Intent(Intent.ACTION_VIEW, uri).apply {
                setPackage("com.whatsapp")
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
            ActionResult(true, "sendWhatsAppMessage", "Opening WhatsApp with message.", "WhatsApp par message taiyar hai!")
        } catch (e: Exception) {
            // Fallback generic view
            openWebsite("https://wa.me/?text=${Uri.encode(message)}")
        }
    }

    fun sendSms(phoneNumber: String, message: String): ActionResult {
        Log.d(TAG, "Executing sendSms: num=$phoneNumber")
        return try {
            val intent = Intent(Intent.ACTION_SENDTO).apply {
                data = Uri.parse("smsto:$phoneNumber")
                putExtra("sms_body", message)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
            ActionResult(true, "sendSms", "SMS app opened with message.", "SMS message taiyar hai, send button dabayein.")
        } catch (e: Exception) {
            ActionResult(false, "sendSms", "Could not open SMS: ${e.message}")
        }
    }

    // -------------------------------------------------------------------------
    // 6. PHONE CALLS & CONTACTS
    // -------------------------------------------------------------------------

    fun makeCall(phoneNumber: String): ActionResult {
        val cleanedNumber = phoneNumber.replace(Regex("[^0-9+]"), "")
        if (cleanedNumber.isEmpty()) {
            return ActionResult(false, "makeCall", "Invalid phone number provided.")
        }

        return try {
            val dialIntent = Intent(Intent.ACTION_DIAL, Uri.parse("tel:$cleanedNumber")).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(dialIntent)
            ActionResult(true, "makeCall", "Dialer opened for $cleanedNumber.", "$cleanedNumber par call lagaya ja raha hai.")
        } catch (e: Exception) {
            ActionResult(false, "makeCall", "Could not start call: ${e.message}")
        }
    }

    fun callContact(contactName: String): ActionResult {
        if (context.checkSelfPermission(android.Manifest.permission.READ_CONTACTS) != PackageManager.PERMISSION_GRANTED) {
            return ActionResult(
                false,
                "callContact",
                "Contacts permission is not granted. Please grant Contacts permission to call by name."
            )
        }

        val matches = mutableListOf<ContactMatch>()
        try {
            val uri = ContactsContract.CommonDataKinds.Phone.CONTENT_URI
            val projection = arrayOf(
                ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME,
                ContactsContract.CommonDataKinds.Phone.NUMBER
            )
            val selection = "${ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME} LIKE ?"
            val selectionArgs = arrayOf("%$contactName%")

            val cursor = context.contentResolver.query(uri, projection, selection, selectionArgs, null)
            cursor?.use {
                val nameIndex = it.getColumnIndex(ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME)
                val numberIndex = it.getColumnIndex(ContactsContract.CommonDataKinds.Phone.NUMBER)

                while (it.moveToNext()) {
                    val name = if (nameIndex >= 0) it.getString(nameIndex) else "Unknown"
                    val number = if (numberIndex >= 0) it.getString(numberIndex) else ""
                    if (number.isNotBlank() && matches.none { m -> m.phoneNumber == number }) {
                        matches.add(ContactMatch(name, number))
                    }
                }
            }
        } catch (e: Exception) {
            return ActionResult(false, "callContact", "Failed to search contacts: ${e.message}")
        }

        return when {
            matches.isEmpty() -> {
                ActionResult(false, "callContact", "I couldn't find any contact named '$contactName' in your phone.", "Aapke phone me '$contactName' naam ka koi contact nahi mila.")
            }
            matches.size == 1 -> {
                val contact = matches.first()
                makeCall(contact.phoneNumber)
                ActionResult(true, "callContact", "Calling ${contact.name} at ${contact.phoneNumber}.", "${contact.name} ko phone lagaya ja raha hai.")
            }
            else -> {
                ActionResult(
                    success = false,
                    action = "callContact",
                    message = "Multiple matches found for '$contactName'.",
                    spokenReply = "Mujhe '$contactName' naam ke ${matches.size} contacts mile hain. Kise call karna hai?",
                    multipleMatches = matches,
                    directExecution = false
                )
            }
        }
    }

    // -------------------------------------------------------------------------
    // 7. NAVIGATION & MAPS
    // -------------------------------------------------------------------------

    fun openNavigation(destination: String): ActionResult {
        Log.d(TAG, "Executing openNavigation: $destination")
        return try {
            val uri = Uri.parse("google.navigation:q=" + Uri.encode(destination))
            val mapIntent = Intent(Intent.ACTION_VIEW, uri).apply {
                setPackage("com.google.android.apps.maps")
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }

            if (mapIntent.resolveActivity(context.packageManager) != null) {
                context.startActivity(mapIntent)
            } else {
                val webMap = Intent(Intent.ACTION_VIEW, Uri.parse("https://www.google.com/maps/dir/?api=1&destination=" + Uri.encode(destination))).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                context.startActivity(webMap)
            }
            ActionResult(true, "openNavigation", "Navigating to $destination.", "$destination ke liye navigation shuru kar di hai!")
        } catch (e: Exception) {
            ActionResult(false, "openNavigation", "Could not start navigation: ${e.message}")
        }
    }

    // -------------------------------------------------------------------------
    // 8. QUICK SETTINGS
    // -------------------------------------------------------------------------

    fun openSettings(type: String = "general"): ActionResult {
        Log.d(TAG, "Executing openSettings: type=$type")
        return try {
            val action = when (type.lowercase()) {
                "wifi" -> Settings.ACTION_WIFI_SETTINGS
                "bluetooth" -> Settings.ACTION_BLUETOOTH_SETTINGS
                "display", "brightness" -> Settings.ACTION_DISPLAY_SETTINGS
                "sound", "volume", "audio" -> Settings.ACTION_SOUND_SETTINGS
                "battery" -> Intent.ACTION_POWER_USAGE_SUMMARY
                "apps" -> Settings.ACTION_APPLICATION_SETTINGS
                "location", "gps" -> Settings.ACTION_LOCATION_SOURCE_SETTINGS
                else -> Settings.ACTION_SETTINGS
            }
            val intent = Intent(action).apply { addFlags(Intent.FLAG_ACTIVITY_NEW_TASK) }
            context.startActivity(intent)
            ActionResult(true, "openSettings", "Opened $type settings.", "$type settings open kar di hai.")
        } catch (e: Exception) {
            ActionResult(false, "openSettings", "Could not open settings: ${e.message}")
        }
    }

    // -------------------------------------------------------------------------
    // 9. APP LAUNCHING (FULL PHONE APPS)
    // -------------------------------------------------------------------------

    fun openApp(appName: String): ActionResult {
        val normalized = appName.trim().lowercase()
        Log.d(TAG, "Executing openApp: $normalized")

        if (normalized.contains("whatsapp")) return openWhatsApp()
        if (normalized.contains("torch") || normalized.contains("flashlight")) return toggleFlashlight()
        if (normalized.contains("battery")) return getBatteryStatus()
        if (normalized.contains("wifi")) return openSettings("wifi")
        if (normalized.contains("bluetooth")) return openSettings("bluetooth")

        if (normalized.contains("camera")) {
            return try {
                val intent = Intent(MediaStore.INTENT_ACTION_STILL_IMAGE_CAMERA).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                context.startActivity(intent)
                ActionResult(true, "openApp", "Camera opened.", "Camera open kar diya hai.")
            } catch (e: Exception) {
                ActionResult(false, "openApp", "Could not open camera: ${e.message}")
            }
        }

        if (normalized.contains("calculator")) {
            val calcPackages = listOf("com.google.android.calculator", "com.android.calculator2", "com.sec.android.app.popupcalculator")
            for (pkg in calcPackages) {
                val intent = context.packageManager.getLaunchIntentForPackage(pkg)
                if (intent != null) {
                    intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    context.startActivity(intent)
                    return ActionResult(true, "openApp", "Calculator opened.", "Calculator open ho gaya hai.")
                }
            }
        }

        if (normalized.contains("clock") || normalized.contains("alarm")) {
            val intent = Intent(AlarmClock.ACTION_SHOW_ALARMS).apply { addFlags(Intent.FLAG_ACTIVITY_NEW_TASK) }
            return try {
                context.startActivity(intent)
                ActionResult(true, "openApp", "Clock opened.", "Clock khol diya hai.")
            } catch (_: Exception) {
                ActionResult(false, "openApp", "Could not open Clock.")
            }
        }

        // Check common apps mapping
        val appPackages = mapOf(
            "youtube" to "com.google.android.youtube",
            "instagram" to "com.instagram.android",
            "chrome" to "com.android.chrome",
            "browser" to "com.android.chrome",
            "maps" to "com.google.android.apps.maps",
            "gmail" to "com.google.android.gm",
            "calendar" to "com.google.android.calendar",
            "photos" to "com.google.android.apps.photos",
            "gallery" to "com.google.android.apps.photos",
            "spotify" to "com.spotify.music"
        )

        val targetPkg = appPackages[normalized]
            ?: appPackages.entries.firstOrNull { normalized.contains(it.key) }?.value

        if (targetPkg != null) {
            val launchIntent = context.packageManager.getLaunchIntentForPackage(targetPkg)
            if (launchIntent != null) {
                launchIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                context.startActivity(launchIntent)
                return ActionResult(true, "openApp", "Opened $appName successfully.", "$appName khol diya gaya hai.")
            }
        }

        // Dynamic search in installed applications
        try {
            val pm = context.packageManager
            val installedApps = pm.getInstalledApplications(PackageManager.GET_META_DATA)
            for (app in installedApps) {
                val label = pm.getApplicationLabel(app).toString().lowercase()
                if (label.contains(normalized) || normalized.contains(label)) {
                    val intent = pm.getLaunchIntentForPackage(app.packageName)
                    if (intent != null) {
                        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        context.startActivity(intent)
                        return ActionResult(true, "openApp", "Opened $label successfully.", "$label khol diya hai.")
                    }
                }
            }
        } catch (_: Exception) {}

        // Fallback for YouTube or general web
        if (normalized.contains("youtube")) {
            return openWebsite("https://youtube.com")
        }

        return ActionResult(false, "openApp", "App '$appName' is not installed on this device.", "Yeh app '$appName' aapke phone me install nahi hai.")
    }

    // -------------------------------------------------------------------------
    // 10. WEB SEARCH & BROWSING
    // -------------------------------------------------------------------------

    fun searchWeb(query: String): ActionResult {
        Log.d(TAG, "Executing searchWeb: $query")
        return try {
            val intent = Intent(Intent.ACTION_WEB_SEARCH).apply {
                putExtra(SearchManager.QUERY, query)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
            ActionResult(true, "searchWeb", "Searching Google for '$query'.", "Google par '$query' search kiya ja raha hai.")
        } catch (_: Exception) {
            openWebsite("https://www.google.com/search?q=" + Uri.encode(query))
        }
    }

    fun openWebsite(rawUrl: String): ActionResult {
        var url = rawUrl.trim()
        if (!url.startsWith("http://") && !url.startsWith("https://")) {
            url = "https://$url"
        }

        return try {
            val uri = Uri.parse(url)
            val intent = Intent(Intent.ACTION_VIEW, uri).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
            ActionResult(true, "openWebsite", "Opened $url.", "Website open kar di hai.")
        } catch (e: Exception) {
            ActionResult(false, "openWebsite", "Failed to open website: ${e.message}")
        }
    }
}
