package edu.kit.kastel.vads.compiler.semantic

import edu.kit.kastel.vads.compiler.CompilerOptions
import edu.kit.kastel.vads.compiler.Span
import edu.kit.kastel.vads.compiler.parser.AstNode
import edu.kit.kastel.vads.compiler.parser.visitor.NoOpVisitor
import edu.kit.kastel.vads.compiler.parser.visitor.RecursivePostorderVisitor

sealed interface SemanticError {
    data class InvalidIntegerLiteralRange(val node: AstNode.LiteralNode) : SemanticError
    data class MissingReturnStatement(val node: AstNode.FunctionNode, val span: Span) : SemanticError
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

        val visitor = object : NoOpVisitor<Unit> {
            override fun visit(literalNode: AstNode.LiteralNode, data: Unit) {
                require(literalNode is AstNode.IntLiteralNode) { TODO("Only IntLiteralNode is supported") }
                if (literalNode.parseValue() != null) {
                    return super.visit(literalNode, data)
                }

                errors += SemanticError.InvalidIntegerLiteralRange(literalNode)
                return super.visit(literalNode, data)
            }
        }

        program.accept(RecursivePostorderVisitor(visitor), Unit)
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
        if (hasReturn(functionNode.body.statements)) {
            return listOf()
        }
        return listOf(SemanticError.MissingReturnStatement(functionNode, functionNode.body.statements.last().span))
    }

    private fun hasReturn(statements: List<AstNode.StatementNode>): Boolean {
        for (statement in statements) {
            if (hasReturn(statement)) {
                return true
            }
        }
        return false
    }

    private fun hasReturn(statement: AstNode.StatementNode): Boolean {
        return when (statement) {
            is AstNode.ReturnNode -> true
            is AstNode.BlockNode -> hasReturn(statement.statements)
            is AstNode.IfNode -> hasReturn(statement.body) &&
                    statement.elseStatement != null && hasReturn(statement.elseStatement)

            else -> false
        }
    }
}
