#!/usr/bin/env kotlin

import Solution.HttpConnectionUtils.computeSHA256
import Solution.HttpConnectionUtils.toReadableHexString
import java.net.HttpURLConnection
import java.net.URI

object HttpConnectionUtils {
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

    fun getRange(start: Int? = null, end: Int) : ByteArray {
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

    private fun establishGetConnection() = (URI(SERVER_URL).toURL().openConnection() as HttpURLConnection).also { it.requestMethod = "GET" }

    fun ByteArray.toReadableHexString() = this.joinToString("") { "%02x".format(it) }
}

fun checkHashes(original: String, computed: String) {
    require(original == computed) {
        "The final hash does not match the original hash."
    }
    println("Hashes match! Received data is correct.")
}

var originalHash: String? = null

if (args.isEmpty() || args[0] == "") {
    println("No original hash has been passed as argument, no final checks will be performed.")
} else {
    originalHash = args[0]
}

val (expectedLength, data) = HttpConnectionUtils.getDataLength() to HttpConnectionUtils.getGlitchyData()

if (expectedLength == data.size) {
    println("Data is not glitchy.")
    val digest = computeSHA256(data)
    println("SHA-256 of the whole data: ${digest.toReadableHexString()}")
    if (originalHash != null) {
        checkHashes(originalHash!!, digest.toReadableHexString())
    }
} else {
    println("Received data partially (${data.size} out of $expectedLength bytes).")
}

val initialIdx = data.size
var res = data.copyOf()

HttpConnectionUtils.getMissingChunks(initialIdx, expectedLength).forEach { res += it }

val resDigest = computeSHA256(res)
println("SHA-256 of the whole data: ${resDigest.toReadableHexString()}")

if (originalHash != null) {
    checkHashes(originalHash!!, resDigest.toReadableHexString())
}
