package edu.kit.kastel.vads.compiler.ir

import edu.kit.kastel.vads.compiler.CompilerOptions
import edu.kit.kastel.vads.compiler.lexer.Token
import edu.kit.kastel.vads.compiler.parser.AstNode
import edu.kit.kastel.vads.compiler.parser.SymbolName

context(options: CompilerOptions)
fun buildIr(function: AstNode.FunctionNode): IrGraph {
    val statements = getStatementsUntilFirstReturn(function.body.statements)

    val currentDefinitions = mutableMapOf<SymbolName, IrNode>()

    val sideEffectNode = statements.dropLast(1).fold(IrNode.StartNode as IrNode.SideEffectNode) { sideEffectNode, statement ->
        with(currentDefinitions) {
            val (_, newSideEffectNode) = createIrNodeForAstNode(statement, sideEffectNode)
            newSideEffectNode
        }
    }

    val (returnIrNode, _) = with(currentDefinitions) {
        val returnStatement = statements.last()
        require(returnStatement is AstNode.ReturnNode) { "Last statement must be a return statement" }
        createReturnIrNode(returnStatement, sideEffectNode)
    }

    return IrGraph(returnIrNode, function.name.name.asString())
}

context(currentDefinitions: MutableMap<SymbolName, IrNode>)
private fun createIrNodeForAstNode(
    astNode: AstNode,
    lastSideEffectNode: IrNode.SideEffectNode
): Pair<IrNode, IrNode.SideEffectNode> {
    return when (astNode) {
        is AstNode.BinaryOperationNode -> createBinaryOperationIrNode(astNode, lastSideEffectNode)
        is AstNode.IdentifierExpressionNode -> handleIdentifierExpressionNode(astNode, lastSideEffectNode)
        is AstNode.LiteralNode -> Pair(createConstantIntegerIrNode(astNode), lastSideEffectNode)
        is AstNode.NegateNode -> createNegateIrNode(astNode, lastSideEffectNode)
        is AstNode.AssignmentNode -> handleAssignmentNode(astNode, lastSideEffectNode)
        is AstNode.DeclarationNode -> handleDeclarationNode(astNode, lastSideEffectNode)
        is AstNode.ReturnNode -> createReturnIrNode(astNode, lastSideEffectNode)
        is AstNode.LValueIdentifierNode -> TODO()
        is AstNode.NameNode -> TODO()
        is AstNode.TypeNode -> TODO()
        is AstNode.FunctionNode -> TODO()
        is AstNode.BlockNode -> TODO()
        is AstNode.ProgramNode -> TODO()
    }
}

context(currentDefinitions: MutableMap<SymbolName, IrNode>)
private fun createBinaryOperationIrNode(
    binaryOperationAstNode: AstNode.BinaryOperationNode,
    lastSideEffectNode: IrNode.SideEffectNode
): Pair<IrNode, IrNode.SideEffectNode> {
    val (leftIrNode, newSideEffectNode) = createIrNodeForAstNode(binaryOperationAstNode.lhs, lastSideEffectNode)
    val (rightIrNode, newSideEffectNode2) = createIrNodeForAstNode(binaryOperationAstNode.rhs, newSideEffectNode)

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

        else -> error("Unsupported operator type: ${binaryOperationAstNode.operatorType}")
    }
}

context(currentDefinitions: MutableMap<SymbolName, IrNode>)
private fun handleIdentifierExpressionNode(identifierAstNode: AstNode.IdentifierExpressionNode, lastSideEffectNode: IrNode.SideEffectNode): Pair<IrNode, IrNode.SideEffectNode> {
    val variableName = identifierAstNode.name.name
    val irNode = readVariable(variableName)
    return irNode to lastSideEffectNode
}

context(currentDefinitions: MutableMap<SymbolName, IrNode>)
private fun createNegateIrNode(
    negateAstNode: AstNode.NegateNode,
    lastSideEffectNode: IrNode.SideEffectNode
): Pair<IrNode, IrNode.SideEffectNode> {
    val (expressionIrNode, newSideEffectNode) = createIrNodeForAstNode(negateAstNode.expression, lastSideEffectNode)
    return Pair(IrNode.NegateNode(expressionIrNode), newSideEffectNode)
}

context(currentDefinitions: MutableMap<SymbolName, IrNode>)
private fun handleAssignmentNode(assignmentNode: AstNode.AssignmentNode, lastSideEffectNode: IrNode.SideEffectNode): Pair<IrNode, IrNode.SideEffectNode> {
    val desugar: ((IrNode, IrNode, IrNode.SideEffectNode) -> Pair<IrNode, IrNode.SideEffectNode>)? = when (assignmentNode.operator.type) {
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
        else -> error("Unsupported assignment operator: ${assignmentNode.operator.type}")
    }

    val (expressionNode, newSideEffectNode) = createIrNodeForAstNode(assignmentNode.expression, lastSideEffectNode)

    when (val lValue = assignmentNode.lValue) {
        is AstNode.LValueIdentifierNode -> {
            val (newValue, newSideEffectNode2) = desugar?.invoke(readVariable(lValue.name.name), expressionNode, newSideEffectNode) ?: Pair(expressionNode, newSideEffectNode)
            writeVariable(lValue.name.name, newValue)
            return newValue to newSideEffectNode2
        }
    }
}

context(currentDefinitions: MutableMap<SymbolName, IrNode>)
private fun handleDeclarationNode(declarationNode: AstNode.DeclarationNode, lastSideEffectNode: IrNode.SideEffectNode): Pair<IrNode, IrNode.SideEffectNode> {
    if (declarationNode.initializer == null) {
        // TODO: figure out a better way to hande this case
        // for now, we just create a no-op node
        // this is fine as the node should never be used anywhere as the semantic analysis catches uses of uninitialized variables
        return IrNode.NoOpNode to lastSideEffectNode
    }

    val (expressionIrNode, newSideEffectNode) = createIrNodeForAstNode(declarationNode.initializer, lastSideEffectNode)
    writeVariable(declarationNode.name.name, expressionIrNode)
    return expressionIrNode to newSideEffectNode
}

private fun createConstantIntegerIrNode(literalAstNode: AstNode.LiteralNode): IrNode.IntegerConstantNode {
    return IrNode.IntegerConstantNode(literalAstNode.parseValue()!!)
}

context(currentDefinitions: MutableMap<SymbolName, IrNode>)
private fun createReturnIrNode(
    astNode: AstNode.ReturnNode,
    lastSideEffectNode: IrNode.SideEffectNode
): Pair<IrNode.ReturnNode, IrNode.SideEffectNode> {
    val (expressionIrNode, newSideEffectNode) = createIrNodeForAstNode(astNode.expression, lastSideEffectNode)
    return Pair(IrNode.ReturnNode(expressionIrNode, newSideEffectNode), newSideEffectNode)
}

context(currentDefinitions: MutableMap<SymbolName, IrNode>)
private fun writeVariable(variableName: SymbolName, value: IrNode) {
    currentDefinitions[variableName] = value
}

context(currentDefinitions: MutableMap<SymbolName, IrNode>)
private fun readVariable(variableName: SymbolName): IrNode {
    return currentDefinitions[variableName] ?: TODO("look in predecessor blocks once we have them")
}

private fun getStatementsUntilFirstReturn(statements: List<AstNode>): List<AstNode> {
    val returnIndex = statements.indexOfFirst { it is AstNode.ReturnNode }
    return if (returnIndex == -1) {
        statements
    } else {
        statements.subList(0, returnIndex + 1)
    }
}
