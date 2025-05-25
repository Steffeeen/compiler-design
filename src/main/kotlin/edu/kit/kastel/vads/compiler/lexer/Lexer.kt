package edu.kit.kastel.vads.compiler.lexer

import edu.kit.kastel.vads.compiler.CompilerOptions
import edu.kit.kastel.vads.compiler.Position.SimplePosition
import edu.kit.kastel.vads.compiler.Span
import edu.kit.kastel.vads.compiler.Span.SimpleSpan
import edu.kit.kastel.vads.compiler.lexer.Token.*

private val OPERATOR_AND_SEPARATOR_LOOKAHEAD = (OperatorType.entries.map { it.value } + SeparatorType.entries.map { it.value }).maxOf { it.length }

class Lexer(private val source: String, private val options: CompilerOptions) {
    private var pos = 0
    private var lineStart = 0
    private var line = 0

    fun lex(): List<Token> {
        val tokens = mutableListOf<Token>()
        while (true) {
            val token = nextToken()
            if (token != null) {
                tokens.add(token)
            } else {
                break
            }
        }

        return tokens
    }

    fun nextToken(): Token? {
        val error = skipWhitespace()
        if (error != null) {
            return error
        }
        if (this.pos >= this.source.length) {
            return null
        }

        val lookahead = peekMultiple(OPERATOR_AND_SEPARATOR_LOOKAHEAD).trim()

        for (separator in SeparatorType.entries.sortedByDescending { it.value.length }) {
            if (lookahead.startsWith(separator.value)) {
                return separator(separator)
            }
        }

        for (operator in OperatorType.entries.sortedByDescending { it.value.length }) {
            if (lookahead.startsWith(operator.value)) {
                return Operator(operator, buildSpan(operator.value.length))
            }
        }

        if (isIdentifierChar(peek())) {
            if (isNumeric(peek())) {
                return lexNumber()
            }
            return lexIdentifierOrKeyword()
        }

        return Error(peek().toString(), buildSpan(1))
    }

    enum class CommentType {
        SINGLE_LINE,
        MULTI_LINE
    }

    private fun skipWhitespace(): Error? {
        var currentCommentType: CommentType? = null
        var multiLineCommentDepth = 0
        var commentStart = -1
        while (hasMore(0)) {
            when (peek()) {
                ' ', '\t' -> this.pos++
                '\n', '\r' -> {
                    this.pos++
                    this.lineStart = this.pos
                    this.line++
                    if (currentCommentType == CommentType.SINGLE_LINE) {
                        currentCommentType = null
                    }
                }

                '/' -> {
                    if (currentCommentType == CommentType.SINGLE_LINE) {
                        this.pos++
                        continue
                    }
                    if (hasMore(1)) {
                        if (peek(1) == '/' && currentCommentType == null) {
                            currentCommentType = CommentType.SINGLE_LINE
                        } else if (peek(1) == '*') {
                            currentCommentType = CommentType.MULTI_LINE
                            multiLineCommentDepth++
                        } else if (currentCommentType == CommentType.MULTI_LINE) {
                            this.pos++
                            continue
                        } else {
                            return null
                        }
                        commentStart = this.pos
                        this.pos += 2
                        continue
                    }
                    // are we in a multi line comment of any depth?
                    if (multiLineCommentDepth > 0) {
                        this.pos++
                        continue
                    }
                    return null
                }

                else -> {
                    if (currentCommentType == CommentType.MULTI_LINE) {
                        if (peek() == '*' && hasMore(1) && peek(1) == '/') {
                            this.pos += 2
                            multiLineCommentDepth--
                            currentCommentType = if (multiLineCommentDepth == 0) null else CommentType.MULTI_LINE
                        } else {
                            this.pos++
                        }
                        continue
                    } else if (currentCommentType == CommentType.SINGLE_LINE) {
                        this.pos++
                        continue
                    }
                    return null
                }
            }
        }
        if (!hasMore(0) && currentCommentType == CommentType.MULTI_LINE) {
            return Error(this.source.substring(commentStart), buildSpan(0))
        }
        return null
    }

    private fun separator(parenOpen: SeparatorType): Separator {
        return Separator(parenOpen, buildSpan(parenOpen.value.length))
    }

    private fun lexIdentifierOrKeyword(): Token {
        var off = 1
        while (hasMore(off) && isIdentifierChar(peek(off))) {
            off++
        }
        val id = this.source.substring(this.pos, this.pos + off)
        // This is a naive solution. Using a better data structure (hashmap, trie) likely performs better.
        for (value in KeywordType.entries) {
            if (value.keyword == id) {
                return Keyword(value, buildSpan(off))
            }
        }
        return Identifier(id, buildSpan(off))
    }

    private fun lexNumber(): Token {
        if (isHexPrefix()) {
            var off = 2
            while (hasMore(off) && isHex(peek(off))) {
                off++
            }
            if (off == 2) {
                // 0x without any further hex digits
                return Error(this.source.substring(this.pos, this.pos + off), buildSpan(2))
            }
            return NumberLiteral(this.source.substring(this.pos, this.pos + off), 16, buildSpan(off))
        }
        var off = 1
        while (hasMore(off) && isNumeric(peek(off))) {
            off++
        }
        if (peek() == '0' && off > 1) {
            // leading zero is not allowed
            return Error(this.source.substring(this.pos, this.pos + off), buildSpan(off))
        }
        return NumberLiteral(this.source.substring(this.pos, this.pos + off), 10, buildSpan(off))
    }

    private fun isHexPrefix(): Boolean {
        return peek() == '0' && hasMore(1) && (peek(1) == 'x' || peek(1) == 'X')
    }

    private fun isIdentifierChar(c: Char): Boolean {
        return c == '_' || c >= 'a' && c <= 'z' || c >= 'A' && c <= 'Z' || c >= '0' && c <= '9'
    }

    private fun isNumeric(c: Char): Boolean {
        return c >= '0' && c <= '9'
    }

    private fun isHex(c: Char): Boolean {
        return isNumeric(c) || (c >= 'a' && c <= 'f') || (c >= 'A' && c <= 'F')
    }

    private fun buildSpan(proceed: Int): Span {
        val start = this.pos
        this.pos += proceed
        val s = SimplePosition(this.line, start - this.lineStart)
        val e = SimplePosition(this.line, start - this.lineStart + proceed)
        return SimpleSpan(s, e)
    }

    private fun peek(): Char = source[pos]

    private fun hasMore(offset: Int): Boolean {
        return pos + offset < source.length
    }

    private fun peek(offset: Int): Char = source[pos + offset]

    private fun peekMultiple(@Suppress("SameParameterValue") length: Int): String {
        return if (hasMore(length)) {
            source.substring(pos, pos + length).takeWhile { !it.isWhitespace() }
        } else {
            source.substring(pos)
        }
    }
}
