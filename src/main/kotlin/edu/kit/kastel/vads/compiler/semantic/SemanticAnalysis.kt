package edu.kit.kastel.vads.compiler.semantic

import edu.kit.kastel.vads.compiler.CompilerOptions
import edu.kit.kastel.vads.compiler.Span
import edu.kit.kastel.vads.compiler.parser.AstNode
import edu.kit.kastel.vads.compiler.parser.visitor.NoOpVisitor
import edu.kit.kastel.vads.compiler.parser.visitor.RecursivePostorderVisitor
import edu.kit.kastel.vads.compiler.typechecker.TypeChecking
import edu.kit.kastel.vads.compiler.typechecker.TypeError

sealed interface SemanticError {
    data class InvalidIntegerLiteralRange(val node: AstNode.LiteralNode) : SemanticError
    data class MissingReturnStatement(val node: AstNode.FunctionNode, val span: Span) : SemanticError
    data class VariableAlreadyExists(val node: AstNode.NameNode) : SemanticError
    data class VariableNotDeclaredBeforeAssignment(val node: AstNode.NameNode) : SemanticError
    data class VariableNotInitialized(val node: AstNode.NameNode) : SemanticError
    data class BreakNotInLoop(val node: AstNode.BreakNode) : SemanticError
    data class ContinueNotInLoop(val node: AstNode.ContinueNode) : SemanticError
    data class DeclarationInForIncrement(val node: AstNode.DeclarationNode) : SemanticError
    data class TypeErrorWrapper(val error: TypeError) : SemanticError
}

interface SemanticAnalysis {
    fun analyze(program: AstNode.ProgramNode): List<SemanticError>
}

context(options: CompilerOptions)
fun analyzeProgram(program: AstNode.ProgramNode): List<SemanticError> {
    val analyses = listOf(
        ReturnAnalysis,
        BreakAndContinueWithinLoopAnalysis,
        IntegerLiteralRangeAnalysis,
        NoDeclarationInForIncrementAnalysis,
        VariableStatusAnalysis,
        TypeChecking,
    )

    for (analysis in analyses) {
        val errors = analysis.analyze(program)
        if (errors.isNotEmpty()) {
            return errors
        }
    }

    return listOf()
}

private object IntegerLiteralRangeAnalysis : SemanticAnalysis {
    override fun analyze(program: AstNode.ProgramNode): List<SemanticError> {
        val errors = mutableListOf<SemanticError>()

        val visitor = object : NoOpVisitor<Unit> {
            override fun visit(literalNode: AstNode.IntLiteralNode, data: Unit) {
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

private object NoDeclarationInForIncrementAnalysis : SemanticAnalysis {
    override fun analyze(program: AstNode.ProgramNode): List<SemanticError> {
        val errors = mutableListOf<SemanticError>()

        program.accept(RecursivePostorderVisitor(object : NoOpVisitor<Unit> {
            override fun visit(forNode: AstNode.ForNode, data: Unit) {
                if (forNode.increment is AstNode.DeclarationNode) {
                    errors += SemanticError.DeclarationInForIncrement(forNode.increment)
                }

                super.visit(forNode, data)
            }
        }), Unit)

        return errors
    }
}

private object BreakAndContinueWithinLoopAnalysis : SemanticAnalysis {
    override fun analyze(program: AstNode.ProgramNode): List<SemanticError> {
        return program.topLevelFunctions.flatMap { checkBreakNotInLoop(it.body.statements) }
    }

    private fun checkBreakNotInLoop(statements: List<AstNode>): List<SemanticError> {
        val errors = mutableListOf<SemanticError>()
        for (statement in statements) {
            when (statement) {
                is AstNode.BreakNode -> errors += SemanticError.BreakNotInLoop(statement)
                is AstNode.ContinueNode -> errors += SemanticError.ContinueNotInLoop(statement)
                is AstNode.BlockNode -> errors += checkBreakNotInLoop(statement.statements)
                is AstNode.IfNode -> {
                    errors += checkBreakNotInLoop(listOf(statement.body))
                    if (statement.elseStatement != null) {
                        errors += checkBreakNotInLoop(listOf(statement.elseStatement))
                    }
                }

                else -> {}
            }
        }
        return errors
    }


}
