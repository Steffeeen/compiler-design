package edu.kit.kastel.vads.compiler.parser

import edu.kit.kastel.vads.compiler.Position
import edu.kit.kastel.vads.compiler.Span
import edu.kit.kastel.vads.compiler.lexer.Token
import edu.kit.kastel.vads.compiler.parser.visitor.Visitor

sealed interface AstNode {
    val span: Span

    fun <T, R> accept(visitor: Visitor<T, R?>, data: T): R?

    sealed interface ExpressionNode : AstNode

    sealed interface StatementNode : AstNode

    data class TypeNode(val type: Type, override val span: Span) : AstNode {
        override fun <T, R> accept(visitor: Visitor<T, R?>, data: T): R? = visitor.visit(this, data)
    }

    data class AssignmentNode(
        val lValue: LValueNode,
        val operator: Token.Operator,
        val expression: ExpressionNode
    ) : StatementNode {

        override val span get() = lValue.span.merge(expression.span)
        override fun <T, R> accept(visitor: Visitor<T, R?>, data: T): R? = visitor.visit(this, data)
    }

    data class BinaryOperationNode(
        val lhs: ExpressionNode,
        val rhs: ExpressionNode,
        val operatorType: Token.OperatorType
    ) :
        ExpressionNode {

        override val span get() = lhs.span.merge(rhs.span)
        override fun <T, R> accept(visitor: Visitor<T, R?>, data: T): R? = visitor.visit(this, data)
    }

    class BlockNode(val statements: List<StatementNode>, override val span: Span) : StatementNode {
        override fun <T, R> accept(visitor: Visitor<T, R?>, data: T): R? = visitor.visit(this, data)
    }

    data class DeclarationNode(val type: TypeNode, val name: NameNode, val initializer: ExpressionNode?) :
        StatementNode {

        override val span get() = type.span.merge((initializer ?: name).span)
        override fun <T, R> accept(visitor: Visitor<T, R?>, data: T): R? = visitor.visit(this, data)
    }

    data class FunctionNode(val returnType: TypeNode, val name: NameNode, val body: BlockNode) : AstNode {
        override val span get() = Span.SimpleSpan(returnType.span.start, body.span.end)

        override fun <T, R> accept(visitor: Visitor<T, R?>, data: T): R? = visitor.visit(this, data)
    }

    data class IdentifierExpressionNode(val name: NameNode) : ExpressionNode {
        override val span get() = name.span

        override fun <T, R> accept(visitor: Visitor<T, R?>, data: T): R? = visitor.visit(this, data)
    }

    sealed class LiteralNode(val type: Type) : ExpressionNode {
        override fun <T, R> accept(visitor: Visitor<T, R?>, data: T): R? = visitor.visit(this, data)
    }

    data class IntLiteralNode(val value: String, val base: Int, override val span: Span) : LiteralNode(Type.IntType) {
        fun parseValue(): Long? {
            return when (base) {
                10 -> parseDecimal(value.length)
                16 -> parseHexadecimal(value.length)
                else -> error("Unsupported base $base")
            }
        }

        private fun parseDecimal(end: Int): Long? {
            val l: Long
            try {
                l = value.substring(0, end).toLong(10)
            } catch (_: NumberFormatException) {
                return null
            }

            if (l < 0 || l > Integer.toUnsignedLong(Integer.MIN_VALUE)) {
                return null
            }

            return l
        }

        private fun parseHexadecimal(end: Int): Long? {
            return try {
                value.substring(2, end).toUInt(16).toLong()
            } catch (_: NumberFormatException) {
                null
            }
        }
    }

    data class BooleanLiteralNode(val value: Boolean, override val span: Span) : LiteralNode(Type.BoolType)

    sealed interface LValueNode : AstNode

    data class LValueIdentifierNode(val name: NameNode) : LValueNode {
        override val span get() = name.span

        override fun <T, R> accept(visitor: Visitor<T, R?>, data: T): R? = visitor.visit(this, data)
    }

    data class UnaryOperationNode(val expression: ExpressionNode, val operator: Token.Operator) : ExpressionNode {
        override val span get() = operator.span.merge(expression.span)

        override fun <T, R> accept(visitor: Visitor<T, R?>, data: T): R? = visitor.visit(this, data)
    }

    data class NameNode(val name: SymbolName, override val span: Span) : AstNode {
        override fun <T, R> accept(visitor: Visitor<T, R?>, data: T): R? = visitor.visit(this, data)
    }

    data class ProgramNode(val topLevelFunctions: List<FunctionNode>) : AstNode {
        override val span get() = span()

        private fun span(): Span {
            val first = topLevelFunctions.first()
            val last = topLevelFunctions.last()
            return Span.SimpleSpan(first.span.start, last.span.end)
        }

        override fun <T, R> accept(visitor: Visitor<T, R?>, data: T): R? = visitor.visit(this, data)
    }

    data class ReturnNode(val expression: ExpressionNode, val start: Position) : StatementNode {
        override val span get() = Span.SimpleSpan(start, expression.span.end)

        override fun <T, R> accept(visitor: Visitor<T, R?>, data: T): R? = visitor.visit(this, data)
    }

    data class IfNode(val condition: ExpressionNode, val body: StatementNode, val elseStatement: StatementNode?, override val span: Span) : StatementNode {
        override fun <T, R> accept(visitor: Visitor<T, R?>, data: T): R? = visitor.visit(this, data)
    }

    data class WhileNode(val condition: ExpressionNode, val body: StatementNode, override val span: Span) : StatementNode {
        override fun <T, R> accept(visitor: Visitor<T, R?>, data: T): R? = visitor.visit(this, data)
    }

    data class ForNode(
        val initializer: StatementNode?,
        val condition: ExpressionNode,
        val increment: StatementNode?,
        val body: StatementNode,
        override val span: Span
    ) : StatementNode {
        override fun <T, R> accept(visitor: Visitor<T, R?>, data: T): R? = visitor.visit(this, data)
    }

    data class BreakNode(override val span: Span) : StatementNode {
        override fun <T, R> accept(visitor: Visitor<T, R?>, data: T): R? = visitor.visit(this, data)
    }

    data class ContinueNode(override val span: Span) : StatementNode {
        override fun <T, R> accept(visitor: Visitor<T, R?>, data: T): R? = visitor.visit(this, data)
    }

    data class TernaryOperationNode(
        val condition: ExpressionNode,
        val trueExpression: ExpressionNode,
        val falseExpression: ExpressionNode
    ) : ExpressionNode {
        override val span get() = condition.span.merge(falseExpression.span)

        override fun <T, R> accept(visitor: Visitor<T, R?>, data: T): R? = visitor.visit(this, data)
    }
}