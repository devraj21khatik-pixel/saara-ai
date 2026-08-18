package com.saara.ai

import android.Manifest
import android.app.SearchManager
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
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
import android.util.Log
import android.view.Gravity
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import java.net.URLEncoder
import java.util.Locale

class MainActivity : AppCompatActivity(), TextToSpeech.OnInitListener {

    private lateinit var chatContainer: LinearLayout
    private lateinit var etInput: EditText
    private lateinit var btnMic: ImageButton
    private lateinit var btnSend: ImageButton
    private lateinit var chatScroll: ScrollView
    
    // Naye Action Buttons
    private lateinit var btnCastScreen: Button
    private lateinit var btnQuickChat: Button
    private lateinit var btnLiveVoice: Button

    private var speechRecognizer: SpeechRecognizer? = null
    private var textToSpeech: TextToSpeech? = null
    private var isLiveVoiceMode = false
    
    private val OVERLAY_PERMISSION_CODE = 2084

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        // 1. Initialize UI Elements
        chatContainer = findViewById(R.id.chatContainer)
        etInput = findViewById(R.id.etInput)
        btnMic = findViewById(R.id.btnMic)
        btnSend = findViewById(R.id.btnSend)
        chatScroll = findViewById(R.id.chatScroll)
        
        btnCastScreen = findViewById(R.id.btnCastScreen)
        btnQuickChat = findViewById(R.id.btnQuickChat)
        btnLiveVoice = findViewById(R.id.btnLiveVoice)

        // 2. Setup Permissions & Services
        checkAndRequestPermissions()
        initSpeechRecognizer()
        textToSpeech = TextToSpeech(this, this)

        // 3. Welcome Message
        addChatBubble("Namaste Sir! Main Saara hoon. Bataiye main aapki kya madad kar sakti hoon?", false)

        // 4. Button Clicks
        btnCastScreen.setOnClickListener {
            addChatBubble("Screen Cast settings khol rahi hoon Sir...", false)
            speakOut("Screen Cast settings khol rahi hoon Sir")
            openScreenCast()
        }

        // Quick Chat button ab Floating Bubble trigger karega
        btnQuickChat.setOnClickListener {
            checkOverlayPermissionAndStart()
        }

        btnLiveVoice.setOnClickListener {
            isLiveVoiceMode = !isLiveVoiceMode
            if (isLiveVoiceMode) {
                btnLiveVoice.setBackgroundColor(Color.parseColor("#8b5cf6")) // Active Color
                addChatBubble("Live Voice Mode ON. Ab main lagatar sun rahi hoon.", false)
                speakOut("Live Voice Mode ON.")
                startVoiceRecognition()
            } else {
                btnLiveVoice.setBackgroundColor(Color.parseColor("#334155")) // Normal Color
                addChatBubble("Live Voice Mode OFF.", false)
                speakOut("Live Voice Mode OFF kar diya hai.")
            }
        }

        btnMic.setOnClickListener { 
            isLiveVoiceMode = false // Normal mic click disables live mode
            btnLiveVoice.setBackgroundColor(Color.parseColor("#334155"))
            startVoiceRecognition() 
        }

        btnSend.setOnClickListener {
            val query = etInput.text.toString().trim()
            if (query.isNotEmpty()) {
                addChatBubble(query, true)
                processQuery(query)
                etInput.setText("")
            }
        }
    }

    // --- FLOATING OVERLAY PERMISSION & START LOGIC ---
    private fun checkOverlayPermissionAndStart() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && !Settings.canDrawOverlays(this)) {
            val intent = Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:$packageName"))
            startActivityForResult(intent, OVERLAY_PERMISSION_CODE)
            Toast.makeText(this, "Pehle Overlay Permission allow karein", Toast.LENGTH_SHORT).show()
        } else {
            startService(Intent(this, SaaraFloatingService::class.java))
            addChatBubble("Floating Saara active ho gayi hai!", false)
            speakOut("Floating Saara active ho gayi hai.")
        }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == OVERLAY_PERMISSION_CODE) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && Settings.canDrawOverlays(this)) {
                startService(Intent(this, SaaraFloatingService::class.java))
                addChatBubble("Floating Saara active ho gayi hai!", false)
            } else {
                Toast.makeText(this, "Permission Denied", Toast.LENGTH_SHORT).show()
            }
        }
    }

    // --- CHAT BUBBLE UI LOGIC ---
    private fun addChatBubble(message: String, isUser: Boolean) {
        val textView = TextView(this).apply {
            text = message
            setTextColor(Color.WHITE)
            textSize = 16f
            setPadding(35, 25, 35, 25)
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                gravity = if (isUser) Gravity.END else Gravity.START
                setMargins(0, 10, 0, 10)
            }
        }

        val shape = GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            cornerRadius = 30f
            setColor(if (isUser) Color.parseColor("#475569") else Color.parseColor("#3b2667"))
        }
        textView.background = shape

        chatContainer.addView(textView)
        chatScroll.post { chatScroll.fullScroll(ScrollView.FOCUS_DOWN) }
    }

    // --- PERMISSIONS ---
    private fun checkAndRequestPermissions() {
        val permissions = mutableListOf(
            Manifest.permission.RECORD_AUDIO, Manifest.permission.READ_CONTACTS,
            Manifest.permission.CALL_PHONE, Manifest.permission.SEND_SMS
        )
        val missing = permissions.filter { ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED }
        if (missing.isNotEmpty()) ActivityCompat.requestPermissions(this, missing.toTypedArray(), 101)
    }

    // --- SPEECH RECOGNITION (STT) & TEXT TO SPEECH (TTS) ---
    private fun initSpeechRecognizer() {
        if (SpeechRecognizer.isRecognitionAvailable(this)) {
            speechRecognizer = SpeechRecognizer.createSpeechRecognizer(this)
            speechRecognizer?.setRecognitionListener(object : RecognitionListener {
                override fun onReadyForSpeech(params: Bundle?) { etInput.hint = "Sun rahi hoon..." }
                override fun onBeginningOfSpeech() {}
                override fun onRmsChanged(rmsdB: Float) {}
                override fun onBufferReceived(buffer: ByteArray?) {}
                override fun onEndOfSpeech() { etInput.hint = "Ask Saara..." }
                override fun onError(error: Int) { 
                    etInput.hint = "Ask Saara..." 
                    if (isLiveVoiceMode) etInput.postDelayed({ startVoiceRecognition() }, 1000)
                }
                override fun onResults(results: Bundle?) {
                    val matches = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                    if (!matches.isNullOrEmpty()) {
                        addChatBubble(matches[0], true)
                        processQuery(matches[0])
                    }
                }
                override fun onPartialResults(partialResults: Bundle?) {}
                override fun onEvent(eventType: Int, params: Bundle?) {}
            })
        }
    }

    private fun startVoiceRecognition() {
        textToSpeech?.stop() 
        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, "hi-IN")
        }
        try { speechRecognizer?.startListening(intent) } catch (e: Exception) {}
    }

    override fun onInit(status: Int) {
        if (status == TextToSpeech.SUCCESS) textToSpeech?.setLanguage(Locale("hi", "IN"))
    }

    private fun speakOut(text: String) {
        textToSpeech?.speak(text, TextToSpeech.QUEUE_FLUSH, null, "")
    }

    // --- MAIN LOGIC ROUTER (Commands Processing) ---
    private fun processQuery(query: String) {
        val lowerQuery = query.lowercase()

        when {
            // 1. SCREENCAST
            lowerQuery.contains("screencast") || lowerQuery.contains("cast screen") -> {
                addChatBubble("Screen cast khol rahi hoon Sir.", false)
                speakOut("Screen cast khol rahi hoon Sir.")
                openScreenCast()
                restartLiveVoiceIfNeeded()
            }
            
            // 2. YOUTUBE AUTO-PLAY
            lowerQuery.contains("youtube") -> {
                val song = lowerQuery.replace(Regex("\\b(youtube|par|play|karo|chalao|chala|lagao|on)\\b"), "").trim()
                addChatBubble("YouTube par '$song' play kar rahi hoon...", false)
                speakOut("YouTube par $song play kar rahi hoon.")
                playOnYouTube(song)
                restartLiveVoiceIfNeeded()
            }
            
            // 3. SPOTIFY
            lowerQuery.contains("spotify") -> {
                val song = lowerQuery.replace(Regex("\\b(spotify|par|play|karo|chalao|chala|lagao|on)\\b"), "").trim()
                addChatBubble("Spotify par '$song' play kar rahi hoon...", false)
                speakOut("Spotify par $song play kar rahi hoon.")
                playOnSpotify(song)
                restartLiveVoiceIfNeeded()
            }
            
            // 4. GOOGLE SEARCH
            lowerQuery.contains("google") || lowerQuery.contains("search") -> {
                val searchItem = lowerQuery.replace(Regex("\\b(google|par|search|karo|batao|dhundo|kya|hai)\\b"), "").trim()
                addChatBubble("Google par '$searchItem' search kar rahi hoon...", false)
                speakOut("$searchItem search kar rahi hoon.")
                searchOnGoogle(searchItem)
                restartLiveVoiceIfNeeded()
            }
            
            // 5. CALLING
            lowerQuery.contains("call") || lowerQuery.contains("phone laga") -> {
                val target = lowerQuery.replace(Regex("\\b(call|phone|karo|akro|ko|laga|lagao|karna)\\b"), "").trim()
                addChatBubble("'$target' ko call laga rahi hoon...", false)
                speakOut("$target ko call laga rahi hoon.")
                makeCall(target)
                restartLiveVoiceIfNeeded()
            }

            // 6. WHATSAPP (Basic NLP)
            lowerQuery.contains("whatsapp") && lowerQuery.contains("message") -> {
                val target = lowerQuery.substringAfter("whatsapp par").substringBefore("ko").trim()
                val msg = lowerQuery.substringAfter("message bhejo").trim()
                addChatBubble("WhatsApp par message bhej rahi hoon...", false)
                speakOut("WhatsApp message bhej rahi hoon.")
                sendWhatsAppMessage(target, msg)
                restartLiveVoiceIfNeeded()
            }
            
            // 7. OPEN ANY APP
            lowerQuery.contains("open") || lowerQuery.contains("kholo") -> {
                val appName = lowerQuery.replace(Regex("\\b(open|kholo|app)\\b"), "").trim()
                addChatBubble("'$appName' open kar rahi hoon.", false)
                speakOut("$appName open kar rahi hoon.")
                openApp(appName)
                restartLiveVoiceIfNeeded()
            }
            
            // 8. AI CHAT FALLBACK (Vercel Server)
            else -> {
                val serverUrl = "https://saara-ai-lac.vercel.app/chat"
                try {
                    ApiHelper.sendQueryToBackend(serverUrl, query, isLive = false) { reply ->
                        runOnUiThread {
                            addChatBubble(reply, false)
                            speakOut(reply)
                            restartLiveVoiceIfNeeded(reply.length * 100L) // Wait for AI to finish speaking
                        }
                    }
                } catch (e: Exception) {
                    runOnUiThread { 
                        addChatBubble("Error connecting to AI", false)
                        restartLiveVoiceIfNeeded()
                    }
                }
            }
        }
    }

    // Helper function for Live Voice loop
    private fun restartLiveVoiceIfNeeded(delay: Long = 2000L) {
        if (isLiveVoiceMode) {
            etInput.postDelayed({ startVoiceRecognition() }, delay)
        }
    }

    // --- APP AUTOMATION FUNCTIONS ---

    private fun openScreenCast() {
        try {
            startActivity(Intent("android.settings.CAST_SETTINGS").apply { addFlags(Intent.FLAG_ACTIVITY_NEW_TASK) })
        } catch (e: Exception) {
            try {
                startActivity(Intent("android.settings.WIFI_DISPLAY_SETTINGS").apply { addFlags(Intent.FLAG_ACTIVITY_NEW_TASK) })
            } catch (ex: Exception) { Toast.makeText(this, "Screen Cast settings not found", Toast.LENGTH_SHORT).show() }
        }
    }

    private fun searchOnGoogle(query: String) {
        try {
            val intent = Intent(Intent.ACTION_WEB_SEARCH).apply {
                putExtra(SearchManager.QUERY, query)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            startActivity(intent)
        } catch (e: Exception) {
            val webIntent = Intent(Intent.ACTION_VIEW, Uri.parse("https://www.google.com/search?q=${URLEncoder.encode(query, "UTF-8")}"))
            startActivity(webIntent)
        }
    }

    private fun playOnYouTube(query: String) {
        try {
            val intent = Intent(MediaStore.INTENT_ACTION_MEDIA_PLAY_FROM_SEARCH).apply {
                setPackage("com.google.android.youtube")
                putExtra(SearchManager.QUERY, query)
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
        for (app in packages) {
            if (pm.getApplicationLabel(app).toString().contains(appName, true)) {
                pm.getLaunchIntentForPackage(app.packageName)?.let { startActivity(it.apply { addFlags(Intent.FLAG_ACTIVITY_NEW_TASK) }) }
                return
            }
        }
        addChatBubble("Sorry Sir, $appName phone mein nahi mila.", false)
    }

    private fun makeCall(name: String) {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CALL_PHONE) != PackageManager.PERMISSION_GRANTED) {
            addChatBubble("Sir, Call ki permission nahi hai.", false)
            checkAndRequestPermissions()
            return
        }
        val number = getPhoneNumberFromContact(name)
        if (number != null) {
            try {
                startActivity(Intent(Intent.ACTION_CALL, Uri.parse("tel:$number")).apply { addFlags(Intent.FLAG_ACTIVITY_NEW_TASK) })
            } catch (e: Exception) {
                startActivity(Intent(Intent.ACTION_DIAL, Uri.parse("tel:$number")).apply { addFlags(Intent.FLAG_ACTIVITY_NEW_TASK) })
            }
        } else {
            addChatBubble("Sir, mujhe '$name' ka number nahi mila.", false)
        }
    }

    private fun getPhoneNumberFromContact(name: String): String? {
        var number: String? = null
        try {
            val cursor = contentResolver.query(
                ContactsContract.CommonDataKinds.Phone.CONTENT_URI,
                arrayOf(ContactsContract.CommonDataKinds.Phone.NUMBER),
                "LOWER(${ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME}) LIKE LOWER(?)",
                arrayOf("%$name%"),
                null
            )
            cursor?.use {
                if (it.moveToFirst()) {
                    val index = it.getColumnIndex(ContactsContract.CommonDataKinds.Phone.NUMBER)
                    if (index != -1) number = it.getString(index)
                }
            }
        } catch (e: Exception) { Log.e("CallError", "Contact error", e) }
        return number
    }

    private fun sendWhatsAppMessage(name: String, message: String) {
        val number = getPhoneNumberFromContact(name)
        if (number != null) {
            try {
                val formattedNumber = if (number.startsWith("+")) number else "+91$number"
                val intent = Intent(Intent.ACTION_VIEW).apply {
                    data = Uri.parse("https://api.whatsapp.com/send?phone=$formattedNumber&text=${URLEncoder.encode(message, "UTF-8")}")
                    setPackage("com.whatsapp")
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                startActivity(intent)
            } catch (e: Exception) {
                addChatBubble("Sir, WhatsApp open nahi ho pa raha.", false)
            }
        } else {
            addChatBubble("Sir, mujhe '$name' ka number nahi mila.", false)
        }
    }

    override fun onDestroy() {
        speechRecognizer?.destroy()
        textToSpeech?.stop()
        textToSpeech?.shutdown()
        super.onDestroy()
    }
}
