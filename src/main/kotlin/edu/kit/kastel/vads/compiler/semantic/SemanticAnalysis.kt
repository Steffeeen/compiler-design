package edu.kit.kastel.vads.compiler.semantic

import edu.kit.kastel.vads.compiler.CompilerOptions
import edu.kit.kastel.vads.compiler.parser.AstNode
import edu.kit.kastel.vads.compiler.parser.visitor.NoOpVisitor
import edu.kit.kastel.vads.compiler.parser.visitor.RecursivePostorderVisitor

sealed interface SemanticError {
    data class InvalidIntegerLiteralRange(val node: AstNode.LiteralNode) : SemanticError
    data class NoReturnStatement(val node: AstNode.FunctionNode) : SemanticError
    data class VariableAlreadyExists(val node: AstNode.NameNode) : SemanticError
    data class VariableNotDeclaredBeforeAssignment(val node: AstNode.NameNode) : SemanticError
    data class VariableNotInitialized(val node: AstNode.NameNode) : SemanticError
}

interface SemanticAnalysis {
    fun analyze(program: AstNode.ProgramNode): List<SemanticError>
}

context(options: CompilerOptions)
fun analyzeProgram(program: AstNode.ProgramNode): SemanticError? {
    val analyses = listOf(
        ReturnAnalysis,
        IntegerLiteralRangeAnalysis,
        VariableStatusAnalysis
    )

    for (analysis in analyses) {
        val result = analysis.analyze(program)
        if (result.isNotEmpty()) {
            return result.first()
        }
    }

    return null
}

private object IntegerLiteralRangeAnalysis : SemanticAnalysis {
    override fun analyze(program: AstNode.ProgramNode): List<SemanticError> {
        val errors = mutableListOf<SemanticError>()

        val visitor = object : NoOpVisitor<Unit?> {
            override fun visit(literalNode: AstNode.LiteralNode, data: Unit?) {
                if (literalNode.parseValue() != null) {
                    return super.visit(literalNode, data)
                }

                errors += SemanticError.InvalidIntegerLiteralRange(literalNode)
                return super.visit(literalNode, data)
            }
        }

        program.accept(RecursivePostorderVisitor(visitor), null)
        return errors
    }
}

/**
 * Checks that functions return.
 * Currently only works for straight-line code.
 */
private object ReturnAnalysis : SemanticAnalysis {
    override fun analyze(program: AstNode.ProgramNode): List<SemanticError> {
        return program.topLevelFunctions.flatMap { analyzeFunction(it) }
    }

    private fun analyzeFunction(functionNode: AstNode.FunctionNode): List<SemanticError> {
        val hasReturn = functionNode.body.statements.any { it is AstNode.ReturnNode }
        if (!hasReturn) {
            return listOf(SemanticError.NoReturnStatement(functionNode))
        }
        return listOf()
    }
}
