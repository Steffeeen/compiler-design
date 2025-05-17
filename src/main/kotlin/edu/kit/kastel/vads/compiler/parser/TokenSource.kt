package edu.kit.kastel.vads.compiler.parser

import edu.kit.kastel.vads.compiler.Position
import edu.kit.kastel.vads.compiler.Span
import edu.kit.kastel.vads.compiler.lexer.Token

class TokenSource(private val tokens: List<Token>) {
    private var index = 0

    fun peek(): Token? {
        if (hasNoMoreTokens()) {
            return null
        }

        return tokens[index]
    }

    fun hasNoMoreTokens(): Boolean {
        return index >= tokens.size
    }

    fun consume(): Token? {
        val token = peek()
        this.index++
        return token
    }

    fun createEOFSpan(): Span {
        val position = tokens.lastOrNull()?.span?.end ?: Position.SimplePosition(0, 0)
        return Span.SimpleSpan(position, position)
    }
}
