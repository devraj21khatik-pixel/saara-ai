package com.saara.ai

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.OutputStreamWriter
import java.net.HttpURLConnection
import java.net.URL

class ApiHelper {

    // Network request strictly runs off the UI thread (Dispatchers.IO)
    suspend fun sendQueryToBackend(userQuery: String, serverUrl: String): String = withContext(Dispatchers.IO) {
        try {
            val url = URL(serverUrl)
            val conn = url.openConnection() as HttpURLConnection
            conn.requestMethod = "POST"
            conn.setRequestProperty("Content-Type", "application/json")
            conn.doOutput = true
            conn.connectTimeout = 8000 // 8 second timeout
            conn.readTimeout = 8000

            // Request Payload
            val jsonInput = JSONObject().apply {
                put("query", userQuery)
            }

            val writer = OutputStreamWriter(conn.outputStream)
            writer.write(jsonInput.toString())
            writer.flush()
            writer.close()

            // Response handling
            if (conn.responseCode == HttpURLConnection.HTTP_OK) {
                val responseText = conn.inputStream.bufferedReader().use { it.readText() }
                val jsonResponse = JSONObject(responseText)
                return@withContext jsonResponse.optString("reply", "Response processed.")
            } else {
                return@withContext "Server error (${conn.responseCode})"
            }
        } catch (e: Exception) {
            e.printStackTrace()
            return@withContext "Network delay, trying again..."
        }
    }
}
