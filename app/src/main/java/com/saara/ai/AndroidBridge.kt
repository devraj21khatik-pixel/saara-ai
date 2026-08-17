package com.saara.ai

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.ContactsContract
import android.webkit.JavascriptInterface
import android.widget.Toast
import java.net.URLEncoder

class AndroidBridge(private val context: Context) {

    // 1. KISI BHI APP KO KHOLNA
    @JavascriptInterface
    fun openApp(appName: String) {
        val pm = context.packageManager
        val packages = pm.getInstalledApplications(PackageManager.GET_META_DATA)
        var appFound = false

        for (app in packages) {
            val label = pm.getApplicationLabel(app).toString()
            if (label.contains(appName, ignoreCase = true)) {
                val launchIntent = pm.getLaunchIntentForPackage(app.packageName)
                if (launchIntent != null) {
                    launchIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    context.startActivity(launchIntent)
                    appFound = true
                    break
                }
            }
        }
        if (!appFound) {
            Toast.makeText(context, "$appName nahi mila Sir", Toast.LENGTH_SHORT).show()
        }
    }

    // 2. CALL LAGAANA (Naam ya Number dono se)
    @JavascriptInterface
    fun makeCall(query: String) {
        val number = getPhoneNumberFromContact(query) ?: query.replace(Regex("[^0-9+]"), "")
        if (number.isNotEmpty()) {
            val intent = Intent(Intent.ACTION_CALL).apply {
                data = Uri.parse("tel:$number")
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            }
            try {
                context.startActivity(intent)
            } catch (e: Exception) {
                // Runtime Permission fallback
                val dialIntent = Intent(Intent.ACTION_DIAL, Uri.parse("tel:$number")).apply {
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK
                }
                context.startActivity(dialIntent)
            }
        } else {
            Toast.makeText(context, "$query ka Contact nahi mila", Toast.LENGTH_SHORT).show()
        }
    }

    // 3. SMS BHEJNA
    @JavascriptInterface
    fun sendSMS(query: String, message: String) {
        val number = getPhoneNumberFromContact(query) ?: query.replace(Regex("[^0-9+]"), "")
        if (number.isNotEmpty()) {
            val intent = Intent(Intent.ACTION_SENDTO).apply {
                data = Uri.parse("smsto:$number")
                putExtra("sms_body", message)
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            }
            context.startActivity(intent)
        }
    }

    // 4. WHATSAPP MESSAGE BHEJNA
    @JavascriptInterface
    fun sendWhatsApp(query: String, message: String) {
        val rawNumber = getPhoneNumberFromContact(query) ?: query.replace(Regex("[^0-9]"), "")
        
        // India (+91) Country Code formatting if missing
        val formattedNumber = if (rawNumber.length == 10) "91$rawNumber" else rawNumber

        try {
            val url = "https://api.whatsapp.com/send?phone=$formattedNumber&text=${URLEncoder.encode(message, "UTF-8")}"
            val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url)).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            }
            context.startActivity(intent)
        } catch (e: Exception) {
            Toast.makeText(context, "WhatsApp open nahi ho saka", Toast.LENGTH_SHORT).show()
        }
    }

    // HELPER: Phone ke Contacts me se Naam dhoondhne ke liye
    private fun getPhoneNumberFromContact(name: String): String? {
        val cursor = context.contentResolver.query(
            ContactsContract.CommonDataKinds.Phone.CONTENT_URI,
            arrayOf(ContactsContract.CommonDataKinds.Phone.NUMBER),
            "${ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME} LIKE ?",
            arrayOf("%$name%"),
            null
        )
        cursor?.use {
            if (it.moveToFirst()) {
                val index = it.getColumnIndex(ContactsContract.CommonDataKinds.Phone.NUMBER)
                if (index != -1) return it.getString(index)
            }
        }
        return null
    }
}
