package edu.kit.kastel.vads.compiler.typechecker

import edu.kit.kastel.vads.compiler.lexer.Token
import edu.kit.kastel.vads.compiler.parser.AstNode
import edu.kit.kastel.vads.compiler.parser.SymbolName
import edu.kit.kastel.vads.compiler.parser.visitor.VisitorWithParents
import edu.kit.kastel.vads.compiler.semantic.SemanticAnalysis
import edu.kit.kastel.vads.compiler.semantic.SemanticError
import edu.kit.kastel.vads.compiler.util.NamespaceStack

sealed interface TypeError {
    data class TypeMismatchSingleNode(val node: AstNode, val actual: Type, val expected: Type) : TypeError
    data class TypeMismatchTwoNodes(val node1: AstNode, val type1: Type, val node2: AstNode, val type2: Type) : TypeError
}

object TypeChecking : SemanticAnalysis {
    override fun analyze(program: AstNode.ProgramNode): List<SemanticError> {
        val errors = mutableListOf<TypeError>()

        val functionTypes =
            program.topLevelFunctions.associate { function -> function.name.name to Type.FunctionType(function.returnType.type, function.parameters.map { it.type.type }) }

        val visitor = TypeCheckingVisitor(functionTypes)
        program.accept(visitor, Unit)
        errors += visitor.errors

        return errors.map { SemanticError.TypeErrorWrapper(it) }
    }
}

private class TypeCheckingVisitor(private val functionTypes: Map<SymbolName, Type.FunctionType>) : VisitorWithParents() {

    val errors: MutableList<TypeError> = mutableListOf()
    private val typeCache: MutableMap<AstNode.ExpressionNode, Type> = mutableMapOf()
    private val symbolTable = NamespaceStack<Type>()

    override fun visit(blockNode: AstNode.BlockNode, parents: List<AstNode>) {
        createNamespace {
            blockNode.statements.forEach { typeCheck(it) }
        }
    }

    override fun visit(parameterNode: AstNode.ParameterNode, parents: List<AstNode>) {
        symbolTable.setInTopMost(parameterNode.name, parameterNode.type.type)
    }

    override fun visit(declarationNode: AstNode.DeclarationNode, parents: List<AstNode>) {
        symbolTable.setInTopMost(declarationNode.name, declarationNode.type.type)

        if (declarationNode.initializer != null) {
            val initializerType = typeCheck(declarationNode.initializer)
            compareTypes(declarationNode.type.type, initializerType, declarationNode.initializer)
        }
    }

    override fun visit(assignmentNode: AstNode.AssignmentNode, parents: List<AstNode>) {
        require(assignmentNode.lValue is AstNode.LValueIdentifierNode) { TODO("Currently only identifier lValues are supported") }
        val lValueType = symbolTable[assignmentNode.lValue.name] ?: error("Variable status analysis failed")
        val rValueType = typeCheck(assignmentNode.expression)

        if (assignmentNode.operator == Token.OperatorType.ASSIGN) {
            compareTypes(assignmentNode.lValue, lValueType, assignmentNode.expression, rValueType)
            return
        }

        val (expectedLValueType, expectedRValueType) = assignmentNode.operator.expectedType()
        compareTypes(expectedLValueType, lValueType, assignmentNode.lValue)
        compareTypes(expectedRValueType, rValueType, assignmentNode.expression)
    }

    override fun visit(binaryOperationNode: AstNode.BinaryOperationNode, parents: List<AstNode>) {
        val leftType = typeCheck(binaryOperationNode.left)
        val rightType = typeCheck(binaryOperationNode.right)

        if (binaryOperationNode.operatorType == Token.OperatorType.EQUAL || binaryOperationNode.operatorType == Token.OperatorType.NOT_EQUAL) {
            compareTypes(binaryOperationNode.left, leftType, binaryOperationNode.right, rightType)
            typeCache[binaryOperationNode] = Type.BoolType
            return
        }

        val (expectedLeftType, expectedRightType, resultType) = binaryOperationNode.operatorType.expectedType()
        compareTypes(expectedLeftType, leftType, binaryOperationNode.left)
        compareTypes(expectedRightType, rightType, binaryOperationNode.right)

        typeCache[binaryOperationNode] = resultType
    }

    override fun visit(unaryOperationNode: AstNode.UnaryOperationNode, parents: List<AstNode>) {
        val expressionType = typeCheck(unaryOperationNode.expression)

        val expectedType = when (unaryOperationNode.operator) {
            Token.OperatorType.SUB_OR_NEGATE, Token.OperatorType.BITWISE_NOT -> Type.IntType
            Token.OperatorType.LOGICAL_NOT -> Type.BoolType
            Token.OperatorType.DEREFERENCE_OR_MUL -> TODO()
        }

        compareTypes(expectedType, expressionType, unaryOperationNode.expression)

        typeCache[unaryOperationNode] = expectedType
    }

    override fun visit(returnNode: AstNode.ReturnNode, parents: List<AstNode>) {
        val returnType = typeCheck(returnNode.expression)
        val functionType = parents.filterIsInstance<AstNode.FunctionNode>().first().returnType.type
        compareTypes(functionType, returnType, returnNode.expression)
    }

    override fun visit(ifNode: AstNode.IfNode, parents: List<AstNode>) {
        val conditionType = typeCheck(ifNode.condition)
        compareTypes(Type.BoolType, conditionType, ifNode.condition)

        typeCheck(ifNode.body)

        if (ifNode.elseStatement != null) {
            typeCheck(ifNode.elseStatement)
        }
    }

    override fun visit(whileNode: AstNode.WhileNode, parents: List<AstNode>) {
        val conditionType = typeCheck(whileNode.condition)
        compareTypes(Type.BoolType, conditionType, whileNode.condition)

        typeCheck(whileNode.body)
    }

    override fun visit(forNode: AstNode.ForNode, parents: List<AstNode>) {
        createNamespace {
            if (forNode.initializer != null) {
                typeCheck(forNode.initializer)
            }

            val conditionType = typeCheck(forNode.condition)
            compareTypes(Type.BoolType, conditionType, forNode.condition)

            if (forNode.increment != null) {
                typeCheck(forNode.increment)
            }

            typeCheck(forNode.body)
        }
    }

    override fun visit(ternaryOperationNode: AstNode.TernaryOperationNode, parents: List<AstNode>) {
        val conditionType = typeCheck(ternaryOperationNode.condition)
        compareTypes(Type.BoolType, conditionType, ternaryOperationNode.condition)

        val trueBranchType = typeCheck(ternaryOperationNode.trueExpression)
        val falseBranchType = typeCheck(ternaryOperationNode.falseExpression)

        compareTypes(ternaryOperationNode.trueExpression, trueBranchType, ternaryOperationNode.falseExpression, falseBranchType)
        typeCache[ternaryOperationNode] = trueBranchType
    }

    override fun visit(callNormalNode: AstNode.CallNormalNode, parents: List<AstNode>) {
        val functionType = functionTypes[callNormalNode.name.name]!!
        compareFunctionTypes(callNormalNode.arguments, functionType)

        typeCache[callNormalNode] = functionType.returnType
    }

    override fun visit(callBuiltinNode: AstNode.CallBuiltinNode, parents: List<AstNode>) {
        val functionType = Type.getTypeForBuiltinFunction(callBuiltinNode.keyword)
        compareFunctionTypes(callBuiltinNode.arguments, functionType)

        typeCache[callBuiltinNode] = functionType.returnType
    }

    private fun compareFunctionTypes(arguments: List<AstNode.ExpressionNode>, functionType: Type.FunctionType) {
        val argumentsToType = arguments.associateWith { typeCheck(it) }.toList()
        for ((expectedType, actual) in functionType.parameterTypes.zip(argumentsToType)) {
            val (node, actualType) = actual
            compareTypes(expectedType, actualType, node)
        }
    }

    override fun visit(functionNode: AstNode.FunctionNode, parents: List<AstNode>) {
        createNamespace {
            functionNode.parameters.forEach { it.accept(this, Unit) }
            functionNode.body.accept(this, Unit)
        }
    }

    override fun visit(programNode: AstNode.ProgramNode, parents: List<AstNode>) {
        programNode.topLevelFunctions.forEach { it.accept(this, Unit) }
    }

    private fun typeCheck(node: AstNode.ExpressionNode): Type {
        val type = when (node) {
            is AstNode.LiteralNode -> node.type
            is AstNode.IdentifierExpressionNode -> symbolTable[node.name] ?: error("Variable status analysis failed")

            else -> {
                node.accept(this, Unit)
                typeCache[node] ?: error("Type checking failed")
            }
        }
        return type
    }

    private fun typeCheck(node: AstNode.StatementNode) {
        node.accept(this, Unit)
    }

    private fun compareTypes(expected: Type, actual: Type, node: AstNode) {
        if (expected != actual) {
            errors += TypeError.TypeMismatchSingleNode(node, actual, expected)
        }
    }

    private fun compareTypes(node1: AstNode, type1: Type, node2: AstNode, type2: Type) {
        if (type1 != type2) {
            errors += TypeError.TypeMismatchTwoNodes(node1, type1, node2, type2)
        }
    }

    private inline fun createNamespace(block: () -> Unit) {
        symbolTable.pushNamespace()
        block()
        symbolTable.popNamespace()
    }
}

private data class BinaryOperationType(val leftType: Type, val rightType: Type, val resultType: Type)

private val INT_TO_INT = BinaryOperationType(Type.IntType, Type.IntType, Type.IntType)
private val INT_TO_BOOL = BinaryOperationType(Type.IntType, Type.IntType, Type.BoolType)
private val BOOL_TO_BOOL = BinaryOperationType(Type.BoolType, Type.BoolType, Type.BoolType)

private fun Token.OperatorType.BinaryOperatorType.expectedType(): BinaryOperationType = when (this) {
    Token.OperatorType.ADD,
    Token.OperatorType.SUB_OR_NEGATE,
    Token.OperatorType.DEREFERENCE_OR_MUL,
    Token.OperatorType.DIV,
    Token.OperatorType.MOD,
    Token.OperatorType.BITWISE_AND,
    Token.OperatorType.BITWISE_OR,
    Token.OperatorType.BITWISE_XOR,
    Token.OperatorType.LEFT_SHIFT,
    Token.OperatorType.RIGHT_SHIFT,
    Token.OperatorType.ASSIGN_ADD,
    Token.OperatorType.ASSIGN_SUB,
    Token.OperatorType.ASSIGN_MUL,
    Token.OperatorType.ASSIGN_DIV,
    Token.OperatorType.ASSIGN_MOD,
    Token.OperatorType.ASSIGN_BITWISE_AND,
    Token.OperatorType.ASSIGN_BITWISE_OR,
    Token.OperatorType.ASSIGN_BITWISE_XOR,
    Token.OperatorType.ASSIGN_LEFT_SHIFT,
    Token.OperatorType.ASSIGN_RIGHT_SHIFT,
        -> INT_TO_INT

    Token.OperatorType.GREATER_EQUAL,
    Token.OperatorType.GREATER_THAN,
    Token.OperatorType.LESS_EQUAL,
    Token.OperatorType.LESS_THAN,
        -> INT_TO_BOOL

    Token.OperatorType.LOGICAL_AND,
    Token.OperatorType.LOGICAL_OR
        -> BOOL_TO_BOOL

    Token.OperatorType.EQUAL, Token.OperatorType.NOT_EQUAL -> error("The comparison operators' expected type is always to be the same on both sides")
    Token.OperatorType.ASSIGN -> error("The assignment operator's expected type is always to just be the same on both sides")
    Token.OperatorType.FIELD_ACCESS -> TODO()
    Token.OperatorType.FIELD_DEREFERENCE -> TODO()
}
