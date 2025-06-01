package edu.kit.kastel.vads.compiler.semantic

import edu.kit.kastel.vads.compiler.parser.AstNode
import edu.kit.kastel.vads.compiler.parser.SymbolName

class Namespace<T> {
    private val content: MutableMap<SymbolName, T> = mutableMapOf()

    fun put(name: AstNode.NameNode, value: T, merger: (T, T) -> T) {
        content.merge(name.name, value!!, merger)
    }

    fun put(name: AstNode.NameNode, value: T) {
        content[name.name] = value
    }

    fun put(name: SymbolName, value: T) {
        content[name] = value
    }

    fun get(name: AstNode.NameNode): T? {
        return content[name.name]
    }

    fun get(name: SymbolName): T? {
        return content[name]
    }

    operator fun contains(name: AstNode.NameNode): Boolean {
        return content.containsKey(name.name)
    }

    fun getAll(): Map<SymbolName, T> {
        return content
    }
}
