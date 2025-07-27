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
    data class TypeMismatchSingleNodeKind(val node: AstNode, val actual: Type, val expected: Type.Kind) : TypeError
    data class NullDereference(val node: AstNode) : TypeError
    data class LargeTypeComparison(val node: AstNode, val type: Type) : TypeError
    data class LargeTypeAssignment(val node: AstNode, val type: Type) : TypeError
    data class LargeTypeReturn(val node: AstNode, val type: Type) : TypeError
}

object TypeChecking : SemanticAnalysis {
    override fun analyze(program: AstNode.ProgramNode): List<SemanticError> {
        val errors = mutableListOf<TypeError>()

        val functionTypes =
            program.topLevelFunctions.associate { (returnType, parameters, name) -> name.name to Type.FunctionType(returnType.type, parameters.map { it.type.type }) }
        val structTypes = program.structDeclarations.associate { (name, fields) -> name.name to Type.StructType(name.name, fields.associate { it.name.name to it.type.type }) }

        val visitor = TypeCheckingVisitor(functionTypes, structTypes)
        program.accept(visitor, Unit)
        errors += visitor.errors

        return errors.map { SemanticError.TypeErrorWrapper(it) }
    }
}

private class TypeCheckingVisitor(private val functionTypes: Map<SymbolName, Type.FunctionType>, private val structTypes: Map<SymbolName, Type.StructType>) : VisitorWithParents() {

    val errors: MutableList<TypeError> = mutableListOf()
    private val typeCache: MutableMap<AstNode.ExpressionNode, Type> = mutableMapOf()
    private val lValueTypeCache: MutableMap<AstNode.LValueNode, Type> = mutableMapOf()
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
        val lValueType = typeCheck(assignmentNode.lValue)
        val rValueType = typeCheck(assignmentNode.expression)

        if (lValueType.isLargeType()) {
            errors += TypeError.LargeTypeAssignment(assignmentNode, lValueType)
            return
        }

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
            if (leftType.isLargeType() || rightType.isLargeType()) {
                val largeType = if (leftType.isLargeType()) leftType else rightType
                errors += TypeError.LargeTypeComparison(binaryOperationNode, largeType)
                typeCache[binaryOperationNode] = Type.BoolType
                return
            }

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
            Token.OperatorType.DEREFERENCE_OR_MUL -> error("Unary dereference operator are handled in pointer dereference node")
        }

        compareTypes(expectedType, expressionType, unaryOperationNode.expression)

        typeCache[unaryOperationNode] = expectedType
    }

    override fun visit(arrayAccessNode: AstNode.ArrayAccessNode, parents: List<AstNode>) {
        val expressionType = typeCheck(arrayAccessNode.expression)
        compareTypes(Type.Kind.ARRAY, expressionType, arrayAccessNode.expression)

        val indexType = typeCheck(arrayAccessNode.index)
        compareTypes(Type.IntType, indexType, arrayAccessNode.index)

        typeCache[arrayAccessNode] = (expressionType as Type.ArrayType).elementType
    }

    override fun visit(pointerDereferenceNode: AstNode.PointerDereferenceNode, parents: List<AstNode>) {
        val expressionType = typeCheck(pointerDereferenceNode.expression)
        compareTypes(Type.Kind.POINTER, expressionType, pointerDereferenceNode.expression)

        typeCache[pointerDereferenceNode] = (expressionType as? Type.PointerType)?.elementType ?: Type.NullType
    }

    override fun visit(fieldAccessNode: AstNode.FieldAccessNode, parents: List<AstNode>) {
        val expressionType = typeCheck(fieldAccessNode.expression)
        if (!compareTypes(Type.Kind.STRUCT_REFERENCE, expressionType, fieldAccessNode.expression)) {
            typeCache[fieldAccessNode] = expressionType
            return
        }

        val structType = structTypes[(expressionType as Type.StructReferenceType).structName]!!
        val fieldType = structType.fields[fieldAccessNode.fieldName.name] ?: error("Field '${fieldAccessNode.fieldName.name}' not found in struct '${structType.name}'")

        typeCache[fieldAccessNode] = fieldType
    }

    override fun visit(fieldDereferenceNode: AstNode.FieldDereferenceNode, parents: List<AstNode>) {
        val expressionType = typeCheck(fieldDereferenceNode.expression)
        if (!compareTypes(Type.Kind.POINTER, expressionType, fieldDereferenceNode.expression)) {
            typeCache[fieldDereferenceNode] = expressionType
            return
        }

        if (expressionType == Type.NullType) {
            errors += TypeError.NullDereference(fieldDereferenceNode.expression)
            typeCache[fieldDereferenceNode] = Type.NullType
            return
        }

        val dereferencedType = (expressionType as Type.PointerType).elementType

        if (!compareTypes(Type.Kind.STRUCT_REFERENCE, dereferencedType, fieldDereferenceNode.expression)) {
            typeCache[fieldDereferenceNode] = dereferencedType
            return
        }

        val structType = structTypes[(dereferencedType as Type.StructReferenceType).structName]!!
        val fieldType = structType.fields[fieldDereferenceNode.fieldName.name]!!
        typeCache[fieldDereferenceNode] = fieldType
    }

    override fun visit(lValuePointerDereferenceNode: AstNode.LValuePointerDereferenceNode, parents: List<AstNode>) {
        val lValueType = typeCheck(lValuePointerDereferenceNode.lValue)
        if (!compareTypes(Type.Kind.POINTER, lValueType, lValuePointerDereferenceNode.lValue)) {
            lValueTypeCache[lValuePointerDereferenceNode] = lValueType
            return
        }

        lValueTypeCache[lValuePointerDereferenceNode] = (lValueType as Type.PointerType).elementType
    }

    override fun visit(lValueFieldAccessNode: AstNode.LValueFieldAccessNode, parents: List<AstNode>) {
        val lValueType = typeCheck(lValueFieldAccessNode.lValue)
        if (!compareTypes(Type.Kind.STRUCT_REFERENCE, lValueType, lValueFieldAccessNode.lValue)) {
            lValueTypeCache[lValueFieldAccessNode] = lValueType
            return
        }

        val structType = structTypes[(lValueType as Type.StructReferenceType).structName]!!
        val fieldType = structType.fields[lValueFieldAccessNode.fieldName.name]!!

        lValueTypeCache[lValueFieldAccessNode] = fieldType
    }

    override fun visit(lValueFieldDereferenceNode: AstNode.LValueFieldDereferenceNode, parents: List<AstNode>) {
        val lValueType = typeCheck(lValueFieldDereferenceNode.lValue)
        if (!compareTypes(Type.Kind.POINTER, lValueType, lValueFieldDereferenceNode.lValue)) {
            lValueTypeCache[lValueFieldDereferenceNode] = lValueType
            return
        }

        val dereferencedType = (lValueType as Type.PointerType).elementType

        if (!compareTypes(Type.Kind.STRUCT_REFERENCE, dereferencedType, lValueFieldDereferenceNode.lValue)) {
            lValueTypeCache[lValueFieldDereferenceNode] = dereferencedType
            return
        }

        val structType = structTypes[(dereferencedType as Type.StructReferenceType).structName]!!
        val fieldType = structType.fields[lValueFieldDereferenceNode.fieldName.name]!!

        lValueTypeCache[lValueFieldDereferenceNode] = fieldType
    }

    override fun visit(lValueArrayAccessNode: AstNode.LValueArrayAccessNode, parents: List<AstNode>) {
        val lValueType = typeCheck(lValueArrayAccessNode.lValue)
        compareTypes(Type.Kind.ARRAY, lValueType, lValueArrayAccessNode.lValue)

        val indexType = typeCheck(lValueArrayAccessNode.index)
        compareTypes(Type.IntType, indexType, lValueArrayAccessNode.index)

        lValueTypeCache[lValueArrayAccessNode] = (lValueType as Type.ArrayType).elementType
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
        if (functionNode.returnType.type.isLargeType()) {
            errors += TypeError.LargeTypeReturn(functionNode, functionNode.returnType.type)
            return
        }
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
            is AstNode.CallAllocNode -> Type.PointerType(node.type.type)
            is AstNode.CallAllocArrayNode -> Type.ArrayType(node.type.type)
            is AstNode.NullLiteralNode -> Type.NullType

            else -> {
                node.accept(this, Unit)
                typeCache[node] ?: error("Type checking failed")
            }
        }
        return type
    }

    private fun typeCheck(node: AstNode.LValueNode): Type {
        return when (node) {
            is AstNode.LValueIdentifierNode -> symbolTable[node.name] ?: error("Variable status analysis failed")
            else -> {
                node.accept(this, Unit)
                lValueTypeCache[node] ?: error("Type checking failed for l-value node")
            }
        }
    }

    private fun typeCheck(node: AstNode.StatementNode) {
        node.accept(this, Unit)
    }

    private fun compareTypes(expected: Type.Kind, actual: Type, node: AstNode): Boolean {
        if (expected != actual.kind) {
            errors += TypeError.TypeMismatchSingleNodeKind(node, actual, expected)
            return false
        }
        return true
    }

    private fun compareTypes(expected: Type, actual: Type, node: AstNode): Boolean {
        if (expected is Type.PointerType && actual is Type.NullType) {
            // Allow null type for pointer types
            return true
        }
        if (expected != actual) {
            errors += TypeError.TypeMismatchSingleNode(node, actual, expected)
            return false
        }
        return true
    }

    private fun compareTypes(node1: AstNode, type1: Type, node2: AstNode, type2: Type) {
        if (type1 is Type.PointerType && type2 is Type.NullType) {
            // Allow null type for pointer types
            return
        }
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
