package edu.kit.kastel.vads.compiler

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
    val printAst: Boolean = true,
    val printIrToFile: Boolean = true,
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

    val irGraph = buildIr(program.topLevelFunctions.first())

    if (printIrToFile) {
        val dotFile = output.toAbsolutePath().parent.resolve("graph.dot")
        if (!Files.exists(dotFile)) {
            Files.writeString(dotFile, irGraph.toDotVisualization())
        } else {
            System.err.println("File '${dotFile.toAbsolutePath()}' already exists, skipping write.")
        }
    }
}

context(options: CompilerOptions)
private fun lexAndParse(input: Path): ParseResult {
    val tokens = Lexer(Files.readString(input), options).lex()
    val tokenSource = TokenSource(tokens)
    return parse(tokenSource)
}
