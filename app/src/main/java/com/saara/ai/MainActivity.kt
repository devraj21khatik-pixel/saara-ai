package com.saara.ai

import android.Manifest
import android.annotation.SuppressLint
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.webkit.JavascriptInterface
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat

class MainActivity : AppCompatActivity() {

    private lateinit var webView: WebView

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // 1. Request Audio Recording Permission on App Launch
        checkAudioPermission()

        webView = WebView(this).apply {
            settings.javaScriptEnabled = true
            settings.domStorageEnabled = true
            settings.mediaPlaybackRequiresUserGesture = false // Auto audio playback allow karne ke liye
            webViewClient = WebViewClient()
            
            // Connect JavaScript Bridge
            addJavascriptInterface(WebAppInterface(this@MainActivity, this), "AndroidBridge")
        }

        webView.loadUrl("file:///android_asset/index.html")
        setContentView(webView)
    }

    private fun checkAudioPermission() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) 
            != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(
                this,
                arrayOf(Manifest.permission.RECORD_AUDIO),
                101
            )
        }
    }

    class WebAppInterface(private val activity: MainActivity, private val webView: WebView) {
        
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

        @JavascriptInterface
        fun openScreenCast() {
            activity.runOnUiThread {
                // 1. Overlay Permission Check (Floating Bubble ke liye)
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && !Settings.canDrawOverlays(activity)) {
                    Toast.makeText(activity, "Please grant Overlay permission for Floating Icon", Toast.LENGTH_LONG).show()
                    val intent = Intent(
                        Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                        Uri.parse("package:${activity.packageName}")
                    )
                    activity.startActivity(intent)
                    return@runOnUiThread
                }

                // 2. Start Floating Bubble Service
                val serviceIntent = Intent(activity, FloatingWidgetService::class.java)
                activity.startService(serviceIntent)

                // 3. Open Wireless Display / Cast Settings
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

                // 4. Send Saara App to Background (Home Screen)
                activity.moveTaskToBack(true)
            }
        }
    }
}
