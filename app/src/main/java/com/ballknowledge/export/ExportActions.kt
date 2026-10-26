package com.ballknowledge.export

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.print.PrintAttributes
import android.print.PrintManager
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.core.content.FileProvider
import java.io.File

/** Android-side export plumbing: share, save to a file, print to PDF. */
object ExportActions {

    fun fileSafe(name: String): String =
        name.replace(Regex("""[^A-Za-z0-9._ -]"""), "").trim().replace(Regex("""\s+"""), "-").ifBlank { "ball-knowledge" }

    /** Opens the share sheet with the HTML attached as a file. */
    fun shareHtml(context: Context, html: String, fileName: String) {
        val dir = File(context.cacheDir, "exports").apply { mkdirs() }
        dir.listFiles()?.forEach { it.delete() }
        val file = File(dir, "${fileSafe(fileName)}.html")
        file.writeText(html, Charsets.UTF_8)
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.files", file)
        val send = Intent(Intent.ACTION_SEND).apply {
            type = "text/html"
            putExtra(Intent.EXTRA_STREAM, uri)
            putExtra(Intent.EXTRA_SUBJECT, fileName)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(Intent.createChooser(send, "Share match pages"))
    }

    /** Writes text to a file the user picked with the system "save" screen. */
    fun writeText(context: Context, uri: Uri, text: String) {
        context.contentResolver.openOutputStream(uri, "wt")?.use { it.write(text.toByteArray(Charsets.UTF_8)) }
            ?: throw IllegalStateException("Couldn't open the file for writing.")
    }

    fun readText(context: Context, uri: Uri): String =
        context.contentResolver.openInputStream(uri)?.use { it.readBytes().toString(Charsets.UTF_8) }
            ?: throw IllegalStateException("Couldn't open the file.")

    // Held so the WebView isn't garbage-collected before printing starts.
    private var printView: WebView? = null

    /** Opens Android's print screen, where "Save as PDF" is an option. */
    fun printHtml(context: Context, html: String, jobName: String) {
        val webView = WebView(context)
        printView = webView
        webView.webViewClient = object : WebViewClient() {
            override fun onPageFinished(view: WebView, url: String?) {
                val printManager = context.getSystemService(Context.PRINT_SERVICE) as PrintManager
                val adapter = view.createPrintDocumentAdapter(fileSafe(jobName))
                printManager.print(
                    jobName,
                    adapter,
                    PrintAttributes.Builder().setMediaSize(PrintAttributes.MediaSize.ISO_A4).build(),
                )
                printView = null
            }
        }
        webView.loadDataWithBaseURL(null, html, "text/html", "UTF-8", null)
    }
}
