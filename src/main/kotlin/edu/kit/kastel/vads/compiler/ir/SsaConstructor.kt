package edu.kit.kastel.vads.compiler.ir

import edu.kit.kastel.vads.compiler.CompilerOptions
import edu.kit.kastel.vads.compiler.lexer.Token
import edu.kit.kastel.vads.compiler.parser.AstNode
import edu.kit.kastel.vads.compiler.parser.SymbolName
import edu.kit.kastel.vads.compiler.parser.visitor.RecursivePostorderVisitor
import edu.kit.kastel.vads.compiler.parser.visitor.VisitorWithoutData

context(compilerOptions: CompilerOptions)
fun buildIr(program: AstNode.ProgramNode): IrProgram {
    val graphs = program.topLevelFunctions.map { buildIr(it) }
    return IrProgram(graphs)
}

context(compilerOptions: CompilerOptions)
private fun buildIr(function: AstNode.FunctionNode): IrGraph = SsaConstructor(compilerOptions).buildIr(function)

private typealias StatementReturn = Pair<IrNode.SideEffectNode?, IrNode.ControlNode?>
private typealias ExpressionReturn = Triple<IrNode.DataNode, IrNode.SideEffectNode, IrNode.ControlNode>

private val CONTROL_FLOW_END = Pair(null, null)

private class LoopInformation(val loopRegion: IrNode.LoopRegionNode) {
    private var _backEdges = mutableListOf<Triple<IrNode.ControlNode, IrNode.SideEffectNode, IrNode.ScopeNode>>()
    private var _afterLoopEdges = mutableListOf<Triple<IrNode.ControlNode, IrNode.SideEffectNode, IrNode.ScopeNode>>()

    val backEdges get() = _backEdges
    val afterLoopEdges get() = _afterLoopEdges

    fun addBackEdge(controlNode: IrNode.ControlNode, sideEffectNode: IrNode.SideEffectNode, scopeNode: IrNode.ScopeNode): Boolean {
        return backEdges.add(Triple(controlNode, sideEffectNode, scopeNode))
    }

    fun addAfterLoopEdge(controlNode: IrNode.ControlNode, sideEffectNode: IrNode.SideEffectNode, scopeNode: IrNode.ScopeNode): Boolean {
        return afterLoopEdges.add(Triple(controlNode, sideEffectNode, scopeNode))
    }
}

private class SsaConstructor(val compilerOptions: CompilerOptions) {
    private val returnNodes = mutableListOf<IrNode.ReturnNode>()
    private val loopStack = mutableListOf<LoopInformation>()
    private val currentLoopInformation get() = loopStack.last()

    fun buildIr(function: AstNode.FunctionNode): IrGraph {
        val scopeNode = IrNode.ScopeNode()
        val (sideEffectNode, controlNode) = with(scopeNode) {
            createIrNodeForStatement(function.body, IrNode.StartNode, IrNode.StartNode)
        }

        val endNode = IrNode.EndNode(returnNodes)
        return IrGraph(endNode, function.name.name.asString())
    }

    context(scopeNode: IrNode.ScopeNode)
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

    context(scopeNode: IrNode.ScopeNode)
    private fun createIrNodeForExpression(astNode: AstNode.ExpressionNode, lastSideEffectNode: IrNode.SideEffectNode, lastControlNode: IrNode.ControlNode): ExpressionReturn {
        return when (astNode) {
            is AstNode.BinaryOperationNode -> createBinaryOperationIrNode(astNode, lastSideEffectNode, lastControlNode)
            is AstNode.IdentifierExpressionNode -> handleIdentifierExpressionNode(astNode, lastSideEffectNode, lastControlNode)
            is AstNode.LiteralNode -> Triple(createLiteralIrNode(astNode), lastSideEffectNode, lastControlNode)
            is AstNode.UnaryOperationNode -> createUnaryOperationIrNode(astNode, lastSideEffectNode, lastControlNode)
            is AstNode.TernaryOperationNode -> handleTernaryOperationNode(astNode, lastSideEffectNode, lastControlNode)
        }
    }

    context(scopeNode: IrNode.ScopeNode)
    private fun createBinaryOperationIrNode(
        binaryOperationAstNode: AstNode.BinaryOperationNode,
        lastSideEffectNode: IrNode.SideEffectNode,
        lastControlNode: IrNode.ControlNode
    ): ExpressionReturn {
        if (binaryOperationAstNode.operatorType == Token.OperatorType.LOGICAL_AND || binaryOperationAstNode.operatorType == Token.OperatorType.LOGICAL_OR) {
            return createLogicalBinaryOperationIrNode(binaryOperationAstNode, lastSideEffectNode, lastControlNode)
        }

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
            else -> error("Unsupported operator type: ${binaryOperationAstNode.operatorType}")
        }

        return Triple(irNode, newSideEffectNode2, newControlNode2)
    }

    context(scopeNode: IrNode.ScopeNode)
    private fun createLogicalBinaryOperationIrNode(
        astNode: AstNode.BinaryOperationNode,
        lastSideEffectNode: IrNode.SideEffectNode,
        lastControlNode: IrNode.ControlNode
    ): ExpressionReturn {
        require(astNode.operatorType == Token.OperatorType.LOGICAL_AND || astNode.operatorType == Token.OperatorType.LOGICAL_OR)

        val (leftIrNode, newSideEffectNode, newControlNode) = createIrNodeForExpression(astNode.left, lastSideEffectNode, lastControlNode)

        val ifNode = IrNode.IfNode(leftIrNode, newControlNode)

        val trueProjectionNode = IrNode.IfProjectionNode(ifNode, IrNode.IfProjectionType.TRUE_BRANCH)
        val falseProjectionNode = IrNode.IfProjectionNode(ifNode, IrNode.IfProjectionType.FALSE_BRANCH)

        val (branchToCheck, shortCircuitBranch) = when (astNode.operatorType) {
            Token.OperatorType.LOGICAL_AND -> trueProjectionNode to falseProjectionNode
            Token.OperatorType.LOGICAL_OR -> falseProjectionNode to trueProjectionNode
            else -> error("createLogicalBinaryOperationIrNode called with unsupported operator type: ${astNode.operatorType}")
        }

        val (rightIrNode, newSideEffectNode2, newControlNode2) = createIrNodeForExpression(astNode.right, newSideEffectNode, branchToCheck)
        val regionNode = IrNode.RegionNode(shortCircuitBranch, newControlNode2)

        val name = SymbolName.InternalVariable("shortcircuit_result")
        val phiNode = IrNode.PhiNode(name, leftIrNode, lastControlNode, rightIrNode, branchToCheck, regionNode)
        val sideEffectNode = if (newSideEffectNode == newSideEffectNode2) {
            newSideEffectNode
        } else {
            IrNode.SideEffectPhiNode(newSideEffectNode, newSideEffectNode2, regionNode)
        }

        return Triple(phiNode, sideEffectNode, regionNode)
    }

    context(scopeNode: IrNode.ScopeNode)
    private fun handleIdentifierExpressionNode(
        identifierAstNode: AstNode.IdentifierExpressionNode,
        lastSideEffectNode: IrNode.SideEffectNode,
        lastControlNode: IrNode.ControlNode
    ): ExpressionReturn {
        val variableName = identifierAstNode.name.name
        val irNode = readVariable(variableName)
        return Triple(irNode, lastSideEffectNode, lastControlNode)
    }

    context(scopeNode: IrNode.ScopeNode)
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

    context(scopeNode: IrNode.ScopeNode)
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
            trueProjectionNode,
            falseExpressionNode,
            falseProjectionNode,
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

    context(scopeNode: IrNode.ScopeNode)
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

    context(scopeNode: IrNode.ScopeNode)
    private fun handleDeclarationNode(declarationNode: AstNode.DeclarationNode, lastSideEffectNode: IrNode.SideEffectNode, lastControlNode: IrNode.ControlNode): StatementReturn {
        if (declarationNode.initializer == null) {
            return lastSideEffectNode to lastControlNode
        }

        val (expressionIrNode, newSideEffectNode, newControlNode) = createIrNodeForExpression(declarationNode.initializer, lastSideEffectNode, lastControlNode)
        writeVariable(declarationNode.name.name, expressionIrNode)
        return newSideEffectNode to newControlNode
    }

    context(scopeNode: IrNode.ScopeNode)
    private fun handleBlockNode(blockNode: AstNode.BlockNode, lastSideEffectNode: IrNode.SideEffectNode, lastControlNode: IrNode.ControlNode): StatementReturn {
        return createNamespace {
            val statements = getStatementsUntilFirstControlFlowEndingStatement(blockNode.statements)

            val initial = Pair(lastSideEffectNode, lastControlNode)
            statements.fold<AstNode.StatementNode, Pair<IrNode.SideEffectNode?, IrNode.ControlNode?>>(initial) { (sideEffectNode, controlNode), statement ->
                createIrNodeForStatement(statement, sideEffectNode!!, controlNode!!)
            }
        }
    }

    private fun createLiteralIrNode(literalAstNode: AstNode.LiteralNode): IrNode.ConstantNode {
        return when (literalAstNode) {
            is AstNode.BooleanLiteralNode -> IrNode.BooleanConstantNode(literalAstNode.value)
            is AstNode.IntLiteralNode -> IrNode.IntegerConstantNode(literalAstNode.parseValue()!!)
        }
    }

    context(scopeNode: IrNode.ScopeNode)
    private fun handleReturnNode(astNode: AstNode.ReturnNode, lastSideEffectNode: IrNode.SideEffectNode, lastControlNode: IrNode.ControlNode): StatementReturn {
        val (expressionIrNode, newSideEffectNode, newControlNode) = createIrNodeForExpression(astNode.expression, lastSideEffectNode, lastControlNode)
        val returnNode = IrNode.ReturnNode(expressionIrNode, newSideEffectNode, newControlNode)
        returnNodes.add(returnNode)
        return CONTROL_FLOW_END
    }

    context(scopeNode: IrNode.ScopeNode)
    private fun handleIfNode(astNode: AstNode.IfNode, lastSideEffectNode: IrNode.SideEffectNode, lastControlNode: IrNode.ControlNode): StatementReturn {
        val (conditionNode, newSideEffectNode, newControlNode) = createIrNodeForExpression(astNode.condition, lastSideEffectNode, lastControlNode)
        val ifNode = IrNode.IfNode(conditionNode, newControlNode)

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

        val bodyTransfersControlFlowElsewhere = bodySideEffectNode to bodyControlNode == CONTROL_FLOW_END
        val elseTransfersControlFlowElsewhere = elseSideEffectNode to elseControlNode == CONTROL_FLOW_END

        return when {
            bodyTransfersControlFlowElsewhere && elseTransfersControlFlowElsewhere -> CONTROL_FLOW_END
            bodyTransfersControlFlowElsewhere -> {
                val differingDefinitions = scopeNode.merge(elseScopeNode)
                for ((name, values) in differingDefinitions) {
                    // Propagate the else branch's changes
                    writeVariable(name, values.second)
                }

                elseSideEffectNode to elseControlNode
            }

            elseTransfersControlFlowElsewhere -> {
                val differingDefinitions = scopeNode.merge(bodyScopeNode)
                for ((name, values) in differingDefinitions) {
                    // Propagate the body branch's changes
                    writeVariable(name, values.second)
                }
                bodySideEffectNode to bodyControlNode
            }

            else -> {
                // No branch transfers control flow elsewhere, so we need to create a region node and merge the scope nodes
                val regionNode = IrNode.RegionNode(bodyControlNode!!, elseControlNode!!)
                val sideEffectNode = if (bodySideEffectNode == elseSideEffectNode) {
                    bodySideEffectNode
                } else {
                    IrNode.SideEffectPhiNode(bodySideEffectNode!!, elseSideEffectNode!!, regionNode)
                }

                val differingDefinitions = bodyScopeNode.merge(elseScopeNode)
                for ((name, values) in differingDefinitions) {
                    val phiNode = IrNode.PhiNode(name, values.first, bodyControlNode, values.second, elseControlNode, regionNode)
                    writeVariable(name, phiNode)
                }

                sideEffectNode to regionNode
            }
        }
    }

    context(scopeNode: IrNode.ScopeNode)
    private fun handleWhileNode(astNode: AstNode.WhileNode, lastSideEffectNode: IrNode.SideEffectNode, lastControlNode: IrNode.ControlNode): StatementReturn {
        val loopRegion = IrNode.LoopRegionNode(lastControlNode, null)
        val whileScopeNode = scopeNode.duplicate()
        val incompletePhis = createInCompletePhis(astNode.body, whileScopeNode, loopRegion)

        val (_, loopInformation) = createLoopInformation(loopRegion) {
            with(whileScopeNode) {
                createLoopConditionAndBody(astNode.condition, astNode.body, lastSideEffectNode)
            }
        }

        updatePhisAtLoopEnd(incompletePhis, loopInformation)

        // We need to merge the scope nodes of all branches that lead to the end of the loop.
        // This can, however, not be done one after the other, as for example, the first and third scope could only have changes for a variable
        val (variableInfo, sideEffectNode, controlNode) = mergeDefinitions(loopInformation.afterLoopEdges)
        for ((variableName, nodes) in variableInfo) {
            val (dataNode, _) = nodes
            writeVariable(variableName, dataNode)
        }

        return sideEffectNode to controlNode
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

        val loopRegion = IrNode.LoopRegionNode(newControlNode!!, null)
        val forScopeNode = scopeNode.duplicate()
        val incompletePhis = createInCompletePhis(astNode, forScopeNode, loopRegion)

        val (result, loopInformation) = createLoopInformation(loopRegion) {
            with(forScopeNode) {
                createLoopConditionAndBody(astNode.condition, astNode.body, newSideEffectNode!!)
            }
        }

        val (bodySideEffectNode, bodyControlNode) = result

        // Handle the increment statement
        if (astNode.increment != null && bodySideEffectNode != null && bodyControlNode != null) {
            val loopVariableName = ((astNode.increment as AstNode.AssignmentNode).lValue as AstNode.LValueIdentifierNode).name.name
            val loopVariableIncompletePhi = incompletePhis.find { it.name == loopVariableName }!!
            val loopVariableIsUpdatedInBody = forScopeNode[loopVariableName] != loopVariableIncompletePhi

            if (loopVariableIsUpdatedInBody) {
                // If the loop variable is updated in the body, we remove the original phi node for the loop variable from the incomplete phis
                // as we will manually set the second input to the result of the increment operation.
                // We insert a new placeholder phi node that will only get its second input set,
                // we will then remove this phi node again and use the input to that phi node as the input for the increment operation.
                val placeholderPhiNode = IrNode.PhiNode(SymbolName.InternalVariable("placeholder"), IrNode.IntegerConstantNode(0u), bodyControlNode, null, null, loopRegion)

                val patchedIncompletePhis = (incompletePhis - loopVariableIncompletePhi) + placeholderPhiNode
                updatePhisAtLoopEnd(patchedIncompletePhis, loopInformation)

                val (loopVariableDataNodeAfterIncrement, sideEffectNode) = with(forScopeNode.duplicate()) {
                    writeVariable(loopVariableName, placeholderPhiNode.second!!)
                    // Discard the control node as the increment operation does not influence control flow
                    val (incrementSideEffectNode, _) = createIrNodeForStatement(astNode.increment, bodySideEffectNode, bodyControlNode)
                    readVariable(loopVariableName) to incrementSideEffectNode
                }
                loopVariableIncompletePhi.second = loopVariableDataNodeAfterIncrement
                loopVariableIncompletePhi.secondControl = loopRegion
                // TODO: properly propagate side effects
            } else {
                updatePhisAtLoopEnd(incompletePhis - loopVariableIncompletePhi, loopInformation)
                val (loopVariableDataNodeAfterIncrement, sideEffectNode) = with(forScopeNode.duplicate()) {
                    // Discard the control node as the increment operation does not influence control flow
                    val (incrementSideEffectNode, _) = createIrNodeForStatement(astNode.increment, bodySideEffectNode, bodyControlNode)
                    readVariable(loopVariableName) to incrementSideEffectNode
                }
                loopVariableIncompletePhi.second = loopVariableDataNodeAfterIncrement
                loopVariableIncompletePhi.secondControl = loopRegion
                // TODO: properly propagate side effects
            }
        } else {
            updatePhisAtLoopEnd(incompletePhis, loopInformation)
        }

        // We need to merge the scope nodes of all branches that lead to the end of the loop.
        // This can, however, not be done one after the other, as for example, the first and third scope could only have changes for a variable
        val (variableInfo, sideEffectNode, controlNode) = mergeDefinitions(loopInformation.afterLoopEdges)
        for ((variableName, nodes) in variableInfo) {
            val (dataNode, _) = nodes
            writeVariable(variableName, dataNode)
        }

        return sideEffectNode to controlNode
    }

    context(scopeNode: IrNode.ScopeNode)
    private fun mergeDefinitions(edges: List<Triple<IrNode.ControlNode, IrNode.SideEffectNode, IrNode.ScopeNode>>): Triple<Map<SymbolName, Pair<IrNode.DataNode, IrNode.ControlNode>>, IrNode.SideEffectNode, IrNode.ControlNode> {
        require(edges.isNotEmpty())

        if (edges.size == 1) {
            // If there is only one edge, we can just use the values from that edge
            val (controlNode, sideEffectNode, firstScopeNode) = edges.first()

            // Get all variables that changed in the first scope node
            return Triple(scopeNode.merge(firstScopeNode).mapValues { it.value.second to controlNode }, sideEffectNode, controlNode)
        }

        val (firstControlNode, firstSideEffectNode, firstScopeNode) = edges.first()

        val alreadyMergedValues = mutableMapOf<SymbolName, MutableSet<IrNode.DataNode>>()
        val alreadyMergedSideEffects = mutableSetOf(firstSideEffectNode)
        var currentRegionNode = firstControlNode
        var currentSideEffectNode = firstSideEffectNode

        val result = mutableMapOf<SymbolName, Pair<IrNode.DataNode, IrNode.ControlNode>>()

        val differingDefinitions = scopeNode.merge(firstScopeNode)
        for ((variableName, values) in differingDefinitions) {
            val (_, firstEdgeValue) = values
            result[variableName] = firstEdgeValue to firstControlNode
            alreadyMergedValues.getOrPut(variableName) { mutableSetOf() }.add(firstEdgeValue)
        }

        for ((edgeControlNode, sideEffectNode, edgeScopeNode) in edges.drop(1)) {
            currentRegionNode = IrNode.RegionNode(currentRegionNode, edgeControlNode)

            if (sideEffectNode !in alreadyMergedSideEffects) {
                currentSideEffectNode = IrNode.SideEffectPhiNode(currentSideEffectNode, sideEffectNode, currentRegionNode)
                alreadyMergedSideEffects.add(sideEffectNode)
            }

            for ((variableName, value) in edgeScopeNode.getAll()) {
                val valueWasAlreadyMerged = variableName in alreadyMergedValues && value in (alreadyMergedValues.getOrDefault(variableName, mutableSetOf()))
                if (valueWasAlreadyMerged) {
                    continue
                }

                val (currentValue, currentControlNode) = result[variableName]!!
                val phiNode = IrNode.PhiNode(variableName, currentValue, currentControlNode, value, edgeControlNode, currentRegionNode)
                result[variableName] = phiNode to edgeControlNode
            }
        }

        return Triple(result, currentSideEffectNode, currentRegionNode)
    }

    context(scopeNode: IrNode.ScopeNode)
    private fun createInCompletePhis(statement: AstNode.StatementNode, loopScopeNode: IrNode.ScopeNode, loopRegion: IrNode.LoopRegionNode): List<IrNode.PhiNode> {
        // Create incomplete phis for all variables that are written in the loop body
        val writtenVariables = findWrittenVariablesInStatement(statement)
        val writtenAndReadVariables = writtenVariables.intersect(scopeNode.getAll().keys)

        val incompletePhis = mutableListOf<IrNode.PhiNode>()
        for (variableName in writtenAndReadVariables) {
            val currentValue = readVariable(variableName)
            val incompletePhiNode = IrNode.PhiNode(variableName, currentValue, loopRegion.entryPoint, null, null, loopRegion)
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
    private fun createLoopConditionAndBody(condition: AstNode.ExpressionNode, body: AstNode.StatementNode, lastSideEffectNode: IrNode.SideEffectNode): StatementReturn {
        val (conditionNode, newSideEffectNode, newControlNode) = createIrNodeForExpression(condition, lastSideEffectNode, currentLoopInformation.loopRegion)
        val loopEntryNode = IrNode.IfNode(conditionNode, newControlNode)
        val trueProjectionNode = IrNode.IfProjectionNode(loopEntryNode, IrNode.IfProjectionType.TRUE_BRANCH)
        val falseProjectionNode = IrNode.IfProjectionNode(loopEntryNode, IrNode.IfProjectionType.FALSE_BRANCH)

        currentLoopInformation.addAfterLoopEdge(falseProjectionNode, newSideEffectNode, scopeNode)

        val result = createIrNodeForStatement(body, newSideEffectNode, trueProjectionNode)

        currentLoopInformation.addBackEdge(result.second!!, result.first!!, scopeNode)

        return result
    }

    context(scopeNode: IrNode.ScopeNode)
    private fun updatePhisAtLoopEnd(incompletePhis: List<IrNode.PhiNode>, loopInformation: LoopInformation) {
        // TODO: do we need the side effect node?
        val (variableInfo, _, controlNode) = mergeDefinitions(loopInformation.backEdges)

        loopInformation.loopRegion.backEdge = controlNode

        for ((variableName, nodes) in variableInfo) {
            val (dataNode, controlNode) = nodes
            val incompletePhiNode = incompletePhis.find { it.name == variableName }!!
            incompletePhiNode.second = dataNode
            incompletePhiNode.secondControl = controlNode
        }
    }

    context(scopeNode: IrNode.ScopeNode)
    private fun handleBreakNode(lastSideEffectNode: IrNode.SideEffectNode, lastControlNode: IrNode.ControlNode): StatementReturn {
        currentLoopInformation.addAfterLoopEdge(lastControlNode, lastSideEffectNode, scopeNode)
        return CONTROL_FLOW_END
    }

    context(scopeNode: IrNode.ScopeNode)
    private fun handleContinueNode(lastSideEffectNode: IrNode.SideEffectNode, lastControlNode: IrNode.ControlNode): StatementReturn {
        currentLoopInformation.addBackEdge(lastControlNode, lastSideEffectNode, scopeNode)
        return CONTROL_FLOW_END
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
    private inline fun <T> createNamespace(block: () -> T): T {
        scopeNode.pushNamespace()
        val result = block()
        scopeNode.popNamespace()
        return result
    }

    private inline fun <T> createLoopInformation(loopRegion: IrNode.LoopRegionNode, block: () -> T): Pair<T, LoopInformation> {
        val loopInformation = LoopInformation(loopRegion)
        loopStack.addLast(loopInformation)
        val result = block()
        return result to loopStack.removeLast()
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