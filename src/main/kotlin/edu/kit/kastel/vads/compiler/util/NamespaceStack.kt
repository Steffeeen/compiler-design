package edu.kit.kastel.vads.compiler.util

import edu.kit.kastel.vads.compiler.parser.AstNode
import edu.kit.kastel.vads.compiler.parser.SymbolName

class NamespaceStack<T> {
    private val namespaces = mutableListOf<Namespace<T>>()

    fun pushNamespace() = namespaces.addLast(Namespace())
    fun popNamespace() = namespaces.removeLast()

    fun setInTopMost(name: SymbolName, value: T) {
        namespaces.last()[name] = value
    }

    fun setInTopMost(name: AstNode.NameNode, value: T) {
        namespaces.last()[name] = value
    }

    operator fun get(name: SymbolName): T? = namespaces.findLast { name in it }?.get(name)
    operator fun get(name: AstNode.NameNode): T? = namespaces.findLast { name in it }?.get(name)

    fun getAll(): Map<SymbolName, T> {
        return namespaces.flatMap { it.getAll().toList() }.toMap()
    }
}