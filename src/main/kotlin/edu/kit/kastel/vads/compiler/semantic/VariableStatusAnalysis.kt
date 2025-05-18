package edu.kit.kastel.vads.compiler.semantic

import edu.kit.kastel.vads.compiler.lexer.Token
import edu.kit.kastel.vads.compiler.parser.AstNode
import edu.kit.kastel.vads.compiler.parser.visitor.NoOpVisitor
import edu.kit.kastel.vads.compiler.parser.visitor.RecursivePostorderVisitor

/** Checks that variables are
 * - declared before assignment
 * - not declared twice
 * - not initialized twice
 * - assigned before referenced */

object VariableStatusAnalysis : SemanticAnalysis {
    override fun analyze(program: AstNode.ProgramNode): List<SemanticError> {
        val errors = mutableListOf<SemanticError>()
        program.accept(RecursivePostorderVisitor(VariableStatusVisitor), Pair(Namespace(), errors))
        return errors
    }
}

private enum class VariableStatus {
    DECLARED,
    INITIALIZED;

    override fun toString(): String {
        return name.lowercase()
    }
}

private object VariableStatusVisitor : NoOpVisitor<Pair<Namespace<VariableStatus>, MutableList<SemanticError>>> {
    override fun visit(
        assignmentNode: AstNode.AssignmentNode,
        data: Pair<Namespace<VariableStatus>, MutableList<SemanticError>>?
    ) {
        when (val lValue = assignmentNode.lValue) {
            is AstNode.LValueIdentifierNode -> {
                val status = data?.first?.get(lValue.name)

                if (assignmentNode.operator.type == Token.OperatorType.ASSIGN) {
                    checkDeclared(lValue.name, status)?.let { data?.second?.add(it) }
                } else {
                    checkInitialized(lValue.name, status)?.let { data?.second?.add(it) }
                }

                if (status != VariableStatus.INITIALIZED) {
                    // only update when needed, reassignment is totally fine
                    updateStatus(data!!.first, VariableStatus.INITIALIZED, lValue.name)?.let { data.second.add(it) }
                }
            }
        }
        return super.visit(assignmentNode, data)
    }

    override fun visit(
        declarationNode: AstNode.DeclarationNode,
        data: Pair<Namespace<VariableStatus>, MutableList<SemanticError>>?
    ) {
        val status = if (declarationNode.initializer == null) VariableStatus.DECLARED else VariableStatus.INITIALIZED

        updateStatus(data!!.first, status, declarationNode.name)?.let { data.second.add(it) }

        return super.visit(declarationNode, data)
    }

    override fun visit(
        identifierExpressionNode: AstNode.IdentifierExpressionNode,
        data: Pair<Namespace<VariableStatus>, MutableList<SemanticError>>?
    ) {
        val status = data?.first?.get(identifierExpressionNode.name)
        checkInitialized(identifierExpressionNode.name, status)?.let { data?.second?.add(it) }
        return super.visit(identifierExpressionNode, data)
    }
}

private fun updateStatus(data: Namespace<VariableStatus>, status: VariableStatus, name: AstNode.NameNode): SemanticError? {
    if (data.get(name) == VariableStatus.INITIALIZED && status == VariableStatus.DECLARED || data.get(name) == VariableStatus.DECLARED && status == VariableStatus.DECLARED) {
        return SemanticError.VariableAlreadyExists(name)
    }

    data.put(name, status)
    return null
}

private fun checkInitialized(name: AstNode.NameNode, status: VariableStatus?): SemanticError? {
    if (status == null || status == VariableStatus.DECLARED) {
        return SemanticError.VariableNotInitialized(name)
    }
    return null
}

private fun checkDeclared(name: AstNode.NameNode, status: VariableStatus?): SemanticError? {
    if (status == null) {
        return SemanticError.VariableNotDeclaredBeforeAssignment(name)
    }
    return null
}