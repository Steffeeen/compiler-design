package edu.kit.kastel.vads.compiler

import java.nio.file.Files
import java.nio.file.Path

inline fun createTempFile(name: String, directory: Path, block: (Path) -> Unit) {
    require(name.isNotEmpty()) { "Temporary file name must not be empty" }
    require(Files.isDirectory(directory)) { "Provided path is not a directory: $directory" }

    val tempFile = directory.resolve(name)
    Files.createFile(tempFile)
    block(tempFile)
    Files.deleteIfExists(tempFile)
}

fun runProcessAndMaybePrintError(vararg command: String): Boolean {
    val process = ProcessBuilder(*command).start()
    val exitCode = process.waitFor()
    if (exitCode != 0) {
        System.err.println("${command[0]} returned with exit code $exitCode")
        System.err.println("${command[0]} output:")
        process.errorStream.transferTo(System.err)
        process.errorStream.close()
        return false
    }
    return true
}

fun isLinux(): Boolean = System.getProperty("os.name").lowercase().contains("linux")
fun isMac(): Boolean = System.getProperty("os.name").lowercase().contains("mac")
