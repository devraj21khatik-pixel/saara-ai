package com.saara.ai

import android.os.Bundle
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity

class MainActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // UI Layout programmatic taur par bana rahe hain
        val layout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(40, 40, 40, 40)
        }

        val titleView = TextView(this).apply {
            text = "Saara AI Native Assistant"
            textSize = 22f
            setPadding(0, 0, 0, 20)
        }
        layout.addView(titleView)

        val inputField = EditText(this).apply {
            hint = "Saara se kuch poochhein..."
            setPadding(20, 20, 20, 20)
        }
        layout.addView(inputField)

        val submitButton = Button(this).apply {
            text = "Send to Saara"
        }
        layout.addView(submitButton)

        val responseView = TextView(this).apply {
            text = "Response yahan dikhega..."
            textSize = 16f
            setPadding(0, 30, 0, 0)
        }
        layout.addView(responseView)

        submitButton.setOnClickListener {
            val prompt = inputField.text.toString()
            if (prompt.isNotBlank()) {
                responseView.text = "Soch rahi hoon..."
                
                // Yahan apna Vercel backend ka URL daalein
                val serverUrl = "https://your-vercel-backend-url.vercel.app/api/chat"
                
                ApiHelper.sendQueryToBackend(serverUrl, prompt) { result ->
                    runOnUiThread {
                        responseView.text = result
                    }
                }
            }
        }

        setContentView(layout)
    }
}
