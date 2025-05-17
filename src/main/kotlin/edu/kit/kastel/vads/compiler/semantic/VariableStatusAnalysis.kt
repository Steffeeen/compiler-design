package edu.kit.kastel.vads.compiler.semantic

import edu.kit.kastel.vads.compiler.parser.AstNode
import edu.kit.kastel.vads.compiler.parser.visitor.NoOpVisitor

/** Checks that variables are
 * - declared before assignment
 * - not declared twice
 * - not initialized twice
 * - assigned before referenced */

object VariableStatusAnalysis : SemanticAnalysis {
    override fun analyze(program: AstNode.ProgramNode): List<SemanticError> {
        val errors = mutableListOf<SemanticError>()
        program.accept(VariableStatusVisitor, Pair(Namespace(), errors))
        return errors
    }
}

data class VariableAlreadyInitialized(val node: AstNode.DeclarationNode) : SemanticError {}
data class VariableNonDeclaredBeforeAssignment(val node: AstNode.NameNode) : SemanticError {}

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
                checkInitialized(lValue.name, status)?.let { data?.second?.add(it) }
            }
        }
        return super.visit(assignmentNode, data)
    }

    override fun visit(
        declarationNode: AstNode.DeclarationNode,
        data: Pair<Namespace<VariableStatus>, MutableList<SemanticError>>?
    ) {
        val status = if (declarationNode.initializer == null) VariableStatus.DECLARED else VariableStatus.INITIALIZED

        data?.first?.put(declarationNode.name, status) { existing, replacement ->
            if (existing == VariableStatus.INITIALIZED && replacement == VariableStatus.DECLARED) {
                data.second += VariableAlreadyInitialized(declarationNode)
            }

            replacement
        }

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

private fun checkInitialized(name: AstNode.NameNode, status: VariableStatus?): SemanticError? {
    if (status == null) {
        return VariableNonDeclaredBeforeAssignment(name)
    }
    return null
}