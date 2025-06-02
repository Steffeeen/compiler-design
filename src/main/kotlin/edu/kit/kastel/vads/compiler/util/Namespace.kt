package edu.kit.kastel.vads.compiler.util

import edu.kit.kastel.vads.compiler.parser.AstNode
import edu.kit.kastel.vads.compiler.parser.SymbolName

class Namespace<T> {
    private val content: MutableMap<SymbolName, T> = mutableMapOf()

    operator fun set(name: AstNode.NameNode, value: T) {
        content[name.name] = value
    }

    operator fun set(name: SymbolName, value: T) {
        content[name] = value
    }

    operator fun get(name: AstNode.NameNode): T? {
        return content[name.name]
    }

    operator fun get(name: SymbolName): T? {
        return content[name]
    }

    fun getAll(): Map<SymbolName, T> {
        return content
    }

    operator fun contains(name: AstNode.NameNode): Boolean {
        return content.containsKey(name.name)
    }

    operator fun contains(name: SymbolName): Boolean {
        return content.containsKey(name)
    }
}