package com.cleantv.wifilauncher

import android.content.Context
import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.OutputStream
import java.net.ServerSocket
import java.net.Socket
import java.nio.charset.StandardCharsets
import kotlin.concurrent.thread

class WifiWebServer(
    private val context: Context,
    private val prefs: LauncherPrefs,
    private val port: Int = 8080,
    private val onStateChanged: (String) -> Unit
) {
    @Volatile
    private var running = false
    private var serverSocket: ServerSocket? = null

    fun startServer() {
        if (running) return
        running = true
        thread(start = true, isDaemon = true, name = "CleanTV-WiFi-HTTP") {
            try {
                serverSocket = ServerSocket(port)
                while (running) {
                    val client = serverSocket?.accept() ?: break
                    thread(start = true, isDaemon = true) {
                        handleClient(client)
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    private fun handleClient(socket: Socket) {
        socket.use { client ->
            val reader = BufferedReader(InputStreamReader(client.getInputStream(), StandardCharsets.UTF_8))
            val requestLine = reader.readLine() ?: return
            val parts = requestLine.split(" ")
            val method = parts.getOrNull(0) ?: "GET"
            val path = parts.getOrNull(1) ?: "/"

            var contentLength = 0
            var line: String?
            while (reader.readLine().also { line = it } != null && line!!.isNotEmpty()) {
                if (line!!.lowercase().startsWith("content-length:")) {
                    contentLength = line!!.substringAfter(":").trim().toIntOrNull() ?: 0
                }
            }

            val bodyChars = CharArray(contentLength)
            var readTotal = 0
            while (readTotal < contentLength) {
                val r = reader.read(bodyChars, readTotal, contentLength - readTotal)
                if (r == -1) break
                readTotal += r
            }
            val requestBody = String(bodyChars, 0, readTotal)

            val output: OutputStream = client.getOutputStream()
            when {
                method == "GET" && (path == "/" || path.startsWith("/index.html")) -> {
                    val htmlBytes = context.assets.open("web/index.html").readBytes()
                    sendResponse(output, "200 OK", "text/html; charset=utf-8", htmlBytes)
                }
                method == "GET" && path == "/api/state" -> {
                    val jsonBytes = prefs.getSavedStateJson().toByteArray(StandardCharsets.UTF_8)
                    sendResponse(output, "200 OK", "application/json; charset=utf-8", jsonBytes)
                }
                method == "POST" && path == "/api/state" -> {
                    if (requestBody.isNotBlank()) {
                        prefs.saveStateJson(requestBody)
                        onStateChanged(requestBody)
                    }
                    val okBytes = "{\"status\":\"ok\"}".toByteArray(StandardCharsets.UTF_8)
                    sendResponse(output, "200 OK", "application/json; charset=utf-8", okBytes)
                }
                else -> {
                    val notFound = "Not Found".toByteArray(StandardCharsets.UTF_8)
                    sendResponse(output, "404 Not Found", "text/plain; charset=utf-8", notFound)
                }
            }
        }
    }

    private fun sendResponse(out: OutputStream, status: String, contentType: String, payload: ByteArray) {
        val header = buildString {
            append("HTTP/1.1 $status\r\n")
            append("Content-Type: $contentType\r\n")
            append("Content-Length: ${payload.size}\r\n")
            append("Access-Control-Allow-Origin: *\r\n")
            append("Connection: close\r\n\r\n")
        }
        out.write(header.toByteArray(StandardCharsets.UTF_8))
        out.write(payload)
        out.flush()
    }

    fun stopServer() {
        running = false
        try {
            serverSocket?.close()
        } catch (_: Exception) {}
    }
}
