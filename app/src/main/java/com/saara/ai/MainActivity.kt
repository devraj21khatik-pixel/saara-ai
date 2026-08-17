package com.saara.ai

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.webkit.JavascriptInterface
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity

class MainActivity : AppCompatActivity() {

    private lateinit var webView: WebView

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

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

    class WebAppInterface(private val mContext: Context, private val webView: WebView) {
        
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
            try {
                // Open Wireless Display / Cast Settings
                val intent = Intent("android.settings.CAST_SETTINGS")
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                mContext.startActivity(intent)
            } catch (e: Exception) {
                try {
                    val intent = Intent("android.settings.WIFI_DISPLAY_SETTINGS")
                    intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    mContext.startActivity(intent)
                } catch (ex: Exception) {
                    Toast.makeText(mContext, "Screen Cast settings missing on this device", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }
}
