package io.github.sfuri

import java.net.HttpURLConnection
import java.net.URI

object BuggyServerUtils {
    private const val SERVER_URL = "http://localhost:8080"
    private val dataLength = this.establishGetConnection(SERVER_URL).let { connection ->
        try {
            connection.headerFields?.get("Content-Length")?.first()?.toInt() ?: 0
        } finally {
            connection.disconnect()
        }
    }

    fun getData(): ByteArray {
        val data = this.getInitialData()
        return data + this.getRestOfData(initialIdx = data.size, endIdx = this.dataLength)
    }

    private tailrec fun getRestOfData(endIdx: Int, initialIdx: Int = 0, acc: ByteArray = byteArrayOf()): ByteArray {
        val (data, expectedLength) = this.getRange(start = initialIdx, end = endIdx)
        val curr = acc + data

        return if (data.size == expectedLength) {
            curr
        } else {
            this.getRestOfData(initialIdx = initialIdx + data.size, endIdx = this.dataLength, acc = curr)
        }
    }

    private fun getInitialData(log: Boolean = false, serverUrl: String? = null): ByteArray {
        val connection = this.establishGetConnection(serverUrl)
        return try {
            if (log) {
                println("Response code: ${connection.responseCode}")
                println("Response message: ${connection.responseMessage}")
                println("Headers:")
                connection.headerFields.forEach { (k, v) -> println("$k: $v") }
            }
            connection.inputStream.buffered().use { it.readAllBytes() }
        } finally {
            connection.disconnect()
        }
    }

    private fun getRange(start: Int? = null, end: Int, serverUrl: String? = null) : Pair<ByteArray, Int> {
        val connection = establishGetConnection(serverUrl)
        connection.setRequestProperty("Range", "bytes=${if (start != null) "$start-" else "0-" }$end")
        println("Requesting range: $start - $end")
        return try {
            val data = connection.inputStream.buffered().use { it.readAllBytes() }
            val length = connection.headerFields?.get("Content-Length")?.first()?.toInt() ?: 0
            return data to length
        } finally {
            connection.disconnect()
        }
    }

    fun computeSHA256(input: ByteArray): ByteArray =
        java.security.MessageDigest
            .getInstance("SHA-256")
            .digest(input)

    private fun establishGetConnection(serverUrl: String?) =
        (URI(serverUrl ?: SERVER_URL).toURL().openConnection() as HttpURLConnection).also { it.requestMethod = "GET" }

    fun ByteArray.toReadableHexString() =
        this.joinToString("") { "%02x".format(it) }
}