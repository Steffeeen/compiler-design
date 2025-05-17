package edu.kit.kastel.vads.compiler.parser

import edu.kit.kastel.vads.compiler.Position
import edu.kit.kastel.vads.compiler.Span
import edu.kit.kastel.vads.compiler.lexer.Token
import edu.kit.kastel.vads.compiler.parser.visitor.Visitor

sealed interface AstNode {
    val span: Span

    fun <T, R> accept(visitor: Visitor<T?, R?>, data: T?): R?

    sealed interface ExpressionNode : AstNode

    sealed interface StatementNode : AstNode

    data class TypeNode(val type: Type, override val span: Span) : AstNode {
        override fun <T, R> accept(visitor: Visitor<T?, R?>, data: T?): R? {
            return visitor.visit(this, data)
        }
    }

    data class AssignmentNode(
        val lValue: LValueNode,
        val operator: Token.Operator,
        val expression: ExpressionNode
    ) : StatementNode {

        override val span get() = lValue.span.merge(expression.span)
        override fun <T, R> accept(visitor: Visitor<T?, R?>, data: T?): R? {
            return visitor.visit(this, data)
        }
    }

    data class BinaryOperationNode(
        val lhs: ExpressionNode,
        val rhs: ExpressionNode,
        val operatorType: Token.OperatorType
    ) :
        ExpressionNode {

        override val span get() = lhs.span.merge(rhs.span)
        override fun <T, R> accept(visitor: Visitor<T?, R?>, data: T?): R? {
            return visitor.visit(this, data)
        }
    }

    class BlockNode(val statements: List<StatementNode>, override val span: Span) : StatementNode {
        override fun <T, R> accept(visitor: Visitor<T?, R?>, data: T?): R? {
            return visitor.visit(this, data)
        }
    }

    data class DeclarationNode(val type: TypeNode, val name: NameNode, val initializer: ExpressionNode?) :
        StatementNode {

        override val span get() = type.span.merge((initializer ?: name).span)
        override fun <T, R> accept(visitor: Visitor<T?, R?>, data: T?): R? {
            return visitor.visit(this, data)
        }
    }

    data class FunctionNode(val returnType: TypeNode, val name: NameNode, val body: BlockNode) : AstNode {
        override val span get() = Span.SimpleSpan(returnType.span.start, body.span.end)

        override fun <T, R> accept(visitor: Visitor<T?, R?>, data: T?): R? {
            return visitor.visit(this, data)
        }
    }

    data class IdentifierExpressionNode(val name: NameNode) : ExpressionNode {
        override val span get() = name.span

        override fun <T, R> accept(visitor: Visitor<T?, R?>, data: T?): R? {
            return visitor.visit(this, data)
        }
    }

    data class LiteralNode(val value: String, val base: Int, override val span: Span) : ExpressionNode {
        override fun <T, R> accept(visitor: Visitor<T?, R?>, data: T?): R? {
            return visitor.visit(this, data)
        }

        fun parseValue(): Long? {
            var begin = 0
            val end: Int = value.length
            if (base == 16) {
                begin = 2 // ignore 0x
            }
            val l: Long
            try {
                l = value.substring(begin, end).toLong(base)
            } catch (_: NumberFormatException) {
                return null
            }

            return l.takeIf { it > 0 && it <= Integer.MAX_VALUE }
        }
    }

    sealed interface LValueNode : AstNode

    data class LValueIdentifierNode(val name: NameNode) : LValueNode {
        override val span get() = name.span

        override fun <T, R> accept(visitor: Visitor<T?, R?>, data: T?): R? {
            return visitor.visit(this, data)
        }
    }

    data class NegateNode(val expression: ExpressionNode, val minusPos: Span) : ExpressionNode {
        override val span get() = minusPos.merge(expression.span)

        override fun <T, R> accept(visitor: Visitor<T?, R?>, data: T?): R? {
            return visitor.visit(this, data)
        }
    }

    data class NameNode(val name: SymbolName, override val span: Span) : AstNode {
        override fun <T, R> accept(visitor: Visitor<T?, R?>, data: T?): R? {
            return visitor.visit(this, data)
        }
    }

    data class ProgramNode(val topLevelFunctions: List<FunctionNode>) : AstNode {
        override val span get() = span()

        private fun span(): Span {
            val first = topLevelFunctions.first()
            val last = topLevelFunctions.last()
            return Span.SimpleSpan(first.span.start, last.span.end)
        }

        override fun <T, R> accept(visitor: Visitor<T?, R?>, data: T?): R? {
            return visitor.visit(this, data)
        }
    }

    data class ReturnNode(val expression: ExpressionNode, val start: Position) : StatementNode {
        override val span get() = Span.SimpleSpan(start, expression.span.end)

        override fun <T, R> accept(visitor: Visitor<T?, R?>, data: T?): R? {
            return visitor.visit(this, data)
        }
    }
}