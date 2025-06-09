package edu.kit.kastel.vads.compiler.parser

import edu.kit.kastel.vads.compiler.lexer.Token

interface SymbolName {
    fun asString(): String

    private data class Identifier(val identifier: String) : SymbolName {
        override fun asString(): String = identifier
    }

    data class InternalVariable(val name: String) : SymbolName {
        override fun asString(): String = "internal:$name"
    }

    companion object {
        fun forIdentifier(identifier: Token.Identifier): SymbolName {
            return Identifier(identifier.value)
        }
    }
}