package edu.kit.kastel.vads.compiler.ir

import edu.kit.kastel.vads.compiler.CompilerOptions
import edu.kit.kastel.vads.compiler.lexer.Token
import edu.kit.kastel.vads.compiler.parser.AstNode
import edu.kit.kastel.vads.compiler.parser.SymbolName
import edu.kit.kastel.vads.compiler.parser.visitor.RecursivePostorderVisitor
import edu.kit.kastel.vads.compiler.parser.visitor.VisitorWithoutData
import edu.kit.kastel.vads.compiler.util.NamespaceStack

context(compilerOptions: CompilerOptions)
fun buildIr(program: AstNode.ProgramNode): IrProgram {
    val graphs = program.topLevelFunctions.map { buildIr(it) }
    return IrProgram(graphs)
}

context(compilerOptions: CompilerOptions)
private fun buildIr(function: AstNode.FunctionNode): IrGraph = SsaConstructor(compilerOptions).buildIr(function)

private data class StatementReturn(val sideEffectNode: IrNode.SideEffectNode, val controlNode: IrNode.ControlNode?, val controlFlowEnded: Boolean = false)
private typealias ExpressionReturn = Triple<IrNode.DataNode, IrNode.SideEffectNode, IrNode.ControlNode>

private typealias SymbolTable = NamespaceStack<IrNode.DataNode>

private class LoopInformation(val loopRegion: IrNode.LoopRegionNode) {
    private var _backEdges = mutableListOf<Pair<IrNode.ControlNode, SymbolTable>>()
    private var _afterLoopEdges = mutableListOf<Pair<IrNode.ControlNode, SymbolTable>>()

    val backEdges get() = _backEdges
    val afterLoopEdges get() = _afterLoopEdges

    fun addBackEdge(controlNode: IrNode.ControlNode, symbolTable: SymbolTable): Boolean {
        return backEdges.add(Pair(controlNode, symbolTable))
    }

    fun addAfterLoopEdge(controlNode: IrNode.ControlNode, symbolTable: SymbolTable): Boolean {
        return afterLoopEdges.add(Pair(controlNode, symbolTable))
    }
}

private class SsaConstructor(val compilerOptions: CompilerOptions) {
    private val returnNodes = mutableListOf<IrNode.ReturnNode>()
    private val loopStack = mutableListOf<LoopInformation>()
    private val currentLoopInformation get() = loopStack.last()

    fun buildIr(function: AstNode.FunctionNode): IrGraph {
        val symbolTable = SymbolTable()

        symbolTable.pushNamespace()
        val parameters = function.parameters.map { IrNode.ParameterNode(it.name.name) }
        parameters.forEach { symbolTable[it.name] = it }


        with(symbolTable) {
            createIrNodeForStatement(function.body, IrNode.StartNode, IrNode.StartNode)
        }

        val endNode = IrNode.EndNode(returnNodes)
        return IrGraph(endNode, parameters, function.name.name.asString())
    }

    context(symbolTable: SymbolTable)
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
            is AstNode.CallBuiltinNode -> {
                val (_, sideEffectNode, controlNode) = createBuiltInCallNode(astNode, lastSideEffectNode, lastControlNode)
                StatementReturn(sideEffectNode, controlNode)
            }

            is AstNode.CallNormalNode -> {
                val (_, sideEffectNode, controlNode) = createNormalCallNode(astNode, lastSideEffectNode, lastControlNode)
                StatementReturn(sideEffectNode, controlNode)
            }

            is AstNode.CallAllocArrayNode -> TODO()
            is AstNode.CallAllocNode -> TODO()
        }
    }

    context(symbolTable: SymbolTable)
    private fun createIrNodeForExpression(astNode: AstNode.ExpressionNode, lastSideEffectNode: IrNode.SideEffectNode, lastControlNode: IrNode.ControlNode): ExpressionReturn {
        return when (astNode) {
            is AstNode.BinaryOperationNode -> createBinaryOperationIrNode(astNode, lastSideEffectNode, lastControlNode)
            is AstNode.IdentifierExpressionNode -> handleIdentifierExpressionNode(astNode, lastSideEffectNode, lastControlNode)
            is AstNode.LiteralNode -> Triple(createLiteralIrNode(astNode), lastSideEffectNode, lastControlNode)
            is AstNode.UnaryOperationNode -> createUnaryOperationIrNode(astNode, lastSideEffectNode, lastControlNode)
            is AstNode.TernaryOperationNode -> handleTernaryOperationNode(astNode, lastSideEffectNode, lastControlNode)
            is AstNode.CallBuiltinNode -> createBuiltInCallNode(astNode, lastSideEffectNode, lastControlNode)
            is AstNode.CallNormalNode -> createNormalCallNode(astNode, lastSideEffectNode, lastControlNode)
            is AstNode.FieldAccessNode -> TODO()
            is AstNode.FieldDereferenceNode -> TODO()
            is AstNode.NullLiteralNode -> TODO()
            is AstNode.PointerDereferenceNode -> TODO()
            is AstNode.CallAllocArrayNode -> TODO()
            is AstNode.CallAllocNode -> TODO()
            is AstNode.ArrayAccessNode -> TODO()
        }
    }

    private fun <A, B, C> Triple<A, B, C>.lastTwo() = second to third

    context(symbolTable: SymbolTable)
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
            return Triple(irNode, irNode, newControlNode2)
        }

        val irNode = when (binaryOperationAstNode.operatorType) {
            Token.OperatorType.ADD -> IrNode.AddNode(leftIrNode, rightIrNode)
            Token.OperatorType.SUB_OR_NEGATE -> IrNode.SubNode(leftIrNode, rightIrNode)
            Token.OperatorType.DEREFERENCE_OR_MUL -> IrNode.MulNode(leftIrNode, rightIrNode)
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

    context(symbolTable: SymbolTable)
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
            IrNode.SideEffectPhiNode(newSideEffectNode, newSideEffectNode2, newControlNode, newControlNode2)
        }

        return Triple(phiNode, sideEffectNode, regionNode)
    }

    context(symbolTable: SymbolTable)
    private fun handleIdentifierExpressionNode(
        identifierAstNode: AstNode.IdentifierExpressionNode,
        lastSideEffectNode: IrNode.SideEffectNode,
        lastControlNode: IrNode.ControlNode
    ): ExpressionReturn {
        val variableName = identifierAstNode.name.name
        val irNode = readVariable(variableName)
        return Triple(irNode, lastSideEffectNode, lastControlNode)
    }

    context(symbolTable: SymbolTable)
    private fun createUnaryOperationIrNode(
        unaryOperationNode: AstNode.UnaryOperationNode,
        lastSideEffectNode: IrNode.SideEffectNode,
        lastControlNode: IrNode.ControlNode
    ): ExpressionReturn {
        val (expressionIrNode, newSideEffectNode, newControlNode) = createIrNodeForExpression(unaryOperationNode.expression, lastSideEffectNode, lastControlNode)

        val unaryOperationIrNode = when (unaryOperationNode.operator) {
            Token.OperatorType.SUB_OR_NEGATE -> IrNode.NegateNode(expressionIrNode)
            Token.OperatorType.LOGICAL_NOT -> IrNode.LogicalNotNode(expressionIrNode)
            Token.OperatorType.BITWISE_NOT -> IrNode.BitwiseNotNode(expressionIrNode)
            Token.OperatorType.DEREFERENCE_OR_MUL -> TODO()
        }

        return Triple(unaryOperationIrNode, newSideEffectNode, newControlNode)
    }

    context(symbolTable: SymbolTable)
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
            newSideEffectNode
        } else {
            IrNode.SideEffectPhiNode(trueExpressionSideEffectNode, falseExpressionSideEffectNode, trueExpressionControlNode, falseExpressionControlNode)
        }

        return Triple(phiNode, newSideEffectNode2, regionNode)
    }

    context(symbolTable: SymbolTable)
    private fun handleAssignmentNode(assignmentNode: AstNode.AssignmentNode, lastSideEffectNode: IrNode.SideEffectNode, lastControlNode: IrNode.ControlNode): StatementReturn {
        val desugar: ((IrNode.DataNode, IrNode.DataNode, IrNode.SideEffectNode) -> Pair<IrNode.DataNode, IrNode.SideEffectNode>)? = when (assignmentNode.operator) {
            Token.OperatorType.ASSIGN -> null
            Token.OperatorType.ASSIGN_ADD -> { left, right, sideEffect -> IrNode.AddNode(left, right) to sideEffect }
            Token.OperatorType.ASSIGN_SUB -> { left, right, sideEffect -> IrNode.SubNode(left, right) to sideEffect }
            Token.OperatorType.ASSIGN_MUL -> { left, right, sideEffect -> IrNode.MulNode(left, right) to sideEffect }
            Token.OperatorType.ASSIGN_DIV -> { left, right, sideEffectNode ->
                val divNode = IrNode.DivNode(left, right, sideEffectNode)
                divNode to divNode
            }

            Token.OperatorType.ASSIGN_MOD -> { left, right, sideEffectNode ->
                val modNode = IrNode.ModNode(left, right, sideEffectNode)
                modNode to modNode
            }

            Token.OperatorType.ASSIGN_BITWISE_AND -> { left, right, sideEffect -> IrNode.BitwiseAndNode(left, right) to sideEffect }
            Token.OperatorType.ASSIGN_BITWISE_XOR -> { left, right, sideEffect -> IrNode.BitwiseXorNode(left, right) to sideEffect }
            Token.OperatorType.ASSIGN_BITWISE_OR -> { left, right, sideEffect -> IrNode.BitwiseOrNode(left, right) to sideEffect }
            Token.OperatorType.ASSIGN_LEFT_SHIFT -> { left, right, sideEffect -> IrNode.LeftShiftNode(left, right) to sideEffect }
            Token.OperatorType.ASSIGN_RIGHT_SHIFT -> { left, right, sideEffect -> IrNode.RightShiftNode(left, right) to sideEffect }
        }

        val (expressionNode, newSideEffectNode, newControlNode) = createIrNodeForExpression(assignmentNode.expression, lastSideEffectNode, lastControlNode)

        when (val lValue = assignmentNode.lValue) {
            is AstNode.LValueIdentifierNode -> {
                val (newValue, newSideEffectNode2) = desugar?.invoke(readVariable(lValue.name.name), expressionNode, newSideEffectNode) ?: Pair(expressionNode, newSideEffectNode)
                writeVariable(lValue.name.name, newValue)
                return StatementReturn(newSideEffectNode2, newControlNode)
            }

            is AstNode.LValueArrayAccessNode -> TODO()
            is AstNode.LValueFieldAccessNode -> TODO()
            is AstNode.LValueFieldDereferenceNode -> TODO()
            is AstNode.LValuePointerDereferenceNode -> TODO()
        }
    }

    context(symbolTable: SymbolTable)
    private fun handleDeclarationNode(declarationNode: AstNode.DeclarationNode, lastSideEffectNode: IrNode.SideEffectNode, lastControlNode: IrNode.ControlNode): StatementReturn {
        if (declarationNode.initializer == null) {
            return StatementReturn(lastSideEffectNode, lastControlNode)
        }

        val (expressionIrNode, newSideEffectNode, newControlNode) = createIrNodeForExpression(declarationNode.initializer, lastSideEffectNode, lastControlNode)
        writeVariable(declarationNode.name.name, expressionIrNode)
        return StatementReturn(newSideEffectNode, newControlNode)
    }

    context(symbolTable: SymbolTable)
    private fun handleBlockNode(blockNode: AstNode.BlockNode, lastSideEffectNode: IrNode.SideEffectNode, lastControlNode: IrNode.ControlNode): StatementReturn {
        val statements = getStatementsUntilFirstControlFlowEndingStatement(blockNode.statements)

        val initial = StatementReturn(lastSideEffectNode, lastControlNode)
        return statements.fold(initial) { (sideEffectNode, controlNode, _), statement ->
            createIrNodeForStatement(statement, sideEffectNode, controlNode!!)
        }
    }

    private fun createLiteralIrNode(literalAstNode: AstNode.LiteralNode): IrNode.ConstantNode<*> {
        return when (literalAstNode) {
            is AstNode.BooleanLiteralNode -> IrNode.BooleanConstantNode(literalAstNode.value)
            is AstNode.IntLiteralNode -> IrNode.IntegerConstantNode(literalAstNode.parseValue()!!)
        }
    }

    context(symbolTable: SymbolTable)
    private fun handleReturnNode(astNode: AstNode.ReturnNode, lastSideEffectNode: IrNode.SideEffectNode, lastControlNode: IrNode.ControlNode): StatementReturn {
        val (expressionIrNode, newSideEffectNode, newControlNode) = createIrNodeForExpression(astNode.expression, lastSideEffectNode, lastControlNode)
        val returnNode = IrNode.ReturnNode(expressionIrNode, newSideEffectNode, newControlNode)
        returnNodes.add(returnNode)
        return StatementReturn(newSideEffectNode, newControlNode, controlFlowEnded = true)
    }

    context(symbolTable: SymbolTable)
    private fun handleIfNode(astNode: AstNode.IfNode, lastSideEffectNode: IrNode.SideEffectNode, lastControlNode: IrNode.ControlNode): StatementReturn {
        val (conditionNode, newSideEffectNode, newControlNode) = createIrNodeForExpression(astNode.condition, lastSideEffectNode, lastControlNode)
        val ifNode = IrNode.IfNode(conditionNode, newControlNode)

        val trueProjectionNode = IrNode.IfProjectionNode(ifNode, IrNode.IfProjectionType.TRUE_BRANCH)
        val falseProjectionNode = IrNode.IfProjectionNode(ifNode, IrNode.IfProjectionType.FALSE_BRANCH)

        val bodySymbolTable = symbolTable.duplicate()

        val (bodySideEffectNode, bodyControlNode, bodyControlFlowEnded) = with(bodySymbolTable) {
            createIrNodeForStatement(astNode.body, newSideEffectNode, trueProjectionNode)
        }

        val (elseStatementReturn, elseSymbolTable) = if (astNode.elseStatement != null) {
            val elseSymbolTable = symbolTable.duplicate()
            val statementReturn = with(elseSymbolTable) {
                createIrNodeForStatement(astNode.elseStatement, newSideEffectNode, falseProjectionNode)
            }

            Pair(statementReturn, elseSymbolTable)
        } else {
            Pair(StatementReturn(newSideEffectNode, falseProjectionNode), symbolTable)
        }

        val (elseSideEffectNode, elseControlNode, elseControlFlowEnded) = elseStatementReturn

        fun mergeSideEffectsIfNeeded(firstControl: IrNode.ControlNode?, secondControl: IrNode.ControlNode?): IrNode.SideEffectPhiNode {
            if (bodySideEffectNode == elseSideEffectNode) {
                bodySideEffectNode
            }
            return IrNode.SideEffectPhiNode(bodySideEffectNode, elseSideEffectNode, firstControl, secondControl)
        }

        return when {
            bodyControlFlowEnded && elseControlFlowEnded -> {
                StatementReturn(mergeSideEffectsIfNeeded(bodyControlNode, elseControlNode), null, controlFlowEnded = true)
            }

            bodyControlFlowEnded -> {
                val differingDefinitions = symbolTable.merge(elseSymbolTable)
                for ((name, values) in differingDefinitions) {
                    // Propagate the else branch's changes
                    writeVariable(name, values.second)
                }

                StatementReturn(mergeSideEffectsIfNeeded(bodyControlNode, elseControlNode), elseControlNode)
            }

            elseControlFlowEnded -> {
                val differingDefinitions = symbolTable.merge(bodySymbolTable)
                for ((name, values) in differingDefinitions) {
                    // Propagate the body branch's changes
                    writeVariable(name, values.second)
                }

                StatementReturn(mergeSideEffectsIfNeeded(bodyControlNode, elseControlNode), bodyControlNode)
            }

            else -> {
                // No branch transfers control flow elsewhere, so we need to create a region node and merge the scope nodes
                val regionNode = IrNode.RegionNode(bodyControlNode!!, elseControlNode!!)

                val differingDefinitions = bodySymbolTable.merge(elseSymbolTable)
                for ((name, values) in differingDefinitions) {
                    val phiNode = IrNode.PhiNode(name, values.first, bodyControlNode, values.second, elseControlNode, regionNode)
                    writeVariable(name, phiNode)
                }

                StatementReturn(mergeSideEffectsIfNeeded(bodyControlNode, elseControlNode), regionNode)
            }
        }
    }

    context(symbolTable: SymbolTable)
    private fun handleWhileNode(astNode: AstNode.WhileNode, lastSideEffectNode: IrNode.SideEffectNode, lastControlNode: IrNode.ControlNode): StatementReturn {
        val loopRegion = IrNode.LoopRegionNode(lastControlNode, null)
        val whileSymbolTable = symbolTable.duplicate()
        val incompletePhis = createInCompletePhis(astNode.body, whileSymbolTable, loopRegion)

        val (result, loopInformation) = createLoopInformation(loopRegion) {
            with(whileSymbolTable) {
                createLoopConditionAndBody(astNode.condition, astNode.body, lastSideEffectNode)
            }
        }

        updatePhisAtLoopEnd(incompletePhis, loopInformation)

        val afterLoopControlFlowNode = mergeAfterLoopEdges(loopInformation)

        val (bodySideEffectNode, _) = result
        val newSideEffectNode = if (bodySideEffectNode != lastSideEffectNode) {
            IrNode.SideEffectPhiNode(lastSideEffectNode, bodySideEffectNode, lastControlNode, afterLoopControlFlowNode)
        } else {
            lastSideEffectNode
        }
        return StatementReturn(newSideEffectNode, afterLoopControlFlowNode)
    }

    context(symbolTable: SymbolTable)
    private fun handleForNode(astNode: AstNode.ForNode, lastSideEffectNode: IrNode.SideEffectNode, lastControlNode: IrNode.ControlNode): StatementReturn {
        // The for loop is desugared to a while loop with the initializer as a statement before the while loop.
        // The increment is executed at the end of the loop body.
        val (newSideEffectNode, newControlNode, _) = if (astNode.initializer != null) {
            createIrNodeForStatement(astNode.initializer, lastSideEffectNode, lastControlNode)
        } else {
            StatementReturn(lastSideEffectNode, lastControlNode)
        }

        val loopRegion = IrNode.LoopRegionNode(newControlNode!!, null)
        val forSymbolTable = symbolTable.duplicate()
        val incompletePhis = createInCompletePhis(astNode, forSymbolTable, loopRegion)

        val (result, loopInformation) = createLoopInformation(loopRegion) {
            with(forSymbolTable) {
                createLoopConditionAndBody(astNode.condition, astNode.body, newSideEffectNode)
            }
        }

        val (bodySideEffectNode, bodyControlNode) = result

        updatePhisAtLoopEnd(incompletePhis, loopInformation)

        val afterLoopControlFlowNode = mergeAfterLoopEdges(loopInformation)

        // Handle the increment statement
        val sideEffectNodeAfterIncrement = if (astNode.increment != null && bodyControlNode != null) {
            val loopVariableName = ((astNode.increment as AstNode.AssignmentNode).lValue as AstNode.LValueIdentifierNode).name.name
            val loopVariableIncompletePhi = incompletePhis.find { it.name == loopVariableName }!!

            if (loopVariableIncompletePhi.second != null) {
                // loop variable updated in loop
                writeVariable(loopVariableName, loopVariableIncompletePhi.second!!)
            }

            val (incrementSideEffectNode, _) = createIrNodeForStatement(astNode.increment, bodySideEffectNode, bodyControlNode)
            loopVariableIncompletePhi.second = readVariable(loopVariableName)
            loopVariableIncompletePhi.secondControl = loopRegion

            if (newSideEffectNode != incrementSideEffectNode) {
                IrNode.SideEffectPhiNode(newSideEffectNode, incrementSideEffectNode, newControlNode, afterLoopControlFlowNode)
            } else {
                newSideEffectNode
            }
        } else {
            bodySideEffectNode
        }

        return StatementReturn(sideEffectNodeAfterIncrement, afterLoopControlFlowNode)
    }

    /**
     * Merges the definitions of multiple edges.
     * The control flow gets merged using region nodes.
     * For all variables, the values that differ are merged using phi nodes.
     */
    context(symbolTable: SymbolTable)
    private fun mergeDefinitions(edges: List<Pair<IrNode.ControlNode, SymbolTable>>): Pair<Map<SymbolName, Pair<IrNode.DataNode, IrNode.ControlNode>>, IrNode.ControlNode> {
        require(edges.isNotEmpty())

        if (edges.size == 1) {
            // If there is only one edge, we can just use the values from that edge
            val (controlNode, firstSymbolTable) = edges.first()

            // Get all variables that changed in the first scope node
            return Pair(symbolTable.merge(firstSymbolTable).mapValues { it.value.second to controlNode }, controlNode)
        }

        val (firstControlNode, firstSymbolTable) = edges.first()

        val alreadyMergedValues = mutableMapOf<SymbolName, MutableSet<IrNode.DataNode>>()

        val result = mutableMapOf<SymbolName, Pair<IrNode.DataNode, IrNode.ControlNode>>()

        val differingDefinitions = symbolTable.merge(firstSymbolTable)
        for ((variableName, values) in differingDefinitions) {
            val (_, firstEdgeValue) = values
            result[variableName] = firstEdgeValue to firstControlNode
            alreadyMergedValues.getOrPut(variableName) { mutableSetOf() }.add(firstEdgeValue)
        }

        var currentRegionNode = firstControlNode

        for ((edgeControlNode, edgeSymbolTable) in edges.drop(1)) {
            currentRegionNode = IrNode.RegionNode(currentRegionNode, edgeControlNode)

            for ((variableName, value) in edgeSymbolTable.getAll()) {
                val valueWasAlreadyMerged = variableName in alreadyMergedValues && value in (alreadyMergedValues.getOrDefault(variableName, mutableSetOf()))
                if (valueWasAlreadyMerged) {
                    continue
                }

                if (value is IrNode.PhiNode && value.second == null) {
                    // This is an incomplete phi node which means that it belongs to an outer loop, so we just ignore it
                    continue
                }

                if (variableName !in result) {
                    // This variable was not changed
                    continue
                }

                val (currentValue, currentControlNode) = result[variableName]!!
                val phiNode = IrNode.PhiNode(variableName, currentValue, currentControlNode, value, edgeControlNode, currentRegionNode)
                result[variableName] = phiNode to edgeControlNode
            }
        }

        return Pair(result, currentRegionNode)
    }

    context(symbolTable: SymbolTable)
    private fun createInCompletePhis(statement: AstNode.StatementNode, loopSymbolTable: SymbolTable, loopRegion: IrNode.LoopRegionNode): List<IrNode.PhiNode> {
        // Create incomplete phis for all variables that are written in the loop body
        val writtenVariables = findWrittenVariablesInStatement(statement)
        val writtenAndReadVariables = writtenVariables.intersect(symbolTable.getAll().keys)

        val incompletePhis = mutableListOf<IrNode.PhiNode>()
        for (variableName in writtenAndReadVariables) {
            val currentValue = readVariable(variableName)
            val incompletePhiNode = IrNode.PhiNode(variableName, currentValue, loopRegion.entryPoint, null, null, loopRegion)
            incompletePhis.add(incompletePhiNode)
            with(loopSymbolTable) { writeVariable(variableName, incompletePhiNode) }
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

    context(symbolTable: SymbolTable)
    private fun createLoopConditionAndBody(condition: AstNode.ExpressionNode, body: AstNode.StatementNode, lastSideEffectNode: IrNode.SideEffectNode): StatementReturn {
        val (conditionNode, newSideEffectNode, newControlNode) = createIrNodeForExpression(condition, lastSideEffectNode, currentLoopInformation.loopRegion)
        val loopEntryNode = IrNode.IfNode(conditionNode, newControlNode)
        val trueProjectionNode = IrNode.IfProjectionNode(loopEntryNode, IrNode.IfProjectionType.TRUE_BRANCH)
        val falseProjectionNode = IrNode.IfProjectionNode(loopEntryNode, IrNode.IfProjectionType.FALSE_BRANCH)

        // duplicate the scope node as a variable may be written in the loop body, this should not be visible outside the loop as the loop body may not be executed
        currentLoopInformation.addAfterLoopEdge(falseProjectionNode, symbolTable.duplicate())

        val result = createIrNodeForStatement(body, newSideEffectNode, trueProjectionNode)

        if (!result.controlFlowEnded) {
            currentLoopInformation.addBackEdge(result.controlNode!!, symbolTable)
        }

        return result
    }

    context(symbolTable: SymbolTable)
    private fun updatePhisAtLoopEnd(incompletePhis: List<IrNode.PhiNode>, loopInformation: LoopInformation) {
        if (incompletePhis.isEmpty()) {
            return
        }

        val (variableInfo, controlNode) = mergeDefinitions(loopInformation.backEdges)

        loopInformation.loopRegion.backEdge = controlNode

        for ((variableName, nodes) in variableInfo) {
            val (dataNode, controlNode) = nodes
            val incompletePhiNode = incompletePhis.find { it.name == variableName }!!
            incompletePhiNode.second = dataNode
            incompletePhiNode.secondControl = controlNode
        }
    }

    context(symbolTable: SymbolTable)
    private fun mergeAfterLoopEdges(loopInformation: LoopInformation): IrNode.ControlNode {
        // We need to merge the scope nodes of all branches that lead to the end of the loop.
        // This can, however, not be done one after the other, as for example, the first and third scope could only have changes for a variable
        val (variableInfo, controlNode) = mergeDefinitions(loopInformation.afterLoopEdges)
        for ((variableName, nodes) in variableInfo) {
            val (dataNode, _) = nodes
            writeVariable(variableName, dataNode)
        }

        return controlNode
    }


    context(symbolTable: SymbolTable)
    private fun handleBreakNode(lastSideEffectNode: IrNode.SideEffectNode, lastControlNode: IrNode.ControlNode): StatementReturn {
        currentLoopInformation.addAfterLoopEdge(lastControlNode, symbolTable)
        return StatementReturn(lastSideEffectNode, lastControlNode, controlFlowEnded = true)
    }

    context(symbolTable: SymbolTable)
    private fun handleContinueNode(lastSideEffectNode: IrNode.SideEffectNode, lastControlNode: IrNode.ControlNode): StatementReturn {
        currentLoopInformation.addBackEdge(lastControlNode, symbolTable)
        return StatementReturn(lastSideEffectNode, lastControlNode, controlFlowEnded = true)
    }

    context(symbolTable: SymbolTable)
    private fun createBuiltInCallNode(astNode: AstNode.CallBuiltinNode, lastSideEffectNode: IrNode.SideEffectNode, lastControlNode: IrNode.ControlNode): ExpressionReturn {
        val (arguments, newSideEffectNode, newControlNode) = createFunctionCallArguments(astNode.arguments, lastSideEffectNode, lastControlNode)

        val node = when (astNode.keyword) {
            Token.KeywordType.PRINT -> IrNode.PrintNode(arguments.first(), newSideEffectNode, newControlNode)
            Token.KeywordType.READ -> IrNode.ReadNode(newSideEffectNode, newControlNode)
            Token.KeywordType.FLUSH -> IrNode.FlushNode(newSideEffectNode, newControlNode)
            Token.KeywordType.ALLOC -> TODO()
            Token.KeywordType.ALLOC_ARRAY -> TODO()
        }
        return Triple(node, node, node)
    }

    context(symbolTable: SymbolTable)
    private fun createNormalCallNode(astNode: AstNode.CallNormalNode, lastSideEffectNode: IrNode.SideEffectNode, lastControlNode: IrNode.ControlNode): ExpressionReturn {
        val (arguments, newSideEffectNode, newControlNode) = createFunctionCallArguments(astNode.arguments, lastSideEffectNode, lastControlNode)

        val functionName = astNode.name.name
        val node = IrNode.NormalCallNode(functionName, arguments, newSideEffectNode, newControlNode)
        return Triple(node, node, node)
    }

    context(symbolTable: SymbolTable)
    private fun createFunctionCallArguments(
        arguments: List<AstNode.ExpressionNode>,
        lastSideEffectNode: IrNode.SideEffectNode,
        lastControlNode: IrNode.ControlNode
    ): Triple<List<IrNode.DataNode>, IrNode.SideEffectNode, IrNode.ControlNode> {
        val initial = Triple(emptyList<IrNode.DataNode>(), lastSideEffectNode, lastControlNode)
        return arguments.fold(initial) { (accumulatedArgs, sideEffectNode, controlNode), argument ->
            val (argIrNode, newSideEffectNode, newControlNode) = createIrNodeForExpression(argument, sideEffectNode, controlNode)
            Triple(accumulatedArgs + argIrNode, newSideEffectNode, newControlNode)
        }
    }

    context(symbolTable: SymbolTable)
    private fun writeVariable(variableName: SymbolName, value: IrNode.DataNode) {
        symbolTable[variableName] = value
    }

    context(symbolTable: SymbolTable)
    private fun readVariable(variableName: SymbolName): IrNode.DataNode {
        return symbolTable[variableName]!!
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