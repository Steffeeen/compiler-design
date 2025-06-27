package edu.kit.kastel.vads.compiler.semantic

import edu.kit.kastel.vads.compiler.lexer.Token
import edu.kit.kastel.vads.compiler.parser.AstNode
import edu.kit.kastel.vads.compiler.parser.visitor.VisitorWithoutData
import edu.kit.kastel.vads.compiler.util.Namespace
import edu.kit.kastel.vads.compiler.util.NamespaceStack

/** Checks that variables are
 * - declared before assignment
 * - not declared twice
 * - not initialized twice
 * - assigned before referenced */

object VariableStatusAnalysis : SemanticAnalysis {
    override fun analyze(program: AstNode.ProgramNode): List<SemanticError> {
        val visitor = VariableStatusVisitor()
        program.accept(visitor, Unit)
        return visitor.errors
    }
}

private enum class VariableStatus {
    DECLARED,
    INITIALIZED;

    override fun toString(): String {
        return name.lowercase()
    }
}

private class VariableStatusVisitor : VisitorWithoutData() {
    val errors = mutableListOf<SemanticError>()
    private val statusStack = NamespaceStack<VariableStatus>()
    private val blockToNamespace = mutableMapOf<AstNode.BlockNode, Namespace<VariableStatus>>()

    override fun visit(programNode: AstNode.ProgramNode) = programNode.topLevelFunctions.forEach { it.accept(this, Unit) }
    override fun visit(unaryOperationNode: AstNode.UnaryOperationNode) = unaryOperationNode.expression.accept(this, Unit)
    override fun visit(returnNode: AstNode.ReturnNode) = returnNode.expression.accept(this, Unit)
    override fun visit(binaryOperationNode: AstNode.BinaryOperationNode) {
        binaryOperationNode.left.accept(this, Unit)
        binaryOperationNode.right.accept(this, Unit)
    }

    override fun visit(ternaryOperationNode: AstNode.TernaryOperationNode) {
        ternaryOperationNode.condition.accept(this, Unit)
        ternaryOperationNode.trueExpression.accept(this, Unit)
        ternaryOperationNode.falseExpression.accept(this, Unit)
    }

    override fun visit(whileNode: AstNode.WhileNode) {
        whileNode.condition.accept(this, Unit)
        whileNode.body.accept(this, Unit)
    }

    override fun visit(forNode: AstNode.ForNode) {
        if (forNode.initializer !is AstNode.DeclarationNode) {
            forNode.initializer?.accept(this, Unit)
        }
        createNamespace {
            forNode.initializer?.accept(this, Unit)
            forNode.condition.accept(this, Unit)

            // Analyze the body before the increment to ensure that if a variable is initialized in the increment, it is not considered initialized in the body
            createNamespace { forNode.body.accept(this, Unit) }
            val variablesDeclaredBeforeBody = statusStack.getAll().keys
            val bodyNamespace = getNamespaceForBlockOrSingleStatement(forNode.body)
            bodyNamespace.getAll().filter { it.key in variablesDeclaredBeforeBody }.forEach { statusStack.setInTopMost(it.key, it.value) }

            forNode.increment?.accept(this, Unit)
        }
    }

    override fun visit(functionNode: AstNode.FunctionNode) {
        createNamespace {
            functionNode.parameters.forEach { it.accept(this, Unit) }
            functionNode.body.accept(this, Unit)
        }
    }

    override fun visit(parameterNode: AstNode.ParameterNode) {
        checkUndeclared(parameterNode.name)
        updateStatus(parameterNode.name, VariableStatus.INITIALIZED)
    }

    override fun visit(blockNode: AstNode.BlockNode) {
        blockToNamespace[blockNode] = createNamespace {
            for (statement in blockNode.statements) {
                statement.accept(this, Unit)

                if (statement is AstNode.ControlFlowEndNode) {
                    // After a control flow node we assume that all variables that were declared before the control flow node
                    // are initialized regardless of whether they were actually initialized in the block or not
                    statusStack.getAll().forEach { (name, _) -> statusStack.setInTopMost(name, VariableStatus.INITIALIZED) }
                }

                if (statement is AstNode.BlockNode) {
                    // transfer the status of variables that were initialized in the inner block to the outer block
                    val blockNamespace = blockToNamespace[statement]!!
                    val variablesToTransferToOuterBlock = blockNamespace.getAll().filter { (name, status) ->
                        status == VariableStatus.INITIALIZED && statusStack[name] == VariableStatus.DECLARED
                    }
                    variablesToTransferToOuterBlock.forEach { (name, _) -> statusStack.setInTopMost(name, VariableStatus.INITIALIZED) }
                }
            }
        }
    }

    override fun visit(declarationNode: AstNode.DeclarationNode) {
        declarationNode.initializer?.accept(this, Unit)

        checkUndeclared(declarationNode.name)
        val status = if (declarationNode.initializer == null) VariableStatus.DECLARED else VariableStatus.INITIALIZED
        updateStatus(declarationNode.name, status)
    }

    override fun visit(assignmentNode: AstNode.AssignmentNode) {
        require(assignmentNode.lValue is AstNode.LValueIdentifierNode) { TODO("Currently only identifier lValues are supported") }

        assignmentNode.expression.accept(this, Unit)

        if (assignmentNode.operator == Token.OperatorType.ASSIGN) {
            checkDeclared(assignmentNode.lValue.name)
        } else {
            checkInitialized(assignmentNode.lValue.name)
        }

        updateStatus(assignmentNode.lValue.name, VariableStatus.INITIALIZED)
    }

    override fun visit(identifierExpressionNode: AstNode.IdentifierExpressionNode) {
        checkInitialized(identifierExpressionNode.name)
    }

    override fun visit(ifNode: AstNode.IfNode) {
        ifNode.condition.accept(this, Unit)

        // Analyze the body and else statement in separate namespaces to avoid the case where it is only a variable declaration that would leak into the outer namespace
        createNamespace { ifNode.body.accept(this, Unit) }
        createNamespace { ifNode.elseStatement?.accept(this, Unit) }

        if (ifNode.elseStatement != null) {
            // Ensure that variables initialized in both branches are marked as initialized in the outer namespace
            val bodyNamespace = getNamespaceForBlockOrSingleStatement(ifNode.body)
            val elseNamespace = getNamespaceForBlockOrSingleStatement(ifNode.elseStatement)
            val variablesDeclaredBeforeIfStatement = statusStack.getAll().filterValues { it == VariableStatus.DECLARED }.keys
            val initializedInBody = bodyNamespace.getAll().filter { (name, status) -> status == VariableStatus.INITIALIZED && name in variablesDeclaredBeforeIfStatement }.keys
            val initializedInBoth = elseNamespace.getAll().filter { (name, status) -> status == VariableStatus.INITIALIZED && name in initializedInBody }.keys
            initializedInBoth.forEach { statusStack.setInTopMost(it, VariableStatus.INITIALIZED) }
        }
    }

    private fun getNamespaceForBlockOrSingleStatement(statement: AstNode.StatementNode): Namespace<VariableStatus> = when (statement) {
        is AstNode.BlockNode -> blockToNamespace[statement]!!
        is AstNode.ControlFlowEndNode -> {
            // All variables after a control flow node are considered to be initialized
            createNamespace {
                statusStack.getAll().forEach { (name, _) -> statusStack.setInTopMost(name, VariableStatus.INITIALIZED) }
            }
        }

        else -> {
            // For single statements, we create a new namespace and just rerun the analysis, this is technically not optimal but doesn't really matter for single statements
            createNamespace {
                statement.accept(this, Unit)
            }
        }
    }

    private fun updateStatus(name: AstNode.NameNode, status: VariableStatus) {
        val currentStatus = statusStack[name]
        if (currentStatus == null || currentStatus == VariableStatus.DECLARED && status == VariableStatus.INITIALIZED) {
            statusStack.setInTopMost(name, status)
            return
        }

        if (currentStatus == VariableStatus.INITIALIZED && status == VariableStatus.INITIALIZED) {
            return
        }

        errors += SemanticError.VariableAlreadyExists(name)
    }

    private fun checkInitialized(name: AstNode.NameNode) {
        val status = statusStack[name]
        if (status == null || status == VariableStatus.DECLARED) {
            errors += SemanticError.VariableNotInitialized(name)
        }
    }

    private fun checkDeclared(name: AstNode.NameNode) {
        val status = statusStack[name]
        if (status == null) {
            errors += SemanticError.VariableNotDeclaredBeforeAssignment(name)
        }
    }

    private fun checkUndeclared(name: AstNode.NameNode) {
        val status = statusStack[name]
        if (status != null) {
            errors += SemanticError.VariableAlreadyExists(name)
        }
    }

    private inline fun createNamespace(block: () -> Unit): Namespace<VariableStatus> {
        statusStack.pushNamespace()
        block()
        return statusStack.popNamespace()
    }
}
