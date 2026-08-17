package com.saara.ai

import android.annotation.SuppressLint
import android.content.Context
import android.os.Bundle
import android.webkit.JavascriptInterface
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.appcompat.app.AppCompatActivity

class MainActivity : AppCompatActivity() {

    private lateinit var webView: WebView

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        webView = WebView(this).apply {
            settings.javaScriptEnabled = true
            settings.domStorageEnabled = true
            webViewClient = WebViewClient()
            
            // JavaScript ko Kotlin se connect karna
            addJavascriptInterface(WebAppInterface(this@MainActivity, this), "AndroidBridge")
        }

        // Local HTML UI load karna
        webView.loadUrl("file:///android_asset/index.html")
        setContentView(webView)
    }

    class WebAppInterface(private val mContext: Context, private val webView: WebView) {
        
        @JavascriptInterface
        fun sendToSaara(prompt: String) {
            // Sahi Vercel Backend URL
            val serverUrl = "https://saara-ai-lac.vercel.app/chat"
            
            ApiHelper.sendQueryToBackend(serverUrl, prompt) { result ->
                // Clean response formatting for JavaScript
                val safeResult = result.replace("'", "\\'").replace("\n", "\\n")
                
                // Response wapas HTML UI (`script.js`) mein bhej rahe hain
                webView.post {
                    webView.evaluateJavascript("receiveFromSaara('$safeResult')", null)
                }
            }
        }
    }
}
