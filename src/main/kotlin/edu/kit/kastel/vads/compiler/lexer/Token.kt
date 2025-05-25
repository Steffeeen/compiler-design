@file:Suppress("ClassName")

package edu.kit.kastel.vads.compiler.lexer

import edu.kit.kastel.vads.compiler.Span
import edu.kit.kastel.vads.compiler.lexer.Token.Associativity.LEFT
import edu.kit.kastel.vads.compiler.lexer.Token.Associativity.RIGHT

sealed interface Token {
    val span: Span

    fun asString(): String

    data class NumberLiteral(val value: String, val base: Int, override val span: Span) : Token {
        override fun asString(): String = value
    }

    data class Identifier(val value: String, override val span: Span) : Token {
        override fun asString(): String = value
    }

    data class Error(val value: String, override val span: Span) : Token {
        override fun asString(): String = value
    }

    sealed interface TokenType {
        val value: String
    }

    interface TokenWithType<T : TokenType> : Token {
        val type: T

        override fun asString(): String = type.value
    }

    sealed interface KeywordType : TokenType {
        sealed class KeywordTypeImpl(override val value: String) : KeywordType

        object STRUCT : KeywordTypeImpl("struct")
        object IF : KeywordTypeImpl("if")
        object ELSE : KeywordTypeImpl("else")
        object WHILE : KeywordTypeImpl("while")
        object FOR : KeywordTypeImpl("for")
        object CONTINUE : KeywordTypeImpl("continue")
        object BREAK : KeywordTypeImpl("break")
        object RETURN : KeywordTypeImpl("return")
        object ASSERT : KeywordTypeImpl("assert")
        object TRUE : KeywordTypeImpl("true")
        object FALSE : KeywordTypeImpl("false")
        object NULL : KeywordTypeImpl("NULL")
        object PRINT : KeywordTypeImpl("print")
        object READ : KeywordTypeImpl("read")
        object ALLOC : KeywordTypeImpl("alloc")
        object ALLOC_ARRAY : KeywordTypeImpl("alloc_array")
        object INT : KeywordTypeImpl("int")
        object BOOL : KeywordTypeImpl("bool")
        object VOID : KeywordTypeImpl("void")
        object CHAR : KeywordTypeImpl("char")
        object STRING : KeywordTypeImpl("string")

        companion object {
            val entries by lazy {
                KeywordTypeImpl::class.sealedSubclasses.mapNotNull { it.objectInstance }
            }
        }
    }

    data class Keyword(override val type: KeywordType, override val span: Span) : TokenWithType<KeywordType>

    sealed interface SeparatorType : TokenType {
        sealed class SeparatorTypeImpl(override val value: String) : SeparatorType

        object PAREN_OPEN : SeparatorTypeImpl("(")
        object PAREN_CLOSE : SeparatorTypeImpl(")")
        object BRACE_OPEN : SeparatorTypeImpl("{")
        object BRACE_CLOSE : SeparatorTypeImpl("}")
        object COLON : SeparatorTypeImpl(":")
        object SEMICOLON : SeparatorTypeImpl(";")

        companion object {
            val entries by lazy {
                SeparatorTypeImpl::class.sealedSubclasses.mapNotNull { it.objectInstance }
            }
        }
    }

    data class Separator(override val type: SeparatorType, override val span: Span) : TokenWithType<SeparatorType>

    enum class Associativity {
        LEFT, RIGHT
    }

    sealed interface OperatorType : TokenType {
        val precedence: Int
        val associativity: Associativity
        val canBeUnary: Boolean
        val canBeBinary: Boolean

        sealed class OperatorTypeImpl(
            override val value: String,
            override val precedence: Int,
            override val associativity: Associativity,
            override val canBeUnary: Boolean = false,
            override val canBeBinary: Boolean = true,
        ) : OperatorType

        object LOGICAL_NOT : OperatorTypeImpl("!", 13, RIGHT, canBeUnary = true, canBeBinary = false)
        object BITWISE_NOT : OperatorTypeImpl("~", 13, RIGHT, canBeUnary = true, canBeBinary = false)
        object MUL : OperatorTypeImpl("*", 12, LEFT)
        object DIV : OperatorTypeImpl("/", 12, LEFT)
        object MOD : OperatorTypeImpl("%", 12, LEFT)
        object ADD : OperatorTypeImpl("+", 11, LEFT)
        object SUB : OperatorTypeImpl("-", 11, LEFT, canBeUnary = true, canBeBinary = true)
        object LEFT_SHIFT : OperatorTypeImpl("<<", 10, LEFT)
        object RIGHT_SHIFT : OperatorTypeImpl(">>", 10, LEFT)
        object LESS_THAN : OperatorTypeImpl("<", 9, LEFT)
        object LESS_EQUAL : OperatorTypeImpl("<=", 9, LEFT)
        object GREATER_THAN : OperatorTypeImpl(">", 9, LEFT)
        object GREATER_EQUAL : OperatorTypeImpl(">=", 9, LEFT)
        object EQUAL : OperatorTypeImpl("==", 8, LEFT)
        object NOT_EQUAL : OperatorTypeImpl("!=", 8, LEFT)
        object BITWISE_AND : OperatorTypeImpl("&", 7, LEFT)
        object BITWISE_XOR : OperatorTypeImpl("^", 6, LEFT)
        object BITWISE_OR : OperatorTypeImpl("|", 5, LEFT)
        object LOGICAL_AND : OperatorTypeImpl("&&", 4, LEFT)
        object LOGICAL_OR : OperatorTypeImpl("||", 3, LEFT)
        object TERNARY : OperatorTypeImpl("?", 2, RIGHT, canBeBinary = false)
        object ASSIGN : OperatorTypeImpl("=", 1, RIGHT, canBeBinary = false)
        object ASSIGN_ADD : OperatorTypeImpl("+=", 1, RIGHT, canBeBinary = false)
        object ASSIGN_SUB : OperatorTypeImpl("-=", 1, RIGHT, canBeBinary = false)
        object ASSIGN_MUL : OperatorTypeImpl("*=", 1, RIGHT, canBeBinary = false)
        object ASSIGN_DIV : OperatorTypeImpl("/=", 1, RIGHT, canBeBinary = false)
        object ASSIGN_MOD : OperatorTypeImpl("%=", 1, RIGHT, canBeBinary = false)
        object ASSIGN_BITWISE_AND : OperatorTypeImpl("&=", 1, RIGHT, canBeBinary = false)
        object ASSIGN_BITWISE_XOR : OperatorTypeImpl("^=", 1, RIGHT, canBeBinary = false)
        object ASSIGN_BITWISE_OR : OperatorTypeImpl("|=", 1, RIGHT, canBeBinary = false)
        object ASSIGN_LEFT_SHIFT : OperatorTypeImpl("<<=", 1, RIGHT, canBeBinary = false)
        object ASSIGN_RIGHT_SHIFT : OperatorTypeImpl(">>=", 1, RIGHT, canBeBinary = false)

        companion object {
            val entries by lazy {
                OperatorTypeImpl::class.sealedSubclasses.mapNotNull { it.objectInstance }
            }
        }
    }

    data class Operator(override val type: OperatorType, override val span: Span) : TokenWithType<OperatorType>

}
