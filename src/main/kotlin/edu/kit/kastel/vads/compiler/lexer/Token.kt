@file:Suppress("ClassName")

package edu.kit.kastel.vads.compiler.lexer

import edu.kit.kastel.vads.compiler.Span
import edu.kit.kastel.vads.compiler.lexer.Token.Associativity.LEFT
import edu.kit.kastel.vads.compiler.lexer.Token.Associativity.RIGHT
import kotlin.reflect.KClass

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

        sealed interface TypeKeywordType : KeywordType
        sealed interface BuiltinFunctionType : KeywordType

        object STRUCT : KeywordTypeImpl("struct"), TypeKeywordType
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
        object PRINT : KeywordTypeImpl("print"), BuiltinFunctionType
        object READ : KeywordTypeImpl("read"), BuiltinFunctionType
        object ALLOC : KeywordTypeImpl("alloc"), BuiltinFunctionType
        object ALLOC_ARRAY : KeywordTypeImpl("alloc_array"), BuiltinFunctionType
        object INT : KeywordTypeImpl("int"), TypeKeywordType
        object BOOL : KeywordTypeImpl("bool"), TypeKeywordType
        object VOID : KeywordTypeImpl("void")
        object CHAR : KeywordTypeImpl("char")
        object STRING : KeywordTypeImpl("string")
        object FLUSH : KeywordTypeImpl("flush"), BuiltinFunctionType

        companion object {
            val entries by lazy {
                KeywordType::class.getAllSealedSubclasses().mapNotNull { it.objectInstance }
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
        object COMMA : SeparatorTypeImpl(",")
        object BRACKET_OPEN : SeparatorTypeImpl("[")
        object BRACKET_CLOSE : SeparatorTypeImpl("]")

        companion object {
            val entries by lazy {
                SeparatorType::class.getAllSealedSubclasses().mapNotNull { it.objectInstance }
            }
        }
    }

    data class Separator(override val type: SeparatorType, override val span: Span) : TokenWithType<SeparatorType>

    enum class Associativity {
        LEFT, RIGHT
    }

    sealed interface OperatorType : TokenType {
        sealed interface UnaryOperatorType : OperatorType {
            val unaryPrecedence: Int
            val unaryAssociativity: Associativity
        }

        sealed class UnaryOperatorTypeImpl(
            override val value: String,
            override val unaryPrecedence: Int,
            override val unaryAssociativity: Associativity,
        ) : UnaryOperatorType

        sealed interface BinaryOperatorType : OperatorType {
            val binaryPrecedence: Int
            val binaryAssociativity: Associativity
        }

        sealed class BinaryOperatorTypeImpl(
            override val value: String,
            override val binaryPrecedence: Int,
            override val binaryAssociativity: Associativity,
        ) : BinaryOperatorType

        sealed interface AssignOperatorType : BinaryOperatorType

        sealed class AssignOperatorTypeImpl(
            override val value: String,
            override val binaryPrecedence: Int,
            override val binaryAssociativity: Associativity,
        ) : AssignOperatorType

        sealed interface TernaryOperatorType : OperatorType {
            val ternaryPrecedence: Int
            val ternaryAssociativity: Associativity
        }

        sealed class TernaryOperatorTypeImpl(
            override val value: String,
            override val ternaryPrecedence: Int,
            override val ternaryAssociativity: Associativity,
        ) : TernaryOperatorType

        object DEREFERENCE_OR_MUL : UnaryOperatorType, BinaryOperatorType {
            override val value = "*"
            override val unaryPrecedence = 15
            override val unaryAssociativity = RIGHT
            override val binaryPrecedence = 12
            override val binaryAssociativity = LEFT
        }
        object LOGICAL_NOT : UnaryOperatorTypeImpl("!", 13, RIGHT)
        object BITWISE_NOT : UnaryOperatorTypeImpl("~", 13, RIGHT)
        object DIV : BinaryOperatorTypeImpl("/", 12, LEFT)
        object MOD : BinaryOperatorTypeImpl("%", 12, LEFT)
        object ADD : BinaryOperatorTypeImpl("+", 11, LEFT)
        object SUB_OR_NEGATE : UnaryOperatorType, BinaryOperatorType {
            override val value = "-"
            override val unaryPrecedence = 13
            override val unaryAssociativity = RIGHT
            override val binaryPrecedence = 11
            override val binaryAssociativity = LEFT
        }

        object FIELD_DEREFERENCE : BinaryOperatorTypeImpl("->", 14, LEFT)
        object FIELD_ACCESS : BinaryOperatorTypeImpl(".", 14, LEFT)
        object LEFT_SHIFT : BinaryOperatorTypeImpl("<<", 10, LEFT)
        object RIGHT_SHIFT : BinaryOperatorTypeImpl(">>", 10, LEFT)
        object LESS_THAN : BinaryOperatorTypeImpl("<", 9, LEFT)
        object LESS_EQUAL : BinaryOperatorTypeImpl("<=", 9, LEFT)
        object GREATER_THAN : BinaryOperatorTypeImpl(">", 9, LEFT)
        object GREATER_EQUAL : BinaryOperatorTypeImpl(">=", 9, LEFT)
        object EQUAL : BinaryOperatorTypeImpl("==", 8, LEFT)
        object NOT_EQUAL : BinaryOperatorTypeImpl("!=", 8, LEFT)
        object BITWISE_AND : BinaryOperatorTypeImpl("&", 7, LEFT)
        object BITWISE_XOR : BinaryOperatorTypeImpl("^", 6, LEFT)
        object BITWISE_OR : BinaryOperatorTypeImpl("|", 5, LEFT)
        object LOGICAL_AND : BinaryOperatorTypeImpl("&&", 4, LEFT)
        object LOGICAL_OR : BinaryOperatorTypeImpl("||", 3, LEFT)
        object TERNARY : TernaryOperatorTypeImpl("?", 2, RIGHT)
        object ASSIGN : AssignOperatorTypeImpl("=", 1, RIGHT)
        object ASSIGN_ADD : AssignOperatorTypeImpl("+=", 1, RIGHT)
        object ASSIGN_SUB : AssignOperatorTypeImpl("-=", 1, RIGHT)
        object ASSIGN_MUL : AssignOperatorTypeImpl("*=", 1, RIGHT)
        object ASSIGN_DIV : AssignOperatorTypeImpl("/=", 1, RIGHT)
        object ASSIGN_MOD : AssignOperatorTypeImpl("%=", 1, RIGHT)
        object ASSIGN_BITWISE_AND : AssignOperatorTypeImpl("&=", 1, RIGHT)
        object ASSIGN_BITWISE_XOR : AssignOperatorTypeImpl("^=", 1, RIGHT)
        object ASSIGN_BITWISE_OR : AssignOperatorTypeImpl("|=", 1, RIGHT)
        object ASSIGN_LEFT_SHIFT : AssignOperatorTypeImpl("<<=", 1, RIGHT)
        object ASSIGN_RIGHT_SHIFT : AssignOperatorTypeImpl(">>=", 1, RIGHT)

        companion object {
            val entries by lazy {
                OperatorType::class.getAllSealedSubclasses().mapNotNull { it.objectInstance }
            }
        }
    }

    data class Operator(override val type: OperatorType, override val span: Span) : TokenWithType<OperatorType>

}

private fun <T : Any> KClass<T>.getAllSealedSubclasses(): Set<KClass<out T>> {
    val result = mutableSetOf<KClass<out T>>()

    fun collectSubclasses(klass: KClass<out T>) {
        klass.sealedSubclasses.forEach { subclass ->
            result.add(subclass)
            collectSubclasses(subclass)
        }
    }

    collectSubclasses(this)
    return result
}
