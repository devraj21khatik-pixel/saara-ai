package com.saara.ai

import android.Manifest
import android.annotation.SuppressLint
import android.app.SearchManager
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.ContactsContract
import android.provider.Settings
import android.webkit.JavascriptInterface
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import java.net.URLEncoder

class MainActivity : AppCompatActivity() {

    private lateinit var webView: WebView

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // 1. Request All Required Permissions (Audio, Contacts, Call, SMS)
        checkAndRequestPermissions()

        // 2. Initialize & Configure WebView
        webView = WebView(this).apply {
            settings.javaScriptEnabled = true
            settings.domStorageEnabled = true
            settings.mediaPlaybackRequiresUserGesture = false // Auto audio playback enable karne ke liye
            webViewClient = WebViewClient()
            
            // Connect JavaScript Bridge Interface
            addJavascriptInterface(WebAppInterface(this@MainActivity, this), "AndroidBridge")
        }

        webView.loadUrl("file:///android_asset/index.html")
        setContentView(webView)
    }

    private fun checkAndRequestPermissions() {
        val permissions = mutableListOf(
            Manifest.permission.RECORD_AUDIO,
            Manifest.permission.READ_CONTACTS,
            Manifest.permission.CALL_PHONE,
            Manifest.permission.SEND_SMS
        )

        val missingPermissions = permissions.filter {
            ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED
        }

        if (missingPermissions.isNotEmpty()) {
            ActivityCompat.requestPermissions(
                this,
                missingPermissions.toTypedArray(),
                101
            )
        }
    }

    class WebAppInterface(private val activity: MainActivity, private val webView: WebView) {
        
        // --- 1. BACKEND API COMMUNICATION ---
        @JavascriptInterface
        fun sendToSaara(prompt: String, isLive: Boolean) {
            val serverUrl = "https://saara-ai-lac.vercel.app/chat"
            
            ApiHelper.sendQueryToBackend(serverUrl, prompt, isLive) { result ->
                val safeResult = result.replace("'", "\\'").replace("\n", "\\n")
                
                webView.post {
                    webView.evaluateJavascript("receiveFromSaara('$safeResult')", null)
                }
            }
        }

        // --- 2. SCREEN CAST & FLOATING OVERLAY ---
        @JavascriptInterface
        fun openScreenCast() {
            activity.runOnUiThread {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && !Settings.canDrawOverlays(activity)) {
                    Toast.makeText(activity, "Please grant Overlay permission for Floating Icon", Toast.LENGTH_LONG).show()
                    val intent = Intent(
                        Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                        Uri.parse("package:${activity.packageName}")
                    )
                    activity.startActivity(intent)
                    return@runOnUiThread
                }

                val serviceIntent = Intent(activity, FloatingWidgetService::class.java)
                activity.startService(serviceIntent)

                try {
                    val intent = Intent("android.settings.CAST_SETTINGS")
                    intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    activity.startActivity(intent)
                } catch (e: Exception) {
                    try {
                        val intent = Intent("android.settings.WIFI_DISPLAY_SETTINGS")
                        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        activity.startActivity(intent)
                    } catch (ex: Exception) {
                        Toast.makeText(activity, "Screen Cast settings missing on this device", Toast.LENGTH_SHORT).show()
                    }
                }

                activity.moveTaskToBack(true)
            }
        }

        // --- 3. ANY APP LAUNCHER ---
        @JavascriptInterface
        fun openApp(appName: String) {
            activity.runOnUiThread {
                val pm = activity.packageManager
                val packages = pm.getInstalledApplications(PackageManager.GET_META_DATA)
                var appFound = false

                for (app in packages) {
                    val label = pm.getApplicationLabel(app).toString()
                    if (label.contains(appName, ignoreCase = true)) {
                        val launchIntent = pm.getLaunchIntentForPackage(app.packageName)
                        if (launchIntent != null) {
                            launchIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                            activity.startActivity(launchIntent)
                            appFound = true
                            break
                        }
                    }
                }

                if (!appFound) {
                    Toast.makeText(activity, "$appName app nahi mila Sir", Toast.LENGTH_SHORT).show()
                }
            }
        }

        // --- 4. PHONE CALL HANDLER ---
        @JavascriptInterface
        fun makeCall(query: String) {
            activity.runOnUiThread {
                val number = getPhoneNumberFromContact(query) ?: query.replace(Regex("[^0-9+]"), "")
                if (number.isNotEmpty()) {
                    try {
                        val intent = Intent(Intent.ACTION_CALL, Uri.parse("tel:$number")).apply {
                            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        }
                        activity.startActivity(intent)
                    } catch (e: Exception) {
                        val dialIntent = Intent(Intent.ACTION_DIAL, Uri.parse("tel:$number")).apply {
                            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        }
                        activity.startActivity(dialIntent)
                    }
                } else {
                    Toast.makeText(activity, "$query ka Contact nahi mila", Toast.LENGTH_SHORT).show()
                }
            }
        }

        // --- 5. SMS HANDLER ---
        @JavascriptInterface
        fun sendSMS(query: String, message: String) {
            activity.runOnUiThread {
                val number = getPhoneNumberFromContact(query) ?: query.replace(Regex("[^0-9+]"), "")
                if (number.isNotEmpty()) {
                    val intent = Intent(Intent.ACTION_SENDTO, Uri.parse("smsto:$number")).apply {
                        putExtra("sms_body", message)
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    }
                    activity.startActivity(intent)
                } else {
                    Toast.makeText(activity, "$query ka Contact nahi mila", Toast.LENGTH_SHORT).show()
                }
            }
        }

        // --- 6. WHATSAPP HANDLER ---
        @JavascriptInterface
        fun sendWhatsApp(query: String, message: String) {
            activity.runOnUiThread {
                val rawNumber = getPhoneNumberFromContact(query) ?: query.replace(Regex("[^0-9]"), "")
                val formattedNumber = if (rawNumber.length == 10) "91$rawNumber" else rawNumber

                if (formattedNumber.isNotEmpty()) {
                    try {
                        val url = "https://api.whatsapp.com/send?phone=$formattedNumber&text=${URLEncoder.encode(message, "UTF-8")}"
                        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url)).apply {
                            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        }
                        activity.startActivity(intent)
                    } catch (e: Exception) {
                        Toast.makeText(activity, "WhatsApp open nahi ho saka", Toast.LENGTH_SHORT).show()
                    }
                } else {
                    Toast.makeText(activity, "$query ka Number nahi mila", Toast.LENGTH_SHORT).show()
                }
            }
        }

        // --- 7. YOUTUBE AUTOMATION (Direct Video Search & Play) ---
        @JavascriptInterface
        fun playOnYouTube(query: String) {
            activity.runOnUiThread {
                try {
                    // Native YouTube App Search Intent
                    val intent = Intent(Intent.ACTION_SEARCH).apply {
                        setPackage("com.google.android.youtube")
                        putExtra("query", query)
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    }
                    activity.startActivity(intent)
                } catch (e: Exception) {
                    // Fallback: Agar YouTube App na ho toh Browser me khol do
                    val webIntent = Intent(
                        Intent.ACTION_VIEW, 
                        Uri.parse("https://www.youtube.com/results?search_query=${URLEncoder.encode(query, "UTF-8")}")
                    ).apply {
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    }
                    activity.startActivity(webIntent)
                }
            }
        }

        // --- 8. SPOTIFY AUTOMATION (Direct Song Search & Play) ---
        @JavascriptInterface
        fun playOnSpotify(query: String) {
            activity.runOnUiThread {
                try {
                    val intent = Intent(android.media.action.MEDIA_PLAY_FROM_SEARCH).apply {
                        setPackage("com.spotify.music")
                        putExtra(SearchManager.QUERY, query)
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    }
                    activity.startActivity(intent)
                } catch (e: Exception) {
                    Toast.makeText(activity, "Spotify app nahi mila Sir", Toast.LENGTH_SHORT).show()
                }
            }
        }

        // --- HELPER: SEARCH PHONE NUMBER FROM CONTACTS ---
        private fun getPhoneNumberFromContact(name: String): String? {
            return try {
                val cursor = activity.contentResolver.query(
                    ContactsContract.CommonDataKinds.Phone.CONTENT_URI,
                    arrayOf(ContactsContract.CommonDataKinds.Phone.NUMBER),
                    "${ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME} LIKE ?",
                    arrayOf("%$name%"),
                    null
                )
                var number: String? = null
                cursor?.use {
                    if (it.moveToFirst()) {
                        val index = it.getColumnIndex(ContactsContract.CommonDataKinds.Phone.NUMBER)
                        if (index != -1) {
                            number = it.getString(index)
                        }
                    }
                }
                number
            } catch (e: Exception) {
                null
            }
        }
    }
}
