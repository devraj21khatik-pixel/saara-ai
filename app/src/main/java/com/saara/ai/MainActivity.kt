package com.saara.ai

import android.os.Bundle
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity

class MainActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        val textView = TextView(this).apply {
            text = "Saara AI Assistant is Running Natively!"
            textSize = 20f
            setPadding(50, 50, 50, 50)
        }
        setContentView(textView)
    }
}
