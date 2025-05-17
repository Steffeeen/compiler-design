package edu.kit.kastel.vads.compiler.semantic

import edu.kit.kastel.vads.compiler.parser.AstNode
import edu.kit.kastel.vads.compiler.parser.SymbolName

class Namespace<T> {
    private val content: MutableMap<SymbolName, T> = mutableMapOf()

    fun put(name: AstNode.NameNode, value: T, merger: (T, T) -> T) {
        content.merge(name.name, value!!, merger)
    }

    fun get(name: AstNode.NameNode): T? {
        return content[name.name]
    }
}
