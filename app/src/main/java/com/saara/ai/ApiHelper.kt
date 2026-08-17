package com.saara.ai

import android.util.Log
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.URL

object ApiHelper {
    fun sendQueryToBackend(serverUrl: String, prompt: String, callback: (String) -> Unit) {
        Thread {
            try {
                val url = URL(serverUrl)
                val connection = url.openConnection() as HttpURLConnection
                connection.requestMethod = "POST"
                connection.setRequestProperty("Content-Type", "application/json; utf-8")
                connection.setRequestProperty("Accept", "application/json")
                connection.doOutput = true

                val jsonInputString = "{\"prompt\": \"$prompt\"}"
                
                connection.outputStream.use { os ->
                    val input = jsonInputString.toByteArray(Charsets.UTF_8)
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
                    callback(response.toString())
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
