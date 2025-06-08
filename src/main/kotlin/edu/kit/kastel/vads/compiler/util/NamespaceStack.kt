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

    operator fun set(name: SymbolName, value: T) {
        val namespace = namespaces.findLast { name in it } ?: namespaces.last()
        namespace[name] = value
    }

    operator fun set(name: AstNode.NameNode, value: T) = set(name.name, value)

    operator fun get(name: SymbolName): T? = namespaces.findLast { name in it }?.get(name)
    operator fun get(name: AstNode.NameNode): T? = namespaces.findLast { name in it }?.get(name)

    fun getAll(): Map<SymbolName, T> {
        return namespaces.flatMap { it.getAll().toList() }.toMap()
    }

    fun duplicate(): NamespaceStack<T> {
        val newStack = NamespaceStack<T>()
        namespaces.forEach { newStack.namespaces.add(it.duplicate()) }
        return newStack
    }

    fun merge(other: NamespaceStack<T>): Map<SymbolName, Pair<T, T>> {
        require(namespaces.size == other.namespaces.size) { "Cannot merge namespaces with different sizes" }
        val allKeysInThisScope = getAll().keys
        val allKeysInOtherScope = other.getAll().keys
        require(allKeysInThisScope == allKeysInOtherScope) { "Cannot merge namespaces with different keys" }

        val differing = mutableMapOf<SymbolName, Pair<T, T>>()

        val names = allKeysInThisScope

        for (name in names) {
            val thisValue = this[name]!!
            val otherValue = other[name]!!

            if (thisValue != otherValue) {
                differing[name] = Pair(thisValue, otherValue)
            }
        }

        return differing
    }
}