package io.github.sfuri

import io.github.sfuri.BuggyServerUtils.toReadableHexString

fun checkHashes(original: String, computed: String) {
    require(original == computed) {
        "The final hash does not match the original hash."
    }
    println("Hashes match! Received data is correct.")
}

fun main(args: Array<String>) {
    var originalHash: String? = null

    if (args.isEmpty() || args[0] == "") {
        println("No original hash has been passed as argument, no final checks will be performed.")
    } else {
        originalHash = args[0]
    }

    val expectedLength = BuggyServerUtils.getDataLength()
    val data = BuggyServerUtils.getGlitchyData()

    if (expectedLength == data.size) {
        println("Data is not glitchy.")
        val digest = BuggyServerUtils.computeSHA256(data)
        println("SHA-256 of the whole data: ${digest.toReadableHexString()}")
        if (originalHash != null) {
            checkHashes(originalHash!!, digest.toReadableHexString())
        }
    } else {
        println("Received data partially (${data.size} out of $expectedLength bytes).")
    }

    val initialIdx = data.size
    var res = data.copyOf()

    BuggyServerUtils.getMissingChunks(initialIdx, expectedLength).forEach { res += it }

    val resDigest = BuggyServerUtils.computeSHA256(res)
    println("SHA-256 of the whole data: ${resDigest.toReadableHexString()}")

    if (originalHash != null) {
        checkHashes(originalHash!!, resDigest.toReadableHexString())
    }
}