package com.ballknowledge.logic

import java.io.IOException
import java.net.ConnectException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import javax.net.ssl.SSLException

/** Turns a failed fetch into one plain-English sentence. */
object FetchErrors {

    fun describe(source: String, t: Throwable?): String {
        val code = (t as? com.ballknowledge.data.HttpStatusException)?.code
        return when {
            t == null -> "Couldn't get data from $source."
            t is UnknownHostException || t is ConnectException ->
                "Couldn't reach $source — check your internet connection."
            t is SocketTimeoutException -> "$source took too long to answer — try again in a bit."
            code == 404 -> "$source doesn't have this data (not found)."
            code == 429 -> "$source is asking for fewer requests right now — try again later."
            code != null && code >= 500 -> "$source is having problems right now (error $code) — try again later."
            code != null -> "$source refused the request (error $code)."
            t is SSLException -> "Couldn't make a secure connection to $source."
            t is IOException -> "The connection to $source was interrupted — try again."
            // Anything else means the reply arrived but couldn't be understood.
            else -> "$source replied, but the data wasn't readable — its format may have changed."
        }
    }
}
