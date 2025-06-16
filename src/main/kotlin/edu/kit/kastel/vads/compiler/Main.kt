package edu.kit.kastel.vads.compiler

import com.github.ajalt.clikt.completion.CompletionCandidates
import com.github.ajalt.clikt.core.CliktCommand
import com.github.ajalt.clikt.core.main
import com.github.ajalt.clikt.parameters.arguments.argument
import com.github.ajalt.clikt.parameters.options.flag
import com.github.ajalt.clikt.parameters.options.option
import com.github.ajalt.clikt.parameters.types.path
import edu.kit.kastel.vads.compiler.backend.LinearScanRegisterAllocator
import edu.kit.kastel.vads.compiler.backend.ir.asmIrToString
import edu.kit.kastel.vads.compiler.backend.lowerIrToAsmIr
import edu.kit.kastel.vads.compiler.backend.x86.X86Assembler
import edu.kit.kastel.vads.compiler.backend.x86.X86Assembly
import edu.kit.kastel.vads.compiler.backend.x86.X86CodeGenerator
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
import java.nio.file.StandardOpenOption
import kotlin.system.exitProcess

fun main(args: Array<String>) = CompilerOptions().main(args)

class CompilerOptions : CliktCommand() {
    val printAst by option("--print-ast", envvar = "PRINT_AST", help = "Print the AST").flag(default = false)
    val printIrToFile by option("--print-ir", envvar = "PRINT_IR", help = "Print the IR to a file").flag(default = false)
    val overwriteIrFile by option("--overwrite-ir", envvar = "OVERWRITE_IR", help = "Overwrite the IR file if it already exists").flag(default = false)
    val printAssembly by option("--print-assembly", envvar = "PRINT_ASSEMBLY", help = "Print the generated assembly").flag(default = false)

    val inputFile by argument("input_file", completionCandidates = CompletionCandidates.Path).path(mustExist = true, canBeFile = true, canBeDir = false)
    val outputFile by argument("output_file", completionCandidates = CompletionCandidates.Path).path()

    override fun run() {
        runCompiler()
    }
}

private fun CompilerOptions.runCompiler() {
    val parseResult = lexAndParse(inputFile)

    if (parseResult is ParseResult.Failure) {
        for (error in parseResult.errors) {
            System.err.println(error)
        }
        // exit code 42 indicates that the code was rejected by your lexer or parser
        exitProcess(42)
    }

    val program = (parseResult as ParseResult.Success).program

    val result = analyzeProgram(program)

    if (result.isNotEmpty()) {
        // exit code 7 indicates that the code was rejected by your semantic analysis
        System.err.println(result.joinToString("\n"))
        exitProcess(7)
    }

    if (printAst) {
        println(printAst(program))
    }

    val irProgram = buildIr(program)

    if (printIrToFile) {
        // currently only the main function exists, thus only it gets printed
        val dotFile = outputFile.toAbsolutePath().parent.resolve("graph.dot")
        if (overwriteIrFile || !Files.exists(dotFile)) {
            Files.writeString(dotFile, irProgram.graphs.find { it.name == "main" }!!.toDotVisualization(), StandardOpenOption.CREATE)
        } else {
            System.err.println("File '${dotFile.toAbsolutePath()}' already exists, skipping write.")
        }
    }
    val asmIr = lowerIrToAsmIr(irProgram)
    val asmIrString = asmIrToString(asmIr)
    println(asmIrString)

    val registerAllocations = asmIr.functions.associateWith { LinearScanRegisterAllocator.allocateRegisters(it) }

    val assembly = X86CodeGenerator().generateCode(asmIr, registerAllocations)

    if (printAssembly) {
        require(assembly is X86Assembly)
        val assemblyWithLineNumbers = assembly.assemblyCode.lines().mapIndexed { index, line ->
            "${index + 1}: $line"
        }.joinToString("\n")
        println(assemblyWithLineNumbers)
    }

    X86Assembler().assemble(assembly, outputFile)
    println()
}

context(options: CompilerOptions)
private fun lexAndParse(input: Path): ParseResult {
    val tokens = Lexer(Files.readString(input), options).lex()
    val tokenSource = TokenSource(tokens)
    return parse(tokenSource)
}
