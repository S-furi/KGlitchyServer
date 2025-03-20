package io.github.sfuri

import java.net.HttpURLConnection
import java.net.URI

object BuggyServerUtils {
    private const val SERVER_URL = "http://localhost:8080"

    fun getGlitchyData(log: Boolean = false): ByteArray {
        val connection = this.establishGetConnection()
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

    fun getDataLength(): Int {
        val connection = this.establishGetConnection()
        return try {
            connection.headerFields?.get("Content-Length")?.first()?.toInt() ?: 0
        } finally {
            connection.disconnect()
        }
    }

    private fun getRange(start: Int? = null, end: Int) : ByteArray {
        val connection = establishGetConnection()
        connection.setRequestProperty("Range", "bytes=${if (start != null) "$start-" else "0-" }$end")
        println("Requesting range: $start - $end")
        return try {
            connection.inputStream.buffered().use { it.readAllBytes() }
        } finally {
            connection.disconnect()
        }
    }

    fun getMissingChunks(initialIdx: Int, finalLength: Int, chunkSize: Int = 64 * 1024): Sequence<ByteArray> =
        generateSequence(initialIdx) { prev ->
            val next = prev + chunkSize
            if (next < finalLength) next else null
        }.map {
            Thread.sleep(500)
            getRange(it, (it + chunkSize).coerceAtMost(finalLength))
        }

    fun computeSHA256(input: ByteArray): ByteArray =
        java.security.MessageDigest
            .getInstance("SHA-256")
            .digest(input)

    private fun establishGetConnection() =
        (URI(SERVER_URL).toURL().openConnection() as HttpURLConnection).also { it.requestMethod = "GET" }

    fun ByteArray.toReadableHexString() =
        this.joinToString("") { "%02x".format(it) }
}