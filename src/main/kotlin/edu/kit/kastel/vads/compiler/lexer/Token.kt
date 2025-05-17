package edu.kit.kastel.vads.compiler.lexer

import edu.kit.kastel.vads.compiler.Span

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

    interface TokenWithType<T : Enum<T>> : Token {
        val type: T

        override fun asString(): String = type.toString()
    }

    enum class KeywordType(val keyword: String) {
        STRUCT("struct"),
        IF("if"),
        ELSE("else"),
        WHILE("while"),
        FOR("for"),
        CONTINUE("continue"),
        BREAK("break"),
        RETURN("return"),
        ASSERT("assert"),
        TRUE("true"),
        FALSE("false"),
        NULL("NULL"),
        PRINT("print"),
        READ("read"),
        ALLOC("alloc"),
        ALLOC_ARRAY("alloc_array"),
        INT("int"),
        BOOL("bool"),
        VOID("void"),
        CHAR("char"),
        STRING("string");

        override fun toString(): String {
            return keyword
        }
    }

    data class Keyword(override val type: KeywordType, override val span: Span) : TokenWithType<KeywordType>

    enum class SeparatorType(val value: String) {
        PAREN_OPEN("("),
        PAREN_CLOSE(")"),
        BRACE_OPEN("{"),
        BRACE_CLOSE("}"),
        SEMICOLON(";");

        override fun toString(): String = value
    }

    data class Separator(override val type: SeparatorType, override val span: Span) : TokenWithType<SeparatorType>

    enum class OperatorType(val value: String) {
        ASSIGN_SUB("-="),
        SUB("-"),
        ASSIGN_ADD("+="),
        ADD("+"),
        MUL("*"),
        ASSIGN_MUL("*="),
        ASSIGN_DIV("/="),
        DIV("/"),
        ASSIGN_MOD("%="),
        MOD("%"),
        ASSIGN("=");

        override fun toString(): String = value
    }

    data class Operator(override val type: OperatorType, override val span: Span) : TokenWithType<OperatorType>

}
