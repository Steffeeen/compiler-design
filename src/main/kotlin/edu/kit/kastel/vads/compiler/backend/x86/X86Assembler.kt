package edu.kit.kastel.vads.compiler.backend.x86

import edu.kit.kastel.vads.compiler.backend.Assembler
import edu.kit.kastel.vads.compiler.backend.Assembly
import edu.kit.kastel.vads.compiler.backend.X86Architecture
import edu.kit.kastel.vads.compiler.createTempFile
import edu.kit.kastel.vads.compiler.isLinux
import edu.kit.kastel.vads.compiler.isMac
import edu.kit.kastel.vads.compiler.runProcessAndMaybePrintError
import java.nio.file.Files
import java.nio.file.Path

class X86Assembler : Assembler<X86Architecture> {
    override fun assemble(assembly: Assembly<X86Architecture>, path: Path) {
        require(assembly is X86Assembly)
        createTempFile("temp.s", path.toAbsolutePath().parent) { tempFile ->
            when {
                isLinux() -> assembleOnLinux(assembly.assemblyCode, tempFile, path)
                isMac() -> assembleOnMac(assembly.assemblyCode, tempFile, path)
                else -> System.err.println("Operating system ${System.getProperty("os.name")} is not supported")
            }
        }
    }

    private fun assembleOnLinux(assembly: String, tempFile: Path, binary: Path) {
        Files.writeString(tempFile, assembly)

        runProcessAndMaybePrintError("gcc", tempFile.toAbsolutePath().toString(), "-o", binary.toAbsolutePath().toString())
    }

    private fun assembleOnMac(assembly: String, tempFile: Path, binary: Path) {
        val fixedAssembly = assembly
            .replace(Regex("^main:", RegexOption.MULTILINE), "_main:")
            .replace(Regex("^\\.global main$", RegexOption.MULTILINE), "global _main")
            .replace(Regex("^\\.global ", RegexOption.MULTILINE), "global ")
            .replace(Regex("^\\.extern (\\w+)", RegexOption.MULTILINE), "extern _$1")
            .replace(Regex("^\\.text", RegexOption.MULTILINE), "section .text")
            .replace("#", ";")
            .replace(Regex(".type (.*) @function", RegexOption.MULTILINE), "")
            .replace(".intel_syntax noprefix", "")
            .replace("DWORD PTR", "DWORD")
        Files.writeString(tempFile, fixedAssembly)

        createTempFile("temp.o", tempFile.toAbsolutePath().parent) { objectFile ->
            val result = runProcessAndMaybePrintError("nasm", "-f", "macho64", "-o", objectFile.toAbsolutePath().toString(), tempFile.toAbsolutePath().toString())
            if (!result) {
                return
            }
            runProcessAndMaybePrintError(
                "clang",
                objectFile.toAbsolutePath().toString(),
                "-arch",
                "x86_64",
                "-o",
                binary.toAbsolutePath().toString()
            )
        }
    }
}