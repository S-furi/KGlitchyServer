package io.github.sfuri

import io.github.sfuri.BuggyServerUtils.toReadableHexString
import kotlin.system.exitProcess

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

    val data = BuggyServerUtils.getData()
    val digest = BuggyServerUtils.computeSHA256(data)
    println("SHA-256 of the whole data: ${digest.toReadableHexString()}")
    if (originalHash != null) {
        checkHashes(originalHash!!, digest.toReadableHexString())
    }
    exitProcess(0)
}