package edu.kit.kastel.vads.compiler

import edu.kit.kastel.vads.compiler.backend.X86Assembly
import edu.kit.kastel.vads.compiler.backend.generateX86Assembly
import edu.kit.kastel.vads.compiler.ir.buildIr
import edu.kit.kastel.vads.compiler.ir.util.toDotVisualization
import edu.kit.kastel.vads.compiler.lexer.Lexer
import edu.kit.kastel.vads.compiler.parser.ParseResult
import edu.kit.kastel.vads.compiler.parser.TokenSource
import edu.kit.kastel.vads.compiler.parser.parse
import edu.kit.kastel.vads.compiler.parser.printAst
import edu.kit.kastel.vads.compiler.semantic.analyzeProgram
import java.nio.file.Files
import java.nio.file.Path
import kotlin.system.exitProcess

data class CompilerOptions(
    val printAst: Boolean = false,
    val printIrToFile: Boolean = false,
)

fun main(args: Array<String>) = with(CompilerOptions()) {
    if (args.size != 2) {
        System.err.println("Invalid arguments: Expected one input file and one output file")
        exitProcess(3)
    }

    val input = Path.of(args[0])
    val output = Path.of(args[1])
    val parseResult = lexAndParse(input)

    if (parseResult is ParseResult.Failure) {
        for (error in parseResult.errors) {
            System.err.println(error)
        }
        // exit code 42 indicates that the code was rejected by your lexer or parser
        exitProcess(42)
    }

    val program = (parseResult as ParseResult.Success).program

    val result = analyzeProgram(program)

    if (result != null) {
        // exit code 7 indicates that the code was rejected by your semantic analysis
        System.err.println(result)
        exitProcess(7)
    }

    if (printAst) {
        println(printAst(program))
    }

    val irGraphs = program.topLevelFunctions.map { buildIr(it) }

    if (printIrToFile) {
        // currently only the main function exists, thus only it gets printed
        val dotFile = output.toAbsolutePath().parent.resolve("graph.dot")
        if (!Files.exists(dotFile)) {
            Files.writeString(dotFile, irGraphs.find { it.name == "main" }!!.toDotVisualization())
        } else {
            System.err.println("File '${dotFile.toAbsolutePath()}' already exists, skipping write.")
        }
    }

    val assembly = generateX86Assembly(irGraphs)
    assembly.assembleTo(output)
}

context(options: CompilerOptions)
private fun lexAndParse(input: Path): ParseResult {
    val tokens = Lexer(Files.readString(input), options).lex()
    val tokenSource = TokenSource(tokens)
    return parse(tokenSource)
}

private fun X86Assembly.assembleTo(path: Path) {
    val tempFile = path.toAbsolutePath().parent.resolve("temp.s")
    if (!Files.exists(tempFile)) {
        Files.createFile(tempFile)
    }

    when {
        isLinux() -> assembleOnLinux(assembly, tempFile, path)
        isMac() -> assembleOnMac(assembly, tempFile, path)
        else -> System.err.println("Operating system ${System.getProperty("os.name")} is not supported")
    }

    Files.delete(tempFile)
}

private fun assembleOnLinux(assembly: String, tempFile: Path, binary: Path) {
    Files.writeString(tempFile, assembly)

    runProcessAndMaybePrintError("gcc", tempFile.toAbsolutePath().toString(), "-o", binary.toAbsolutePath().toString())
}

private fun assembleOnMac(assembly: String, tempFile: Path, binary: Path) {
    val fixedAssembly = assembly
        .replace("main", "_main")
        .replace(".global", "global")
        .replace(".intel_syntax noprefix", "")
    Files.writeString(tempFile, fixedAssembly)

    val objectFile = tempFile.toAbsolutePath().parent.resolve("temp.o")

    val result = runProcessAndMaybePrintError("nasm", "-f", "macho64", "-o", objectFile.toAbsolutePath().toString(), tempFile.toAbsolutePath().toString())
    if (!result) {
        return
    }
    runProcessAndMaybePrintError(
        "ld",
        "-o",
        binary.toAbsolutePath().toString(),
        objectFile.toAbsolutePath().toString(),
        "-L/Library/Developer/CommandLineTools/SDKs/MacOSX.sdk/usr/lib",
        "-lSystem",
        "-macos_version_min",
        "10.14"
    )

    Files.delete(objectFile)
}

private fun runProcessAndMaybePrintError(vararg command: String): Boolean {
    val process = ProcessBuilder(*command).start()
    val exitCode = process.waitFor()
    if (exitCode != 0) {
        System.err.println("${command[0]} returned with exit code $exitCode")
        System.err.println("${command[0]} output:")
        process.errorStream.transferTo(System.err)
        return false
    }
    return true
}

private fun isLinux(): Boolean = System.getProperty("os.name").lowercase().contains("linux")
private fun isMac(): Boolean = System.getProperty("os.name").lowercase().contains("mac")

