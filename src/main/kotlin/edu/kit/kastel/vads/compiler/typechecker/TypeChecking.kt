package edu.kit.kastel.vads.compiler.typechecker

import edu.kit.kastel.vads.compiler.lexer.Token
import edu.kit.kastel.vads.compiler.parser.AstNode
import edu.kit.kastel.vads.compiler.parser.visitor.VisitorWithoutData
import edu.kit.kastel.vads.compiler.semantic.Namespace
import edu.kit.kastel.vads.compiler.semantic.SemanticAnalysis
import edu.kit.kastel.vads.compiler.semantic.SemanticError

sealed interface TypeError {
    data class MainMustReturnInt(val node: AstNode.FunctionNode) : TypeError
    data class TypeMismatchSingleNode(val node: AstNode, val actual: Type, val expected: Type) : TypeError
    data class TypeMismatchTwoNodes(val node1: AstNode, val type1: Type, val node2: AstNode, val type2: Type) : TypeError
}

object TypeChecking : SemanticAnalysis {
    override fun analyze(program: AstNode.ProgramNode): List<SemanticError> {
        val errors = mutableListOf<TypeError>()
        if (program.topLevelFunctions.first().returnType.type != Type.IntType) {
            errors += TypeError.MainMustReturnInt(program.topLevelFunctions.first())
        }

        val visitor = TypeCheckingVisitor()
        program.accept(visitor, Unit)
        errors += visitor.errors

        return errors.map { SemanticError.TypeErrorWrapper(it) }
    }
}

private class TypeCheckingVisitor : VisitorWithoutData() {

    val errors: MutableList<TypeError> = mutableListOf()
    private val typeCache: MutableMap<AstNode.ExpressionNode, Type> = mutableMapOf()
    private val symbolTable: SymbolTable = SymbolTable()

    override fun visit(programNode: AstNode.ProgramNode) {
        programNode.topLevelFunctions.forEach { it.accept(this, Unit) }
    }

    override fun visit(functionNode: AstNode.FunctionNode) {
        functionNode.body.accept(this, Unit)
    }

    override fun visit(blockNode: AstNode.BlockNode) {
        createNamespace {
            blockNode.statements.forEach { typeCheck(it) }
        }
    }

    override fun visit(declarationNode: AstNode.DeclarationNode) {
        symbolTable.declareType(declarationNode.name, declarationNode.type.type)

        if (declarationNode.initializer != null) {
            val initializerType = typeCheck(declarationNode.initializer)
            compareTypes(declarationNode.type.type, initializerType, declarationNode.initializer)
        }
    }

    override fun visit(assignmentNode: AstNode.AssignmentNode) {
        require(assignmentNode.lValue is AstNode.LValueIdentifierNode) { TODO("Currently only identifier lValues are supported") }
        val lValueType = symbolTable.getType(assignmentNode.lValue.name) ?: error("Variable status analysis failed")
        val rValueType = typeCheck(assignmentNode.expression)

        if (assignmentNode.operator == Token.OperatorType.ASSIGN) {
            compareTypes(assignmentNode.lValue, lValueType, assignmentNode.expression, rValueType)
            return
        }

        val (expectedLValueType, expectedRValueType) = assignmentNode.operator.expectedType()
        compareTypes(expectedLValueType, lValueType, assignmentNode.lValue)
        compareTypes(expectedRValueType, rValueType, assignmentNode.expression)
    }

    override fun visit(binaryOperationNode: AstNode.BinaryOperationNode) {
        val leftType = typeCheck(binaryOperationNode.lhs)
        val rightType = typeCheck(binaryOperationNode.rhs)

        if (binaryOperationNode.operatorType == Token.OperatorType.EQUAL || binaryOperationNode.operatorType == Token.OperatorType.NOT_EQUAL) {
            compareTypes(binaryOperationNode.lhs, leftType, binaryOperationNode.rhs, rightType)
            typeCache[binaryOperationNode] = Type.BoolType
            return
        }

        val (expectedLeftType, expectedRightType, resultType) = binaryOperationNode.operatorType.expectedType()
        compareTypes(expectedLeftType, leftType, binaryOperationNode.lhs)
        compareTypes(expectedRightType, rightType, binaryOperationNode.rhs)

        typeCache[binaryOperationNode] = resultType
    }

    override fun visit(unaryOperationNode: AstNode.UnaryOperationNode) {
        val expressionType = typeCheck(unaryOperationNode.expression)

        val expectedType = when (unaryOperationNode.operator) {
            Token.OperatorType.SUB, Token.OperatorType.BITWISE_NOT -> Type.IntType
            Token.OperatorType.LOGICAL_NOT -> Type.BoolType
        }

        compareTypes(expectedType, expressionType, unaryOperationNode.expression)

        typeCache[unaryOperationNode] = expectedType
    }

    override fun visit(returnNode: AstNode.ReturnNode) {
        val returnType = typeCheck(returnNode.expression)
        compareTypes(Type.IntType, returnType, returnNode.expression)
    }

    override fun visit(ifNode: AstNode.IfNode) {
        val conditionType = typeCheck(ifNode.condition)
        compareTypes(Type.BoolType, conditionType, ifNode.condition)

        typeCheck(ifNode.body)

        if (ifNode.elseStatement != null) {
            typeCheck(ifNode.elseStatement)
        }
    }

    override fun visit(whileNode: AstNode.WhileNode) {
        val conditionType = typeCheck(whileNode.condition)
        compareTypes(Type.BoolType, conditionType, whileNode.condition)

        typeCheck(whileNode.body)
    }

    override fun visit(forNode: AstNode.ForNode) {
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

    override fun visit(ternaryOperationNode: AstNode.TernaryOperationNode) {
        val conditionType = typeCheck(ternaryOperationNode.condition)
        compareTypes(Type.BoolType, conditionType, ternaryOperationNode.condition)

        val trueBranchType = typeCheck(ternaryOperationNode.trueExpression)
        val falseBranchType = typeCheck(ternaryOperationNode.falseExpression)

        compareTypes(ternaryOperationNode.trueExpression, trueBranchType, ternaryOperationNode.falseExpression, falseBranchType)
        typeCache[ternaryOperationNode] = trueBranchType
    }

    private fun typeCheck(node: AstNode.ExpressionNode): Type {
        val type = when (node) {
            is AstNode.LiteralNode -> node.type
            is AstNode.IdentifierExpressionNode -> symbolTable.getType(node.name) ?: error("Variable status analysis failed")

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
    Token.OperatorType.SUB,
    Token.OperatorType.MUL,
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
}

private class SymbolTable {
    private val namespaces = mutableListOf<Namespace<Type>>()

    fun pushNamespace() = namespaces.addFirst(Namespace())
    fun popNamespace() = namespaces.removeFirst()

    fun declareType(name: AstNode.NameNode, type: Type) {
        namespaces.first().put(name, type)
    }

    fun getType(name: AstNode.NameNode): Type? {
        return namespaces.firstNotNullOfOrNull { it.get(name) }
    }
}
