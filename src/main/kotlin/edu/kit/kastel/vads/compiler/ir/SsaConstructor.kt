package edu.kit.kastel.vads.compiler.ir

import edu.kit.kastel.vads.compiler.CompilerOptions
import edu.kit.kastel.vads.compiler.lexer.Token
import edu.kit.kastel.vads.compiler.parser.AstNode
import edu.kit.kastel.vads.compiler.parser.SymbolName
import edu.kit.kastel.vads.compiler.parser.visitor.RecursivePostorderVisitor
import edu.kit.kastel.vads.compiler.parser.visitor.VisitorWithoutData
import edu.kit.kastel.vads.compiler.util.Namespace

context(compilerOptions: CompilerOptions)
fun buildIr(function: AstNode.FunctionNode): IrGraph = SsaConstructor(compilerOptions).buildIr(function)

private typealias StatementReturn = Pair<IrNode.SideEffectNode, IrNode.ControlNode>
private typealias ExpressionReturn = Pair<IrNode.DataNode, IrNode.SideEffectNode>

private class SsaConstructor(val compilerOptions: CompilerOptions) {
    private val returnNodes = mutableListOf<IrNode.ReturnNode>()

    fun buildIr(function: AstNode.FunctionNode): IrGraph {
        val scopeNode = IrNode.ScopeNode()
        val (sideEffectNode, controlNode) = with(scopeNode) {
            createIrNodeForStatement(function.body, IrNode.StartNode, IrNode.StartNode)
        }

        val endNode = IrNode.EndNode(returnNodes, sideEffectNode, controlNode)

        return IrGraph(endNode, function.name.name.asString())
    }

    context(scopeNode: IrNode.ScopeNode)
    private fun createIrNodeForStatement(
        astNode: AstNode.StatementNode,
        lastSideEffectNode: IrNode.SideEffectNode,
        lastControlNode: IrNode.ControlNode
    ): StatementReturn {
        return when (astNode) {
            is AstNode.AssignmentNode -> handleAssignmentNode(astNode, lastSideEffectNode, lastControlNode)
            is AstNode.DeclarationNode -> handleDeclarationNode(astNode, lastSideEffectNode, lastControlNode)
            is AstNode.BlockNode -> handleBlockNode(astNode, lastSideEffectNode, lastControlNode)
            is AstNode.ReturnNode -> handleReturnNode(astNode, lastSideEffectNode, lastControlNode)
            is AstNode.IfNode -> handleIfNode(astNode, lastSideEffectNode, lastControlNode)
            is AstNode.BreakNode -> TODO()
            is AstNode.ContinueNode -> TODO()
            is AstNode.ForNode -> handleForNode(astNode, lastSideEffectNode, lastControlNode)
            is AstNode.WhileNode -> handleWhileNode(astNode, lastSideEffectNode, lastControlNode)
        }
    }

    context(scopeNode: IrNode.ScopeNode)
    private fun createIrNodeForExpression(astNode: AstNode.ExpressionNode, lastSideEffectNode: IrNode.SideEffectNode): ExpressionReturn {
        return when (astNode) {
            is AstNode.BinaryOperationNode -> createBinaryOperationIrNode(astNode, lastSideEffectNode)
            is AstNode.IdentifierExpressionNode -> handleIdentifierExpressionNode(astNode, lastSideEffectNode)
            is AstNode.LiteralNode -> Pair(createLiteralIrNode(astNode), lastSideEffectNode)
            is AstNode.UnaryOperationNode -> createNegateIrNode(astNode, lastSideEffectNode)
            is AstNode.TernaryOperationNode -> TODO()
        }
    }

    context(scopeNode: IrNode.ScopeNode)
    private fun createBinaryOperationIrNode(
        binaryOperationAstNode: AstNode.BinaryOperationNode,
        lastSideEffectNode: IrNode.SideEffectNode
    ): ExpressionReturn {
        val (leftIrNode, newSideEffectNode) = createIrNodeForExpression(binaryOperationAstNode.left, lastSideEffectNode)
        val (rightIrNode, newSideEffectNode2) = createIrNodeForExpression(binaryOperationAstNode.right, newSideEffectNode)

        return when (binaryOperationAstNode.operatorType) {
            Token.OperatorType.ADD -> IrNode.AddNode(leftIrNode, rightIrNode) to newSideEffectNode2
            Token.OperatorType.SUB -> IrNode.SubNode(leftIrNode, rightIrNode) to newSideEffectNode2
            Token.OperatorType.MUL -> IrNode.MulNode(leftIrNode, rightIrNode) to newSideEffectNode2
            Token.OperatorType.DIV -> {
                val divNode = IrNode.DivNode(leftIrNode, rightIrNode, newSideEffectNode2)
                divNode to IrNode.SideEffectProjectionNode(SideEffectType.DIVISION_BY_ZERO_EXCEPTION, divNode)
            }

            Token.OperatorType.MOD -> {
                val modNode = IrNode.ModNode(leftIrNode, rightIrNode, newSideEffectNode2)
                modNode to IrNode.SideEffectProjectionNode(SideEffectType.DIVISION_BY_ZERO_EXCEPTION, modNode)
            }

            Token.OperatorType.LESS_THAN -> {
                IrNode.LessThanNode(leftIrNode, rightIrNode) to newSideEffectNode2
            }

            else -> error("Unsupported operator type: ${binaryOperationAstNode.operatorType}")
        }
    }

    context(scopeNode: IrNode.ScopeNode)
    private fun handleIdentifierExpressionNode(
        identifierAstNode: AstNode.IdentifierExpressionNode,
        lastSideEffectNode: IrNode.SideEffectNode
    ): ExpressionReturn {
        val variableName = identifierAstNode.name.name
        val irNode = readVariable(variableName)
        return irNode to lastSideEffectNode
    }

    context(scopeNode: IrNode.ScopeNode)
    private fun createNegateIrNode(
        unaryOperationNode: AstNode.UnaryOperationNode,
        lastSideEffectNode: IrNode.SideEffectNode
    ): ExpressionReturn {
        require(unaryOperationNode.operator == Token.OperatorType.SUB) { TODO("Only negate operation is supported for now") }

        val (expressionIrNode, newSideEffectNode) = createIrNodeForExpression(unaryOperationNode.expression, lastSideEffectNode)
        return Pair(IrNode.NegateNode(expressionIrNode), newSideEffectNode)
    }

    context(scopeNode: IrNode.ScopeNode)
    private fun handleAssignmentNode(
        assignmentNode: AstNode.AssignmentNode,
        lastSideEffectNode: IrNode.SideEffectNode,
        lastControlNode: IrNode.ControlNode
    ): StatementReturn {
        val desugar: ((IrNode.DataNode, IrNode.DataNode, IrNode.SideEffectNode) -> Pair<IrNode.DataNode, IrNode.SideEffectNode>)? = when (assignmentNode.operator) {
            Token.OperatorType.ASSIGN -> null
            Token.OperatorType.ASSIGN_ADD -> { left, right, sideEffect -> IrNode.AddNode(left, right) to sideEffect }
            Token.OperatorType.ASSIGN_SUB -> { left, right, sideEffect -> IrNode.SubNode(left, right) to sideEffect }
            Token.OperatorType.ASSIGN_MUL -> { left, right, sideEffect -> IrNode.MulNode(left, right) to sideEffect }
            Token.OperatorType.ASSIGN_DIV -> { left, right, sideEffectNode ->
                val divNode = IrNode.DivNode(left, right, sideEffectNode)
                divNode to IrNode.SideEffectProjectionNode(SideEffectType.DIVISION_BY_ZERO_EXCEPTION, divNode)
            }

            Token.OperatorType.ASSIGN_MOD -> { left, right, sideEffectNode ->
                val modNode = IrNode.ModNode(left, right, sideEffectNode)
                modNode to IrNode.SideEffectProjectionNode(SideEffectType.DIVISION_BY_ZERO_EXCEPTION, modNode)
            }

            else -> error("Unsupported assignment operator: ${assignmentNode.operator}")
        }

        val (expressionNode, newSideEffectNode) = createIrNodeForExpression(assignmentNode.expression, lastSideEffectNode)

        when (val lValue = assignmentNode.lValue) {
            is AstNode.LValueIdentifierNode -> {
                val (newValue, newSideEffectNode2) = desugar?.invoke(readVariable(lValue.name.name), expressionNode, newSideEffectNode) ?: Pair(expressionNode, newSideEffectNode)
                writeVariable(lValue.name.name, newValue)
                return newSideEffectNode2 to lastControlNode
            }
        }
    }

    context(scopeNode: IrNode.ScopeNode)
    private fun handleDeclarationNode(
        declarationNode: AstNode.DeclarationNode,
        lastSideEffectNode: IrNode.SideEffectNode,
        lastControlNode: IrNode.ControlNode
    ): StatementReturn {
        if (declarationNode.initializer == null) {
            return lastSideEffectNode to lastControlNode
        }

        val (expressionIrNode, newSideEffectNode) = createIrNodeForExpression(declarationNode.initializer, lastSideEffectNode)
        writeVariable(declarationNode.name.name, expressionIrNode)
        return newSideEffectNode to lastControlNode
    }

    context(scopeNode: IrNode.ScopeNode)
    private fun handleBlockNode(blockNode: AstNode.BlockNode, lastSideEffectNode: IrNode.SideEffectNode, lastControlNode: IrNode.ControlNode): StatementReturn {
        val (newSideEffectNode, namespace) = createNamespace {
            val statements = getStatementsUntilFirstControlFlowEndingStatement(blockNode.statements)

            statements.fold(Pair(lastSideEffectNode, lastControlNode)) { (sideEffectNode, controlNode), statement ->
                createIrNodeForStatement(statement, sideEffectNode, controlNode)
            }
        }



        return newSideEffectNode
    }

    private fun createLiteralIrNode(literalAstNode: AstNode.LiteralNode): IrNode.ConstantNode {
        return when (literalAstNode) {
            is AstNode.BooleanLiteralNode -> IrNode.BooleanConstantNode(literalAstNode.value)
            is AstNode.IntLiteralNode -> IrNode.IntegerConstantNode(literalAstNode.parseValue()!!)
        }
    }

    context(scopeNode: IrNode.ScopeNode)
    private fun handleReturnNode(
        astNode: AstNode.ReturnNode,
        lastSideEffectNode: IrNode.SideEffectNode,
        lastControlNode: IrNode.ControlNode
    ): StatementReturn {
        val (expressionIrNode, newSideEffectNode) = createIrNodeForExpression(astNode.expression, lastSideEffectNode)
        val returnNode = IrNode.ReturnNode(expressionIrNode, newSideEffectNode, lastControlNode)
        returnNodes.add(returnNode)
        return returnNode to returnNode
    }

    context(scopeNode: IrNode.ScopeNode)
    private fun handleIfNode(astNode: AstNode.IfNode, lastSideEffectNode: IrNode.SideEffectNode, lastControlNode: IrNode.ControlNode): StatementReturn {
        val (conditionNode, newSideEffectNode) = createIrNodeForExpression(astNode.condition, lastSideEffectNode)
        val ifNode = IrNode.IfNode(conditionNode, lastControlNode)

        val trueProjectionNode = IrNode.IfProjectionNode(ifNode, IrNode.IfProjectionType.TRUE_BRANCH)
        val falseProjectionNode = IrNode.IfProjectionNode(ifNode, IrNode.IfProjectionType.FALSE_BRANCH)

        val bodyScopeNode = scopeNode.duplicate()

        val (bodySideEffectNode, bodyControlNode) = with(bodyScopeNode) {
            createIrNodeForStatement(astNode.body, newSideEffectNode, trueProjectionNode)
        }

        val (elseSideEffectNode, elseControlNode, elseScopeNode) = if (astNode.elseStatement != null) {
            val elseScopeNode = scopeNode.duplicate()
            val (elseSideEffectNode, elseControlNode) = with(elseScopeNode) {
                createIrNodeForStatement(astNode.elseStatement, newSideEffectNode, falseProjectionNode)
            }

            Triple(elseSideEffectNode, elseControlNode, elseScopeNode)
        } else {
            Triple(newSideEffectNode, falseProjectionNode, scopeNode)
        }

        val regionNode = IrNode.RegionNode(bodyControlNode, elseControlNode)
        val newSideEffectNode2 = if (bodySideEffectNode != elseSideEffectNode) {
            IrNode.SideEffectPhiNode(bodySideEffectNode, elseSideEffectNode, regionNode)
        } else {
            bodySideEffectNode
        }

        val differingDefinitions = bodyScopeNode.merge(elseScopeNode)

        for ((variableName, definitions) in differingDefinitions.entries) {
            val phiNode = IrNode.PhiNode(variableName, definitions.first, definitions.second, regionNode)
            writeVariable(variableName, phiNode)
        }

        return newSideEffectNode2 to regionNode
    }

    context(scopeNode: IrNode.ScopeNode)
    private fun handleWhileNode(astNode: AstNode.WhileNode, lastSideEffectNode: IrNode.SideEffectNode, lastControlNode: IrNode.ControlNode): StatementReturn {
        val loopRegion = IrNode.LoopRegionNode(lastControlNode, null)
        val whileScopeNode = scopeNode.duplicate()
        val incompletePhis = createInCompletePhis(astNode.body, whileScopeNode, loopRegion)

        // Create the while loop condition and body
        val result = with(whileScopeNode) {
            val (bodySideEffectNode, _, falseProjectionNode) = createLoopConditionAndBody(astNode.condition, astNode.body, lastSideEffectNode, loopRegion)

            bodySideEffectNode to falseProjectionNode
        }

        updatePhisAtLoopEnd(incompletePhis, whileScopeNode)

        return result
    }

    context(scopeNode: IrNode.ScopeNode)
    private fun handleForNode(astNode: AstNode.ForNode, lastSideEffectNode: IrNode.SideEffectNode, lastControlNode: IrNode.ControlNode): StatementReturn {
        // The for loop is desugared to a while loop with the initializer as a statement before the while loop.
        // The increment is executed at the end of the loop body.
        val (newSideEffectNode, newControlNode) = if (astNode.initializer != null) {
            createIrNodeForStatement(astNode.initializer, lastSideEffectNode, lastControlNode)
        } else {
            lastSideEffectNode to lastControlNode
        }

        val loopRegion = IrNode.LoopRegionNode(newControlNode, null)
        val forScopeNode = scopeNode.duplicate()
        val incompletePhis = createInCompletePhis(astNode, forScopeNode, loopRegion)

        // Create the condition and body
        val result = with(forScopeNode) {
            val (bodySideEffectNode, bodyControlNode, falseProjectionNode) = createLoopConditionAndBody(astNode.condition, astNode.body, newSideEffectNode, loopRegion)

            // Handle the increment statement
            if (astNode.increment != null) {
                val (incrementSideEffectNode, _) = createIrNodeForStatement(astNode.increment, bodySideEffectNode, bodyControlNode)
                incrementSideEffectNode to falseProjectionNode
            } else {
                bodySideEffectNode to falseProjectionNode
            }
        }

        updatePhisAtLoopEnd(incompletePhis, forScopeNode)

        return result
    }

    context(scopeNode: IrNode.ScopeNode)
    private fun createInCompletePhis(statement: AstNode.StatementNode, loopScopeNode: IrNode.ScopeNode, loopRegion: IrNode.LoopRegionNode): List<IrNode.PhiNode> {
        // Create incomplete phis for all variables that are written in the loop body
        val writtenVariables = findWrittenVariablesInStatement(statement)
        val writtenAndReadVariables = writtenVariables.intersect(scopeNode.getAll().keys)

        val incompletePhis = mutableListOf<IrNode.PhiNode>()
        for (variableName in writtenAndReadVariables) {
            val currentValue = readVariable(variableName)
            val incompletePhiNode = IrNode.PhiNode(variableName, currentValue, null, loopRegion)
            incompletePhis.add(incompletePhiNode)
            with(loopScopeNode) { writeVariable(variableName, incompletePhiNode) }
        }

        return incompletePhis
    }

    private fun findWrittenVariablesInStatement(statement: AstNode.StatementNode): Set<SymbolName> {
        val writtenVariables = mutableSetOf<SymbolName>()

        val visitor = object : VisitorWithoutData() {
            override fun visit(lValueIdentifierNode: AstNode.LValueIdentifierNode) {
                writtenVariables.add(lValueIdentifierNode.name.name)
            }
        }

        statement.accept(RecursivePostorderVisitor(visitor), Unit)

        return writtenVariables
    }

    context(scopeNode: IrNode.ScopeNode)
    private fun createLoopConditionAndBody(
        condition: AstNode.ExpressionNode,
        body: AstNode.StatementNode,
        lastSideEffectNode: IrNode.SideEffectNode,
        loopRegion: IrNode.LoopRegionNode
    ): Triple<IrNode.SideEffectNode, IrNode.ControlNode, IrNode.IfProjectionNode> {
        val (conditionNode, newSideEffectNode) = createIrNodeForExpression(condition, lastSideEffectNode)
        val loopEntryNode = IrNode.IfNode(conditionNode, loopRegion)
        val trueProjectionNode = IrNode.IfProjectionNode(loopEntryNode, IrNode.IfProjectionType.TRUE_BRANCH)
        val falseProjectionNode = IrNode.IfProjectionNode(loopEntryNode, IrNode.IfProjectionType.FALSE_BRANCH)

        val (bodySideEffectNode, bodyControlNode) = createIrNodeForStatement(body, newSideEffectNode, trueProjectionNode)

        loopRegion.backEdge = bodyControlNode

        return Triple(bodySideEffectNode, bodyControlNode, falseProjectionNode)
    }

    context(scopeNode: IrNode.ScopeNode)
    private fun updatePhisAtLoopEnd(incompletePhis: List<IrNode.PhiNode>, loopScopeNode: IrNode.ScopeNode) {
        // Update the incomplete phi nodes with the values of the variables at the end of the loop body
        for (incompletePhiNode in incompletePhis) {
            val value = with(loopScopeNode) { readVariable(incompletePhiNode.name) }
            require(value != incompletePhiNode)
            incompletePhiNode.second = value
            writeVariable(incompletePhiNode.name, incompletePhiNode)
        }
    }

    context(scopeNode: IrNode.ScopeNode)
    private fun writeVariable(variableName: SymbolName, value: IrNode.DataNode) {
        scopeNode[variableName] = value
    }

    context(scopeNode: IrNode.ScopeNode)
    private fun readVariable(variableName: SymbolName): IrNode.DataNode {
        return scopeNode[variableName]!!
    }

    context(scopeNode: IrNode.ScopeNode)
    private inline fun <T> createNamespace(block: () -> T): Pair<T, Namespace<IrNode.DataNode>> {
        scopeNode.pushNamespace()
        val result = block()
        val namespace = scopeNode.popNamespace()
        return result to namespace
    }

}

private fun getStatementsUntilFirstControlFlowEndingStatement(statements: List<AstNode.StatementNode>): List<AstNode.StatementNode> {
    val returnIndex = statements.indexOfFirst { it is AstNode.ControlFlowEndNode }
    return if (returnIndex == -1) {
        statements
    } else {
        statements.subList(0, returnIndex + 1)
    }
}
