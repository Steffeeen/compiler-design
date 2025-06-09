package edu.kit.kastel.vads.compiler.ir

import edu.kit.kastel.vads.compiler.CompilerOptions
import edu.kit.kastel.vads.compiler.lexer.Token
import edu.kit.kastel.vads.compiler.parser.AstNode
import edu.kit.kastel.vads.compiler.parser.SymbolName
import edu.kit.kastel.vads.compiler.parser.visitor.RecursivePostorderVisitor
import edu.kit.kastel.vads.compiler.parser.visitor.VisitorWithoutData

context(compilerOptions: CompilerOptions)
fun buildIr(function: AstNode.FunctionNode): IrGraph = SsaConstructor(compilerOptions).buildIr(function)

private typealias StatementReturn = Pair<IrNode.SideEffectNode, IrNode.ControlNode>
private typealias ExpressionReturn = Triple<IrNode.DataNode, IrNode.SideEffectNode, IrNode.ControlNode>

private data class LoopScopes(
    var afterLoopControlNode: IrNode.ControlNode? = null,
    var loopRegionNode: IrNode.LoopRegionNode? = null,
    var bodyControlNode: IrNode.ControlNode? = null,
)

private class SsaConstructor(val compilerOptions: CompilerOptions) {
    private val returnNodes = mutableListOf<IrNode.ReturnNode>()

    fun buildIr(function: AstNode.FunctionNode): IrGraph {
        val scopeNode = IrNode.ScopeNode()
        val (sideEffectNode, controlNode) = with(scopeNode) {
            with(LoopScopes()) {
                createIrNodeForStatement(function.body, IrNode.StartNode, IrNode.StartNode)
            }
        }

        val endNode = IrNode.EndNode(returnNodes, sideEffectNode, controlNode)
        return IrGraph(endNode, function.name.name.asString())
    }

    context(scopeNode: IrNode.ScopeNode, loopScopes: LoopScopes)
    private fun createIrNodeForStatement(astNode: AstNode.StatementNode, lastSideEffectNode: IrNode.SideEffectNode, lastControlNode: IrNode.ControlNode): StatementReturn {
        return when (astNode) {
            is AstNode.AssignmentNode -> handleAssignmentNode(astNode, lastSideEffectNode, lastControlNode)
            is AstNode.DeclarationNode -> handleDeclarationNode(astNode, lastSideEffectNode, lastControlNode)
            is AstNode.BlockNode -> handleBlockNode(astNode, lastSideEffectNode, lastControlNode)
            is AstNode.ReturnNode -> handleReturnNode(astNode, lastSideEffectNode, lastControlNode)
            is AstNode.IfNode -> handleIfNode(astNode, lastSideEffectNode, lastControlNode)
            is AstNode.BreakNode -> handleBreakNode(lastSideEffectNode, lastControlNode)
            is AstNode.ContinueNode -> handleContinueNode(lastSideEffectNode, lastControlNode)
            is AstNode.ForNode -> handleForNode(astNode, lastSideEffectNode, lastControlNode)
            is AstNode.WhileNode -> handleWhileNode(astNode, lastSideEffectNode, lastControlNode)
        }
    }

    context(scopeNode: IrNode.ScopeNode, loopScopes: LoopScopes)
    private fun createIrNodeForExpression(astNode: AstNode.ExpressionNode, lastSideEffectNode: IrNode.SideEffectNode, lastControlNode: IrNode.ControlNode): ExpressionReturn {
        return when (astNode) {
            is AstNode.BinaryOperationNode -> createBinaryOperationIrNode(astNode, lastSideEffectNode, lastControlNode)
            is AstNode.IdentifierExpressionNode -> handleIdentifierExpressionNode(astNode, lastSideEffectNode, lastControlNode)
            is AstNode.LiteralNode -> Triple(createLiteralIrNode(astNode), lastSideEffectNode, lastControlNode)
            is AstNode.UnaryOperationNode -> createUnaryOperationIrNode(astNode, lastSideEffectNode, lastControlNode)
            is AstNode.TernaryOperationNode -> handleTernaryOperationNode(astNode, lastSideEffectNode, lastControlNode)
        }
    }

    context(scopeNode: IrNode.ScopeNode, loopScopes: LoopScopes)
    private fun createBinaryOperationIrNode(
        binaryOperationAstNode: AstNode.BinaryOperationNode,
        lastSideEffectNode: IrNode.SideEffectNode,
        lastControlNode: IrNode.ControlNode
    ): ExpressionReturn {
        val (leftIrNode, newSideEffectNode, newControlNode) = createIrNodeForExpression(binaryOperationAstNode.left, lastSideEffectNode, lastControlNode)
        val (rightIrNode, newSideEffectNode2, newControlNode2) = createIrNodeForExpression(binaryOperationAstNode.right, newSideEffectNode, newControlNode)

        if (binaryOperationAstNode.operatorType == Token.OperatorType.DIV || binaryOperationAstNode.operatorType == Token.OperatorType.MOD) {
            // For division and modulo, we need to handle the division by zero exception
            val irNode = if (binaryOperationAstNode.operatorType == Token.OperatorType.DIV) {
                IrNode.DivNode(leftIrNode, rightIrNode, newSideEffectNode2)
            } else {
                IrNode.ModNode(leftIrNode, rightIrNode, newSideEffectNode2)
            }
            val sideEffectNode = IrNode.SideEffectProjectionNode(SideEffectType.DIVISION_BY_ZERO_EXCEPTION, irNode)
            return Triple(irNode, sideEffectNode, newControlNode2)
        }

        val irNode = when (binaryOperationAstNode.operatorType) {
            Token.OperatorType.ADD -> IrNode.AddNode(leftIrNode, rightIrNode)
            Token.OperatorType.SUB -> IrNode.SubNode(leftIrNode, rightIrNode)
            Token.OperatorType.MUL -> IrNode.MulNode(leftIrNode, rightIrNode)
            Token.OperatorType.LESS_THAN -> IrNode.LessThanNode(leftIrNode, rightIrNode)
            Token.OperatorType.LESS_EQUAL -> IrNode.LessThanOrEqualNode(leftIrNode, rightIrNode)
            Token.OperatorType.GREATER_THAN -> IrNode.GreaterThanNode(leftIrNode, rightIrNode)
            Token.OperatorType.GREATER_EQUAL -> IrNode.GreaterThanOrEqualNode(leftIrNode, rightIrNode)
            Token.OperatorType.EQUAL -> IrNode.EqualNode(leftIrNode, rightIrNode)
            Token.OperatorType.NOT_EQUAL -> IrNode.NotEqualNode(leftIrNode, rightIrNode)
            Token.OperatorType.BITWISE_AND -> IrNode.BitwiseAndNode(leftIrNode, rightIrNode)
            Token.OperatorType.BITWISE_XOR -> IrNode.BitwiseXorNode(leftIrNode, rightIrNode)
            Token.OperatorType.BITWISE_OR -> IrNode.BitwiseOrNode(leftIrNode, rightIrNode)
            Token.OperatorType.LEFT_SHIFT -> IrNode.LeftShiftNode(leftIrNode, rightIrNode)
            Token.OperatorType.RIGHT_SHIFT -> IrNode.RightShiftNode(leftIrNode, rightIrNode)
            Token.OperatorType.LOGICAL_AND -> IrNode.LogicalAndNode(leftIrNode, rightIrNode)
            Token.OperatorType.LOGICAL_OR -> IrNode.LogicalOrNode(leftIrNode, rightIrNode)
            else -> error("Unsupported operator type: ${binaryOperationAstNode.operatorType}")
        }

        return Triple(irNode, newSideEffectNode2, newControlNode2)
    }

    context(scopeNode: IrNode.ScopeNode, loopScopes: LoopScopes)
    private fun handleIdentifierExpressionNode(
        identifierAstNode: AstNode.IdentifierExpressionNode,
        lastSideEffectNode: IrNode.SideEffectNode,
        lastControlNode: IrNode.ControlNode
    ): ExpressionReturn {
        val variableName = identifierAstNode.name.name
        val irNode = readVariable(variableName)
        return Triple(irNode, lastSideEffectNode, lastControlNode)
    }

    context(scopeNode: IrNode.ScopeNode, loopScopes: LoopScopes)
    private fun createUnaryOperationIrNode(
        unaryOperationNode: AstNode.UnaryOperationNode,
        lastSideEffectNode: IrNode.SideEffectNode,
        lastControlNode: IrNode.ControlNode
    ): ExpressionReturn {
        val (expressionIrNode, newSideEffectNode, newControlNode) = createIrNodeForExpression(unaryOperationNode.expression, lastSideEffectNode, lastControlNode)

        val unaryOperationIrNode = when (unaryOperationNode.operator) {
            Token.OperatorType.SUB -> IrNode.NegateNode(expressionIrNode)
            Token.OperatorType.LOGICAL_NOT -> IrNode.LogicalNotNode(expressionIrNode)
            Token.OperatorType.BITWISE_NOT -> IrNode.BitwiseNotNode(expressionIrNode)
        }

        return Triple(unaryOperationIrNode, newSideEffectNode, newControlNode)
    }

    context(scopeNode: IrNode.ScopeNode, loopScopes: LoopScopes)
    private fun handleTernaryOperationNode(
        ternaryOperationNode: AstNode.TernaryOperationNode,
        lastSideEffectNode: IrNode.SideEffectNode,
        lastControlNode: IrNode.ControlNode
    ): ExpressionReturn {
        // Ternary operations are desugared to an if statement with two branches.
        val (conditionNode, newSideEffectNode, newControlNode) = createIrNodeForExpression(ternaryOperationNode.condition, lastSideEffectNode, lastControlNode)
        val ifNode = IrNode.IfNode(conditionNode, newControlNode)

        val trueProjectionNode = IrNode.IfProjectionNode(ifNode, IrNode.IfProjectionType.TRUE_BRANCH)
        val falseProjectionNode = IrNode.IfProjectionNode(ifNode, IrNode.IfProjectionType.FALSE_BRANCH)

        val (trueExpressionNode, trueExpressionSideEffectNode, trueExpressionControlNode) = createIrNodeForExpression(
            ternaryOperationNode.trueExpression,
            newSideEffectNode,
            trueProjectionNode
        )
        val (falseExpressionNode, falseExpressionSideEffectNode, falseExpressionControlNode) = createIrNodeForExpression(
            ternaryOperationNode.falseExpression,
            newSideEffectNode,
            falseProjectionNode
        )

        val regionNode = IrNode.RegionNode(trueExpressionControlNode, falseExpressionControlNode)
        val phiNode = IrNode.PhiNode(
            SymbolName.InternalVariable("ternary_result"),
            trueExpressionNode,
            falseExpressionNode,
            regionNode
        )

        val newSideEffectNode2 = if (trueExpressionSideEffectNode == falseExpressionSideEffectNode) {
            require(trueExpressionSideEffectNode == newSideEffectNode)
            // If both branches have the same side effect, they both don't have side effects so we just don't need to create a side effect phi node
            newSideEffectNode
        } else {
            IrNode.SideEffectPhiNode(trueExpressionSideEffectNode, falseExpressionSideEffectNode, regionNode)
        }

        return Triple(phiNode, newSideEffectNode2, regionNode)
    }

    context(scopeNode: IrNode.ScopeNode, loopScopes: LoopScopes)
    private fun handleAssignmentNode(assignmentNode: AstNode.AssignmentNode, lastSideEffectNode: IrNode.SideEffectNode, lastControlNode: IrNode.ControlNode): StatementReturn {
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

        val (expressionNode, newSideEffectNode, newControlNode) = createIrNodeForExpression(assignmentNode.expression, lastSideEffectNode, lastControlNode)

        when (val lValue = assignmentNode.lValue) {
            is AstNode.LValueIdentifierNode -> {
                val (newValue, newSideEffectNode2) = desugar?.invoke(readVariable(lValue.name.name), expressionNode, newSideEffectNode) ?: Pair(expressionNode, newSideEffectNode)
                writeVariable(lValue.name.name, newValue)
                return newSideEffectNode2 to newControlNode
            }
        }
    }

    context(scopeNode: IrNode.ScopeNode, loopScopes: LoopScopes)
    private fun handleDeclarationNode(declarationNode: AstNode.DeclarationNode, lastSideEffectNode: IrNode.SideEffectNode, lastControlNode: IrNode.ControlNode): StatementReturn {
        if (declarationNode.initializer == null) {
            return lastSideEffectNode to lastControlNode
        }

        val (expressionIrNode, newSideEffectNode, newControlNode) = createIrNodeForExpression(declarationNode.initializer, lastSideEffectNode, lastControlNode)
        writeVariable(declarationNode.name.name, expressionIrNode)
        return newSideEffectNode to newControlNode
    }

    context(scopeNode: IrNode.ScopeNode, loopScopes: LoopScopes)
    private fun handleBlockNode(blockNode: AstNode.BlockNode, lastSideEffectNode: IrNode.SideEffectNode, lastControlNode: IrNode.ControlNode): StatementReturn {
        val newSideEffectNode = createNamespace {
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

    context(scopeNode: IrNode.ScopeNode, loopScopes: LoopScopes)
    private fun handleReturnNode(astNode: AstNode.ReturnNode, lastSideEffectNode: IrNode.SideEffectNode, lastControlNode: IrNode.ControlNode): StatementReturn {
        val (expressionIrNode, newSideEffectNode, newControlNode) = createIrNodeForExpression(astNode.expression, lastSideEffectNode, lastControlNode)
        val returnNode = IrNode.ReturnNode(expressionIrNode, newSideEffectNode, newControlNode)
        returnNodes.add(returnNode)
        return returnNode to returnNode
    }

    context(scopeNode: IrNode.ScopeNode, loopScopes: LoopScopes)
    private fun handleIfNode(astNode: AstNode.IfNode, lastSideEffectNode: IrNode.SideEffectNode, lastControlNode: IrNode.ControlNode): StatementReturn {
        val (conditionNode, newSideEffectNode, newControlNode) = createIrNodeForExpression(astNode.condition, lastSideEffectNode, lastControlNode)
        val ifNode = IrNode.IfNode(conditionNode, newControlNode)

        val trueProjectionNode = IrNode.IfProjectionNode(ifNode, IrNode.IfProjectionType.TRUE_BRANCH)
        val falseProjectionNode = IrNode.IfProjectionNode(ifNode, IrNode.IfProjectionType.FALSE_BRANCH)

        // If we are in a loop, and we don't have an else statement, we need to know the control flow node when just running the body until the end.
        // This is then the false projection node of the if statement.
        // This information is used when handling a continue statement in the body of the loop.
        if (loopScopes.loopRegionNode != null && astNode.elseStatement == null) {
            loopScopes.bodyControlNode = falseProjectionNode
        }

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

        val isInLoopAndBodyContainsContinueStatement = loopScopes.loopRegionNode?.backEdge == trueProjectionNode
        if (bodyControlNode == trueProjectionNode && elseControlNode == falseProjectionNode && !isInLoopAndBodyContainsContinueStatement) {
            // Only create the region node if the control flows have not diverged.
            // The control flow can diverge if any of the branches contains a return, break, or continue statement.
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

        if (bodyControlNode == trueProjectionNode) {
            // If the body control node is the true projection node, the body did not contain a control flow ending statement.
            return bodySideEffectNode to trueProjectionNode
        }

        if (elseControlNode == falseProjectionNode) {
            // If the else control node is the false projection node, the else did not contain a control flow ending statement.
            return elseSideEffectNode to falseProjectionNode
        }

        TODO("can this happen?")
    }

    context(scopeNode: IrNode.ScopeNode, loopScopes: LoopScopes)
    private fun handleWhileNode(astNode: AstNode.WhileNode, lastSideEffectNode: IrNode.SideEffectNode, lastControlNode: IrNode.ControlNode): StatementReturn {
        val loopRegion = IrNode.LoopRegionNode(lastControlNode, null)
        val whileScopeNode = scopeNode.duplicate()
        val incompletePhis = createInCompletePhis(astNode.body, whileScopeNode, loopRegion)

        // Create the while loop condition and body
        val result = with(whileScopeNode) {
            val (bodySideEffectNode, _, afterLoopControlNode) = createLoopConditionAndBody(astNode.condition, astNode.body, lastSideEffectNode, loopRegion)
            bodySideEffectNode to afterLoopControlNode
        }

        updatePhisAtLoopEnd(incompletePhis, whileScopeNode)

        return result
    }

    context(scopeNode: IrNode.ScopeNode, loopScopes: LoopScopes)
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
            val (bodySideEffectNode, bodyControlNode, afterLoopControlNode) = createLoopConditionAndBody(astNode.condition, astNode.body, newSideEffectNode, loopRegion)

            // Handle the increment statement
            if (astNode.increment != null) {
                val (incrementSideEffectNode, _) = createIrNodeForStatement(astNode.increment, bodySideEffectNode, bodyControlNode)
                incrementSideEffectNode to afterLoopControlNode
            } else {
                bodySideEffectNode to afterLoopControlNode
            }
        }

        updatePhisAtLoopEnd(incompletePhis, forScopeNode)

        return result
    }

    context(scopeNode: IrNode.ScopeNode, loopScopes: LoopScopes)
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

    context(scopeNode: IrNode.ScopeNode, loopScopes: LoopScopes)
    private fun createLoopConditionAndBody(
        condition: AstNode.ExpressionNode,
        body: AstNode.StatementNode,
        lastSideEffectNode: IrNode.SideEffectNode,
        loopRegion: IrNode.LoopRegionNode
    ): Triple<IrNode.SideEffectNode, IrNode.ControlNode, IrNode.ControlNode> {
        val (conditionNode, newSideEffectNode, newControlNode) = createIrNodeForExpression(condition, lastSideEffectNode, loopRegion)
        val loopEntryNode = IrNode.IfNode(conditionNode, newControlNode)
        val trueProjectionNode = IrNode.IfProjectionNode(loopEntryNode, IrNode.IfProjectionType.TRUE_BRANCH)
        val falseProjectionNode = IrNode.IfProjectionNode(loopEntryNode, IrNode.IfProjectionType.FALSE_BRANCH)

        val loopScopes = LoopScopes(falseProjectionNode, loopRegion, trueProjectionNode)
        val (bodySideEffectNode, bodyControlNode) = with(loopScopes) {
            createIrNodeForStatement(body, newSideEffectNode, trueProjectionNode)
        }

        loopRegion.backEdge = if (loopRegion.backEdge == trueProjectionNode) {
            // There are no continue statements in the loop body, set the normal back edge
            bodyControlNode
        } else {
            // There are continue statements in the loop body, create a region node to merge the continue statements' control flow and the normal back edge
            IrNode.RegionNode(loopRegion.backEdge!!, bodyControlNode)
        }

        return Triple(bodySideEffectNode, bodyControlNode, loopScopes.afterLoopControlNode!!)
    }

    context(scopeNode: IrNode.ScopeNode, loopScopes: LoopScopes)
    private fun updatePhisAtLoopEnd(incompletePhis: List<IrNode.PhiNode>, loopScopeNode: IrNode.ScopeNode) {
        // Update the incomplete phi nodes with the values of the variables at the end of the loop body
        for (incompletePhiNode in incompletePhis) {
            val value = with(loopScopeNode) { readVariable(incompletePhiNode.name) }
            require(value != incompletePhiNode)
            incompletePhiNode.second = value
            writeVariable(incompletePhiNode.name, incompletePhiNode)
        }
    }

    context(scopeNode: IrNode.ScopeNode, loopScopes: LoopScopes)
    private fun handleBreakNode(lastSideEffectNode: IrNode.SideEffectNode, lastControlNode: IrNode.ControlNode): StatementReturn {
        val regionNode = IrNode.RegionNode(loopScopes.afterLoopControlNode!!, lastControlNode)
        loopScopes.afterLoopControlNode = regionNode
        return lastSideEffectNode to regionNode
    }

    context(scopeNode: IrNode.ScopeNode, loopScopes: LoopScopes)
    private fun handleContinueNode(lastSideEffectNode: IrNode.SideEffectNode, lastControlNode: IrNode.ControlNode): StatementReturn {
        if (loopScopes.loopRegionNode!!.backEdge != null) {
            // If there is already a back edge, create a region node to merge the control flow with the previous continue statements
            val newControlNode = IrNode.RegionNode(loopScopes.loopRegionNode!!.backEdge!!, lastControlNode)
            loopScopes.loopRegionNode!!.backEdge = newControlNode
            return lastSideEffectNode to newControlNode
        }

        loopScopes.loopRegionNode!!.backEdge = lastControlNode
        return lastSideEffectNode to loopScopes.bodyControlNode!!
    }

    context(scopeNode: IrNode.ScopeNode, loopScopes: LoopScopes)
    private fun writeVariable(variableName: SymbolName, value: IrNode.DataNode) {
        scopeNode[variableName] = value
    }

    context(scopeNode: IrNode.ScopeNode, loopScopes: LoopScopes)
    private fun readVariable(variableName: SymbolName): IrNode.DataNode {
        return scopeNode[variableName]!!
    }

    context(scopeNode: IrNode.ScopeNode, loopScopes: LoopScopes)
    private inline fun <T> createNamespace(block: () -> T): T {
        scopeNode.pushNamespace()
        val result = block()
        scopeNode.popNamespace()
        return result
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