package com.saara.ai

import android.Manifest
import android.app.SearchManager
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.ContactsContract
import android.provider.MediaStore
import android.provider.Settings
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.speech.tts.TextToSpeech
import android.util.Log  // <-- Ye line chhut gayi thi pichli baar!
import android.widget.Button
import android.widget.EditText
import android.widget.ImageButton
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import java.net.URLEncoder
import java.util.Locale

class MainActivity : AppCompatActivity(), TextToSpeech.OnInitListener {

    private lateinit var tvResponse: TextView
    private lateinit var etInput: EditText
    private lateinit var btnMic: ImageButton
    private lateinit var btnSend: Button
    private lateinit var chatScroll: ScrollView

    private var speechRecognizer: SpeechRecognizer? = null
    private var textToSpeech: TextToSpeech? = null
    private var chatHistory = StringBuilder("Namaste Sir! Main Saara hoon. Bataiye main aapki kya madad kar sakti hoon?\n\n")

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        // Initialize UI Elements
        tvResponse = findViewById(R.id.tvResponse)
        etInput = findViewById(R.id.etInput)
        btnMic = findViewById(R.id.btnMic)
        btnSend = findViewById(R.id.btnSend)
        chatScroll = findViewById(R.id.chatScroll)

        // Request Permissions
        checkAndRequestPermissions()

        // Initialize Native Speech Recognizer & TTS
        initSpeechRecognizer()
        textToSpeech = TextToSpeech(this, this)

        // Mic Button Logic
        btnMic.setOnClickListener {
            startVoiceRecognition()
        }

        // Send Button Logic
        btnSend.setOnClickListener {
            val query = etInput.text.toString().trim()
            if (query.isNotEmpty()) {
                appendChat("Aap: $query")
                processQuery(query)
                etInput.setText("")
            }
        }
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
            ActivityCompat.requestPermissions(this, missingPermissions.toTypedArray(), 101)
        }
    }

    private fun initSpeechRecognizer() {
        if (SpeechRecognizer.isRecognitionAvailable(this)) {
            speechRecognizer = SpeechRecognizer.createSpeechRecognizer(this)
            speechRecognizer?.setRecognitionListener(object : RecognitionListener {
                override fun onReadyForSpeech(params: Bundle?) {
                    etInput.hint = "Sun rahi hoon..."
                }
                override fun onBeginningOfSpeech() {}
                override fun onRmsChanged(rmsdB: Float) {}
                override fun onBufferReceived(buffer: ByteArray?) {}
                override fun onEndOfSpeech() {
                    etInput.hint = "Message likhein..."
                }
                override fun onError(error: Int) {
                    Toast.makeText(this@MainActivity, "Aawaaz samajh nahi aayi, phir try karein", Toast.LENGTH_SHORT).show()
                    etInput.hint = "Message likhein..."
                }
                override fun onResults(results: Bundle?) {
                    val matches = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                    if (!matches.isNullOrEmpty()) {
                        val spokenText = matches[0]
                        appendChat("Aap (Voice): $spokenText")
                        processQuery(spokenText)
                    }
                }
                override fun onPartialResults(partialResults: Bundle?) {}
                override fun onEvent(eventType: Int, params: Bundle?) {}
            })
        } else {
            Toast.makeText(this, "Speech recognition is device par support nahi karta", Toast.LENGTH_LONG).show()
        }
    }

    private fun startVoiceRecognition() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            checkAndRequestPermissions()
            return
        }
        textToSpeech?.stop() 
        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, "hi-IN")
            putExtra(RecognizerIntent.EXTRA_PROMPT, "Boliye Sir...")
        }
        speechRecognizer?.startListening(intent)
    }

    override fun onInit(status: Int) {
        if (status == TextToSpeech.SUCCESS) {
            val result = textToSpeech?.setLanguage(Locale("hi", "IN"))
            if (result == TextToSpeech.LANG_MISSING_DATA || result == TextToSpeech.LANG_NOT_SUPPORTED) {
                Log.e("TTS", "Language not supported") 
            }
        }
    }

    private fun speakOut(text: String) {
        textToSpeech?.speak(text, TextToSpeech.QUEUE_FLUSH, null, "")
    }

    private fun appendChat(text: String) {
        chatHistory.append(text).append("\n\n")
        tvResponse.text = chatHistory.toString()
        chatScroll.post { chatScroll.fullScroll(ScrollView.FOCUS_DOWN) }
    }

    private fun processQuery(query: String) {
        val lowerQuery = query.lowercase()

        when {
            lowerQuery.contains("open screencast") || lowerQuery.contains("cast screen") -> {
                appendChat("Saara: Screen cast khol rahi hoon Sir.")
                speakOut("Screen cast khol rahi hoon Sir.")
                openScreenCast()
            }
            lowerQuery.contains("play") && lowerQuery.contains("youtube") -> {
                val song = query.replace("play", "", true).replace("on youtube", "", true).replace("youtube", "", true).trim()
                appendChat("Saara: YouTube par '$song' play kar rahi hoon.")
                speakOut("YouTube par $song play kar rahi hoon.")
                playOnYouTube(song)
            }
            lowerQuery.contains("play") && lowerQuery.contains("spotify") -> {
                val song = query.replace("play", "", true).replace("on spotify", "", true).replace("spotify", "", true).trim()
                appendChat("Saara: Spotify par '$song' play kar rahi hoon.")
                speakOut("Spotify par $song play kar rahi hoon.")
                playOnSpotify(song)
            }
            lowerQuery.startsWith("open ") -> {
                val appName = query.replace("open ", "", true).trim()
                appendChat("Saara: '$appName' open kar rahi hoon.")
                speakOut("$appName open kar rahi hoon.")
                openApp(appName)
            }
            lowerQuery.startsWith("call ") -> {
                val target = query.replace("call ", "", true).trim()
                appendChat("Saara: '$target' ko call laga rahi hoon.")
                speakOut("$target ko call laga rahi hoon.")
                makeCall(target)
            }
            else -> {
                appendChat("Saara: (Thinking...)")
                val serverUrl = "https://saara-ai-lac.vercel.app/chat"
                try {
                    ApiHelper.sendQueryToBackend(serverUrl, query, isLive = false) { reply ->
                        runOnUiThread {
                            val lastIndex = chatHistory.lastIndexOf("Saara: (Thinking...)")
                            if (lastIndex != -1) {
                                chatHistory.delete(lastIndex, chatHistory.length)
                            }
                            appendChat("Saara: $reply")
                            speakOut(reply)
                        }
                    }
                } catch (e: Exception) {
                    runOnUiThread {
                        val lastIndex = chatHistory.lastIndexOf("Saara: (Thinking...)")
                        if (lastIndex != -1) {
                            chatHistory.delete(lastIndex, chatHistory.length)
                        }
                        appendChat("Saara: Api Error: ${e.message}")
                    }
                }
            }
        }
    }

    private fun openScreenCast() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && !Settings.canDrawOverlays(this)) {
            Toast.makeText(this, "Please grant Overlay permission", Toast.LENGTH_LONG).show()
            startActivity(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:$packageName")))
            return
        }
        try {
            startService(Intent(this, FloatingWidgetService::class.java))
        } catch (e: Exception) {}

        try {
            val intent = Intent("android.settings.CAST_SETTINGS").apply { addFlags(Intent.FLAG_ACTIVITY_NEW_TASK) }
            startActivity(intent)
        } catch (e: Exception) {
            try {
                val intent = Intent("android.settings.WIFI_DISPLAY_SETTINGS").apply { addFlags(Intent.FLAG_ACTIVITY_NEW_TASK) }
                startActivity(intent)
            } catch (ex: Exception) {
                Toast.makeText(this, "Screen Cast settings not found", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun playOnYouTube(query: String) {
        try {
            val intent = Intent(Intent.ACTION_SEARCH).apply {
                setPackage("com.google.android.youtube")
                putExtra("query", query)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            startActivity(intent)
        } catch (e: Exception) {
            val webIntent = Intent(Intent.ACTION_VIEW, Uri.parse("https://www.youtube.com/results?search_query=${URLEncoder.encode(query, "UTF-8")}"))
            startActivity(webIntent)
        }
    }

    private fun playOnSpotify(query: String) {
        try {
            val intent = Intent(MediaStore.INTENT_ACTION_MEDIA_PLAY_FROM_SEARCH).apply {
                setPackage("com.spotify.music")
                putExtra(SearchManager.QUERY, query)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            startActivity(intent)
        } catch (e: Exception) {
            Toast.makeText(this, "Spotify app nahi mila", Toast.LENGTH_SHORT).show()
        }
    }

    private fun openApp(appName: String) {
        val pm = packageManager
        val packages = pm.getInstalledApplications(PackageManager.GET_META_DATA)
        var appFound = false
        for (app in packages) {
            val label = pm.getApplicationLabel(app).toString()
            if (label.contains(appName, ignoreCase = true)) {
                val launchIntent = pm.getLaunchIntentForPackage(app.packageName)?.apply { addFlags(Intent.FLAG_ACTIVITY_NEW_TASK) }
                if (launchIntent != null) {
                    startActivity(launchIntent)
                    appFound = true
                    break
                }
            }
        }
        if (!appFound) Toast.makeText(this, "$appName nahi mila", Toast.LENGTH_SHORT).show()
    }

    private fun makeCall(query: String) {
        val number = getPhoneNumberFromContact(query) ?: query.replace(Regex("[^0-9+]"), "")
        if (number.isNotEmpty()) {
            try {
                startActivity(Intent(Intent.ACTION_CALL, Uri.parse("tel:$number")).apply { addFlags(Intent.FLAG_ACTIVITY_NEW_TASK) })
            } catch (e: Exception) {
                startActivity(Intent(Intent.ACTION_DIAL, Uri.parse("tel:$number")).apply { addFlags(Intent.FLAG_ACTIVITY_NEW_TASK) })
            }
        } else {
            Toast.makeText(this, "Contact nahi mila", Toast.LENGTH_SHORT).show()
        }
    }

    private fun getPhoneNumberFromContact(name: String): String? {
        return try {
            val cursor = contentResolver.query(
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
                    if (index != -1) number = it.getString(index)
                }
            }
            number
        } catch (e: Exception) { null }
    }

    override fun onDestroy() {
        speechRecognizer?.destroy()
        textToSpeech?.stop()
        textToSpeech?.shutdown()
        super.onDestroy()
    }
}
