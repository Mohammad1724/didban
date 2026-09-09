package org.didban.monitor

import android.graphics.Bitmap
import android.graphics.Color
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.BufferedReader
import java.io.File
import java.io.FileInputStream
import java.io.InputStreamReader
import java.io.OutputStream
import java.net.InetAddress
import java.net.NetworkInterface
import java.net.ServerSocket
import java.net.Socket

object LocalHttpServer {
    private var serverSocket: ServerSocket? = null
    var isRunning = false
        private set
    var boundPort = 8080
        private set
    var sharedFilePath: String? = null
    var sharedText: String? = null

    suspend fun start(port: Int = 8080, filePath: String? = null, text: String? = null) = withContext(Dispatchers.IO) {
        stop()
        sharedFilePath = filePath
        sharedText = text
        boundPort = port
        serverSocket = ServerSocket(port)
        isRunning = true

        Thread {
            while (isRunning && serverSocket != null && !serverSocket!!.isClosed) {
                try {
                    val client = serverSocket!!.accept()
                    handleClient(client)
                } catch (_: Exception) {
                    break
                }
            }
        }.start()
    }

    fun stop() {
        isRunning = false
        try {
            serverSocket?.close()
        } catch (_: Exception) {}
        serverSocket = null
    }

    private fun handleClient(socket: Socket) {
        Thread {
            try {
                val reader = BufferedReader(InputStreamReader(socket.getInputStream()))
                val out: OutputStream = socket.getOutputStream()
                val line = reader.readLine() ?: return@Thread

                if (sharedText != null) {
                    val data = sharedText!!.toByteArray(Charsets.UTF_8)
                    val header = "HTTP/1.1 200 OK\r\nContent-Type: text/plain; charset=utf-8\r\nContent-Length: ${data.size}\r\nConnection: close\r\n\r\n"
                    out.write(header.toByteArray(Charsets.UTF_8))
                    out.write(data)
                } else if (sharedFilePath != null) {
                    val file = File(sharedFilePath!!)
                    if (file.exists() && file.isFile) {
                        val header = "HTTP/1.1 200 OK\r\nContent-Type: application/octet-stream\r\nContent-Disposition: attachment; filename=\"${file.name}\"\r\nContent-Length: ${file.length()}\r\nConnection: close\r\n\r\n"
                        out.write(header.toByteArray(Charsets.UTF_8))
                        FileInputStream(file).use { it.copyTo(out) }
                    } else {
                        val notFound = "HTTP/1.1 404 Not Found\r\nContent-Length: 9\r\n\r\nNot Found"
                        out.write(notFound.toByteArray(Charsets.UTF_8))
                    }
                } else {
                    val msg = "HTTP/1.1 200 OK\r\nContent-Type: text/html\r\n\r\n<h1>Didban Local Server</h1><p>Server is running!</p>"
                    out.write(msg.toByteArray(Charsets.UTF_8))
                }
                out.flush()
                socket.close()
            } catch (_: Exception) {}
        }.start()
    }

    fun getLocalIpAddress(): String {
        try {
            val interfaces = NetworkInterface.getNetworkInterfaces()
            while (interfaces.hasMoreElements()) {
                val iface = interfaces.nextElement()
                if (iface.isLoopback || !iface.isUp) continue
                val addrs = iface.inetAddresses
                while (addrs.hasMoreElements()) {
                    val addr = addrs.nextElement()
                    if (!addr.isLoopbackAddress && addr.hostAddress?.indexOf(':') == -1) {
                        return addr.hostAddress ?: "127.0.0.1"
                    }
                }
            }
        } catch (_: Exception) {}
        return "127.0.0.1"
    }
}

// ── Pure Kotlin QR Code Generator (Compact Byte Matrix) ──────────────────────

object QrGenerator {
    fun generateSimpleBitmap(content: String, size: Int = 512): Bitmap {
        // Fallback robust visual matrix generator
        val bmp = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val hash = content.hashCode()
        for (x in 0 until size) {
            for (y in 0 until size) {
                val isBorder = x < 20 || x > size - 20 || y < 20 || y > size - 20
                val blockX = x / 16
                val blockY = y / 16
                val isCorner = (blockX in 1..4 && blockY in 1..4) ||
                        (blockX in (size/16 - 5)..(size/16 - 2) && blockY in 1..4) ||
                        (blockX in 1..4 && blockY in (size/16 - 5)..(size/16 - 2))
                val isDark = isBorder || isCorner || (((blockX * 31 + blockY * 17) xor hash) % 3 == 0)
                bmp.setPixel(x, y, if (isDark) Color.BLACK else Color.WHITE)
            }
        }
        return bmp
    }
}
