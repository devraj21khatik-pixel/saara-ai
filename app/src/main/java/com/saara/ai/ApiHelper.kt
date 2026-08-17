package com.saara.ai

import android.util.Log
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.URL

object ApiHelper {
    fun sendQueryToBackend(
        serverUrl: String, 
        prompt: String, 
        isLive: Boolean = false, 
        callback: (String) -> Unit
    ) {
        Thread {
            try {
                val url = URL(serverUrl)
                val connection = url.openConnection() as HttpURLConnection
                connection.requestMethod = "POST"
                connection.setRequestProperty("Content-Type", "application/json; utf-8")
                connection.setRequestProperty("Accept", "application/json")
                connection.doOutput = true

                // Backend app.py "message" aur dynamic "is_live" key expect karta hai
                val jsonInput = JSONObject().apply {
                    put("message", prompt)
                    put("is_live", isLive)
                }
                
                connection.outputStream.use { os ->
                    val input = jsonInput.toString().toByteArray(Charsets.UTF_8)
                    os.write(input, 0, input.size)
                }

                val responseCode = connection.responseCode
                if (responseCode == HttpURLConnection.HTTP_OK) {
                    val br = BufferedReader(InputStreamReader(connection.inputStream, Charsets.UTF_8))
                    val response = StringBuilder()
                    var responseLine: String?
                    while (br.readLine().also { responseLine = it } != null) {
                        response.append(responseLine?.trim())
                    }
                    
                    // JSON Response parse karke clean 'reply' extract karna
                    val jsonResponse = JSONObject(response.toString())
                    val reply = jsonResponse.optString("reply", "No response from Saara")
                    callback(reply)
                } else {
                    callback("Error: Server returned HTTP code $responseCode")
                }
            } catch (e: Exception) {
                Log.e("ApiHelper", "Exception during API call", e)
                callback("Error: ${e.localizedMessage}")
            }
        }.start()
    }
}
