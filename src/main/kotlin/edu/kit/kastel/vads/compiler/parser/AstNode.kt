package edu.kit.kastel.vads.compiler.parser

import edu.kit.kastel.vads.compiler.Position
import edu.kit.kastel.vads.compiler.Span
import edu.kit.kastel.vads.compiler.lexer.Token
import edu.kit.kastel.vads.compiler.parser.visitor.Visitor
import edu.kit.kastel.vads.compiler.typechecker.Type
import kotlin.math.pow

sealed interface AstNode {
    val span: Span
    val children: List<AstNode>

    fun <T, R> accept(visitor: Visitor<T, R>, data: T): R

    sealed interface ExpressionNode : AstNode

    sealed interface StatementNode : AstNode

    sealed interface SimpleNode : StatementNode

    sealed interface ControlFlowEndNode : StatementNode

    data class TypeNode(val type: Type, override val span: Span) : AstNode {
        override fun <T, R> accept(visitor: Visitor<T, R>, data: T): R = visitor.visit(this, data)
        override val children: List<AstNode> = listOf()
    }

    data class NullLiteralNode(override val span: Span) : ExpressionNode {
        override val children: List<AstNode> = listOf()
        override fun <T, R> accept(visitor: Visitor<T, R>, data: T): R = visitor.visit(this, data)
    }

    data class AssignmentNode(val lValue: LValueNode, val operator: Token.OperatorType.AssignOperatorType, val expression: ExpressionNode) : SimpleNode {
        override val span get() = lValue.span.merge(expression.span)
        override val children: List<AstNode> = listOf(lValue, expression)
        override fun <T, R> accept(visitor: Visitor<T, R>, data: T): R = visitor.visit(this, data)
    }

    data class BinaryOperationNode(val left: ExpressionNode, val right: ExpressionNode, val operatorType: Token.OperatorType.BinaryOperatorType) : ExpressionNode {
        override val span get() = left.span.merge(right.span)
        override val children: List<AstNode> = listOf(left, right)
        override fun <T, R> accept(visitor: Visitor<T, R>, data: T): R = visitor.visit(this, data)
    }

    data class PointerDereferenceNode(val expression: ExpressionNode, override val span: Span) : ExpressionNode {
        override val children: List<AstNode> = listOf(expression)
        override fun <T, R> accept(visitor: Visitor<T, R>, data: T): R = visitor.visit(this, data)
    }

    data class FieldAccessNode(val expression: ExpressionNode, val fieldName: NameNode) : ExpressionNode {
        override val span: Span get() = expression.span.merge(fieldName.span)
        override val children: List<AstNode> = listOf(expression, fieldName)
        override fun <T, R> accept(visitor: Visitor<T, R>, data: T): R = visitor.visit(this, data)
    }

    data class FieldDereferenceNode(val expression: ExpressionNode, val fieldName: NameNode) : ExpressionNode {
        override val span: Span get() = expression.span.merge(fieldName.span)
        override val children: List<AstNode> = listOf(expression, fieldName)
        override fun <T, R> accept(visitor: Visitor<T, R>, data: T): R = visitor.visit(this, data)
    }

    data class ArrayAccessNode(val expression: ExpressionNode, val index: ExpressionNode, override val span: Span) : ExpressionNode {
        override val children: List<AstNode> = listOf(expression, index)
        override fun <T, R> accept(visitor: Visitor<T, R>, data: T): R = visitor.visit(this, data)
    }

    class BlockNode(val statements: List<StatementNode>, override val span: Span) : StatementNode {
        override val children: List<AstNode> = statements
        override fun <T, R> accept(visitor: Visitor<T, R>, data: T): R = visitor.visit(this, data)
    }

    data class DeclarationNode(val type: TypeNode, val name: NameNode, val initializer: ExpressionNode?) : SimpleNode {
        override val span get() = type.span.merge((initializer ?: name).span)
        override val children: List<AstNode> = listOfNotNull(type, name, initializer)
        override fun <T, R> accept(visitor: Visitor<T, R>, data: T): R = visitor.visit(this, data)
    }

    data class ParameterNode(val type: TypeNode, val name: NameNode) : AstNode {
        override val span get() = type.span.merge(name.span)
        override val children: List<AstNode> = listOf(type, name)
        override fun <T, R> accept(visitor: Visitor<T, R>, data: T): R = visitor.visit(this, data)
    }

    data class FunctionNode(val returnType: TypeNode, val parameters: List<ParameterNode>, val name: NameNode, val body: BlockNode) : AstNode {
        override val span get() = Span.SimpleSpan(returnType.span.start, body.span.end)
        override val children: List<AstNode> = listOf(returnType, name, body)
        override fun <T, R> accept(visitor: Visitor<T, R>, data: T): R = visitor.visit(this, data)
    }

    data class IdentifierExpressionNode(val name: NameNode) : ExpressionNode {
        override val span get() = name.span
        override val children: List<AstNode> = listOf(name)
        override fun <T, R> accept(visitor: Visitor<T, R>, data: T): R = visitor.visit(this, data)
    }

    sealed class LiteralNode(val type: Type) : ExpressionNode {
        override val children: List<AstNode> = listOf()
    }

    data class IntLiteralNode(val value: String, val base: Int, override val span: Span) : LiteralNode(Type.IntType) {
        fun parseValue(): UInt? {
            return try {
                when (base) {
                    10 -> value.toUInt(10).takeIf { it <= 2.0.pow(31).toUInt() }
                    16 -> value.substring(2).toUInt(16)
                    else -> error("Unsupported base $base")
                }
            } catch (_: NumberFormatException) {
                null
            }
        }

        override fun <T, R> accept(visitor: Visitor<T, R>, data: T): R = visitor.visit(this, data)
    }

    data class BooleanLiteralNode(val value: Boolean, override val span: Span) : LiteralNode(Type.BoolType) {
        override fun <T, R> accept(visitor: Visitor<T, R>, data: T): R = visitor.visit(this, data)
    }

    sealed interface LValueNode : AstNode

    data class LValueIdentifierNode(val name: NameNode) : LValueNode {
        override val span get() = name.span
        override val children = listOf(name)
        override fun <T, R> accept(visitor: Visitor<T, R>, data: T): R = visitor.visit(this, data)
    }

    data class LValuePointerDereferenceNode(val lValue: LValueNode, override val span: Span) : LValueNode {
        override val children: List<AstNode> = listOf(lValue)
        override fun <T, R> accept(visitor: Visitor<T, R>, data: T): R = visitor.visit(this, data)
    }

    data class LValueFieldAccessNode(val lValue: LValueNode, val fieldName: NameNode) : LValueNode {
        override val span: Span get() = lValue.span.merge(fieldName.span)
        override val children: List<AstNode> = listOf(lValue, fieldName)
        override fun <T, R> accept(visitor: Visitor<T, R>, data: T): R = visitor.visit(this, data)
    }

    data class LValueFieldDereferenceNode(val lValue: LValueNode, val fieldName: NameNode) : LValueNode {
        override val span: Span get() = lValue.span.merge(fieldName.span)
        override val children: List<AstNode> = listOf(lValue, fieldName)
        override fun <T, R> accept(visitor: Visitor<T, R>, data: T): R = visitor.visit(this, data)
    }

    data class LValueArrayAccessNode(val lValue: LValueNode, val index: ExpressionNode, override val span: Span) : LValueNode {
        override val children: List<AstNode> = listOf(lValue, index)
        override fun <T, R> accept(visitor: Visitor<T, R>, data: T): R = visitor.visit(this, data)
    }

    data class UnaryOperationNode(val expression: ExpressionNode, val operator: Token.OperatorType.UnaryOperatorType, override val span: Span) : ExpressionNode {
        override val children: List<AstNode> = listOf(expression)
        override fun <T, R> accept(visitor: Visitor<T, R>, data: T): R = visitor.visit(this, data)
    }

    data class NameNode(val name: SymbolName, override val span: Span) : AstNode {
        override val children: List<AstNode> = listOf()
        override fun <T, R> accept(visitor: Visitor<T, R>, data: T): R = visitor.visit(this, data)
    }

    data class ProgramNode(val topLevelFunctions: List<FunctionNode>, val structDeclarations: List<StructDeclarationNode>) : AstNode {
        override val span get() = span()
        override val children: List<AstNode> = topLevelFunctions
        private fun span(): Span {
            val first = topLevelFunctions.first()
            val last = topLevelFunctions.last()
            return Span.SimpleSpan(first.span.start, last.span.end)
        }

        override fun <T, R> accept(visitor: Visitor<T, R>, data: T): R = visitor.visit(this, data)
    }

    data class StructDeclarationNode(val name: NameNode, val fields: List<StructFieldDeclarationNode>, override val span: Span) : AstNode {
        override val children: List<AstNode> = listOf(name) + fields
        override fun <T, R> accept(visitor: Visitor<T, R>, data: T): R = visitor.visit(this, data)
    }

    data class StructFieldDeclarationNode(val type: TypeNode, val name: NameNode) : AstNode {
        override val span: Span get() = type.span.merge(name.span)
        override val children: List<AstNode> = listOf(type, name)
        override fun <T, R> accept(visitor: Visitor<T, R>, data: T): R = visitor.visit(this, data)
    }

    data class ReturnNode(val expression: ExpressionNode, val start: Position) : ControlFlowEndNode {
        override val span get() = Span.SimpleSpan(start, expression.span.end)
        override val children: List<AstNode> = listOf(expression)
        override fun <T, R> accept(visitor: Visitor<T, R>, data: T): R = visitor.visit(this, data)
    }

    data class IfNode(val condition: ExpressionNode, val body: StatementNode, val elseStatement: StatementNode?, override val span: Span) : StatementNode {
        override val children: List<AstNode> = listOfNotNull(condition, body, elseStatement)
        override fun <T, R> accept(visitor: Visitor<T, R>, data: T): R = visitor.visit(this, data)
    }

    data class WhileNode(val condition: ExpressionNode, val body: StatementNode, override val span: Span) : StatementNode {
        override val children: List<AstNode> = listOf(condition, body)
        override fun <T, R> accept(visitor: Visitor<T, R>, data: T): R = visitor.visit(this, data)
    }

    data class ForNode(
        val initializer: SimpleNode?,
        val condition: ExpressionNode,
        val increment: SimpleNode?,
        val body: StatementNode,
        override val span: Span
    ) : StatementNode {
        override val children: List<AstNode> = listOfNotNull(initializer, condition, increment, body)
        override fun <T, R> accept(visitor: Visitor<T, R>, data: T): R = visitor.visit(this, data)
    }

    data class BreakNode(override val span: Span) : ControlFlowEndNode {
        override val children: List<AstNode> = listOf()
        override fun <T, R> accept(visitor: Visitor<T, R>, data: T): R = visitor.visit(this, data)
    }

    data class ContinueNode(override val span: Span) : ControlFlowEndNode {
        override val children: List<AstNode> = listOf()
        override fun <T, R> accept(visitor: Visitor<T, R>, data: T): R = visitor.visit(this, data)
    }

    data class TernaryOperationNode(
        val condition: ExpressionNode,
        val trueExpression: ExpressionNode,
        val falseExpression: ExpressionNode
    ) : ExpressionNode {
        override val span get() = condition.span.merge(falseExpression.span)
        override val children: List<AstNode> = listOf(condition, trueExpression, falseExpression)
        override fun <T, R> accept(visitor: Visitor<T, R>, data: T): R = visitor.visit(this, data)
    }

    sealed interface CallNode : ExpressionNode, SimpleNode {
        val arguments: List<ExpressionNode>
    }

    data class CallNormalNode(val name: NameNode, override val arguments: List<ExpressionNode>, override val span: Span) : CallNode {
        override val children: List<AstNode> = arguments
        override fun <T, R> accept(visitor: Visitor<T, R>, data: T): R = visitor.visit(this, data)
    }

    data class CallBuiltinNode(val keyword: Token.KeywordType.BuiltinFunctionType, override val arguments: List<ExpressionNode>, override val span: Span) : CallNode {
        override val children: List<AstNode> = arguments
        override fun <T, R> accept(visitor: Visitor<T, R>, data: T): R = visitor.visit(this, data)
    }

    data class CallAllocNode(val type: TypeNode, override val span: Span) : CallNode {
        override val arguments: List<ExpressionNode> = listOf()
        override val children: List<AstNode> = listOf(type)
        override fun <T, R> accept(visitor: Visitor<T, R>, data: T): R = visitor.visit(this, data)
    }

    data class CallAllocArrayNode(val type: TypeNode, val size: ExpressionNode, override val span: Span) : CallNode {
        override val arguments: List<ExpressionNode> = listOf(size)
        override val children: List<AstNode> = listOf(type, size)
        override fun <T, R> accept(visitor: Visitor<T, R>, data: T): R = visitor.visit(this, data)
    }
}
