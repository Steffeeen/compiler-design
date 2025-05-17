package edu.kit.kastel.vads.compiler.ir.util

import edu.kit.kastel.vads.compiler.ir.IrGraph
import edu.kit.kastel.vads.compiler.ir.IrNode

private const val INDENT_WIDTH = 4
private val INDENT = " ".repeat(INDENT_WIDTH)

private enum class EdgeType {
    NORMAL,
    SIDE_EFFECT
}

private fun EdgeType.color(): String = when (this) {
    EdgeType.NORMAL -> "black"
    EdgeType.SIDE_EFFECT -> "blue"
}

private data class GraphBuilder(
    val nodeBuilder: StringBuilder = StringBuilder(),
    val edgeBuilder: StringBuilder = StringBuilder(),
    val printedNodesToNumber: MutableMap<IrNode, Int> = mutableMapOf(),
    val createdEdges: MutableSet<Pair<Int, Int>> = mutableSetOf()
) {
    fun ensureNodeExists(node: IrNode): Int {
        printedNodesToNumber[node]?.let { return it }

        val id = printedNodesToNumber.size
        printedNodesToNumber[node] = id
        nodeBuilder.appendIndented("$id [label=\"${node.displayName()}\", color=\"${node.color()}\"];")
        return id
    }

    fun addEdge(fromId: Int, toId: Int, type: EdgeType = EdgeType.NORMAL) {
        if (fromId == toId) return
        if (createdEdges.contains(fromId to toId)) return
        createdEdges.add(fromId to toId)
        edgeBuilder.appendIndented("$fromId -> $toId [color=\"${type.color()}\"];")
    }
}

fun IrGraph.toDotVisualization(): String = printIrGraphToDot(this)

fun printIrGraphToDot(graph: IrGraph, name: String = "main"): String = buildString {
    prefix(name)
    print(graph)
    suffix()
}

private fun StringBuilder.prefix(name: String) {
    appendLine("digraph \"$name\" {")
    appendIndented("layout=dot;")
    appendIndented("node [shape=box];")
    appendIndented("overlap=false;")
}

private fun StringBuilder.suffix() {
    appendLine("}")
}

private fun StringBuilder.print(graph: IrGraph) {
    val graphBuilder = GraphBuilder()
    with(graphBuilder) {
        printNode(graph.returnNode)
    }
    appendLine()
    append(graphBuilder.nodeBuilder)
    appendLine()
    append(graphBuilder.edgeBuilder)
}

private fun GraphBuilder.printNode(node: IrNode): Int {
    val nodeId = ensureNodeExists(node)

    when (node) {
        is IrNode.DivNode, is IrNode.ModNode -> {
            addEdge(printNode(node.left), nodeId)
            addEdge(printNode(node.right), nodeId)
            addEdge(printNode(node.sideEffect), nodeId, EdgeType.SIDE_EFFECT)
        }

        is IrNode.BinaryOperationNode -> {
            addEdge(printNode(node.left), nodeId)
            addEdge(printNode(node.right), nodeId)
        }

        is IrNode.NegateNode -> addEdge(printNode(node.inNode), nodeId)
        is IrNode.ReturnNode -> {
            addEdge(printNode(node.result), nodeId)
            addEdge(printNode(node.sideEffect), nodeId, EdgeType.SIDE_EFFECT)
        }

        is IrNode.SideEffectProjectionNode -> addEdge(printNode(node.inNode), nodeId)
        is IrNode.IntegerConstantNode -> ensureNodeExists(node)
        IrNode.StartNode -> ensureNodeExists(node)
        IrNode.NoOpNode -> ensureNodeExists(node)
    }

    return nodeId
}

private fun IrNode.displayName(): String {
    val baseName = this.javaClass.simpleName.replace("Ir", "")

    if (this is IrNode.IntegerConstantNode) {
        return "$baseName [${this.value}]"
    }

    return baseName
}

private fun IrNode.color(): String {
    return when (this) {
        IrNode.StartNode -> "red"
        is IrNode.ReturnNode -> "blue"
        else -> "black"
    }
}

private fun StringBuilder.appendIndented(line: String) {
    append(INDENT)
    append(line)
    append("\n")
}
