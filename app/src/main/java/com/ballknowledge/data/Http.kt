package com.ballknowledge.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL

/** The server answered, but with an error code (e.g. 404, 503). */
class HttpStatusException(val code: Int, url: String) : IOException("HTTP $code for $url")

/** Minimal GET helper using Android's built-in HTTP client (no extra libraries). */
object Http {
    suspend fun get(url: String): String = withContext(Dispatchers.IO) {
        val conn = URL(url).openConnection() as HttpURLConnection
        try {
            conn.connectTimeout = 15_000
            conn.readTimeout = 25_000
            conn.setRequestProperty("Accept", "application/json")
            val code = conn.responseCode
            if (code !in 200..299) throw HttpStatusException(code, url)
            conn.inputStream.bufferedReader(Charsets.UTF_8).use { it.readText() }
        } finally {
            conn.disconnect()
        }
    }
}
