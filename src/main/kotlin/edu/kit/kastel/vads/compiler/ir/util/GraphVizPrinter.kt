package edu.kit.kastel.vads.compiler.ir.util

import edu.kit.kastel.vads.compiler.ir.IrGraph
import edu.kit.kastel.vads.compiler.ir.IrNode

private const val INDENT_WIDTH = 4
private val INDENT = " ".repeat(INDENT_WIDTH)

private enum class EdgeType {
    DATA,
    SIDE_EFFECT,
    CONTROL,
    PHI_CONTROL_INFO
}

private fun EdgeType.color(): String = when (this) {
    EdgeType.DATA -> "black"
    EdgeType.SIDE_EFFECT -> "blue"
    EdgeType.CONTROL -> "red"
    EdgeType.PHI_CONTROL_INFO -> "green"
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

    fun addEdge(fromId: Int, toId: Int, type: EdgeType = EdgeType.DATA) {
        if (fromId == toId) return
        if (createdEdges.contains(fromId to toId)) return
        createdEdges.add(fromId to toId)
        edgeBuilder.appendIndented("$fromId -> $toId [color=\"${type.color()}\"];")
    }

    fun addNamedEdge(fromId: Int, toId: Int, name: String, type: EdgeType = EdgeType.DATA) {
        if (fromId == toId) return
        if (createdEdges.contains(fromId to toId)) return
        createdEdges.add(fromId to toId)
        edgeBuilder.appendIndented("$fromId -> $toId [label=\"$name\", color=\"${type.color()}\"];")
    }
}

fun IrGraph.toDotVisualization(): String = printIrGraphToDot(this)

fun printIrGraphToDot(graph: IrGraph): String = buildString {
    prefix(graph.name)
    print(graph)
    suffix()
}

private fun StringBuilder.prefix(name: String) {
    appendLine("digraph \"$name\" {")
    appendIndented("label=\"$name\";")
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
        val nodeId = ensureNodeExists(graph.endNode)
        graph.endNode.returnNodes.forEach {
            val returnNodeId = printNode(it)
            addEdge(returnNodeId, nodeId, EdgeType.CONTROL)
            addEdge(returnNodeId, nodeId, EdgeType.SIDE_EFFECT)
        }
    }
    appendLine()
    append(graphBuilder.nodeBuilder)
    appendLine()
    append(graphBuilder.edgeBuilder)
}

private fun GraphBuilder.printNode(node: IrNode): Int {
    if (node in printedNodesToNumber) {
        return printedNodesToNumber[node]!!
    }
    val nodeId = ensureNodeExists(node)

    if (node is IrNode.RegionNode) {
        addEdge(printNode(node.first), nodeId, EdgeType.CONTROL)
        node.second?.let { addEdge(printNode(it), nodeId, EdgeType.CONTROL) }
        return nodeId
    }

    if (node is IrNode.PhiNode) {
        addEdge(nodeId, printNode(node.region), EdgeType.CONTROL)
        addNamedEdge(printNode(node.first), nodeId, "first")
        addNamedEdge(printNode(node.second!!), nodeId, "second")
        addNamedEdge(printNode(node.firstControl), nodeId, "firstControl", EdgeType.PHI_CONTROL_INFO)
        addNamedEdge(printNode(node.secondControl!!), nodeId, "secondControl", EdgeType.PHI_CONTROL_INFO)
        node.dataInputs.forEach { addEdge(printNode(it), nodeId) }
        return nodeId
    }

    if (node is IrNode.SideEffectPhiNode) {
        addNamedEdge(printNode(node.first), nodeId, "first", EdgeType.SIDE_EFFECT)
        addNamedEdge(printNode(node.second), nodeId, "second", EdgeType.SIDE_EFFECT)
        node.firstControl?.let { addNamedEdge(printNode(it), nodeId, "firstControl", EdgeType.PHI_CONTROL_INFO) }
        node.secondControl?.let { addNamedEdge(printNode(it), nodeId, "secondControl", EdgeType.PHI_CONTROL_INFO) }
        return nodeId
    }

    if (node is IrNode.DataNode) {
        node.dataInputs.forEach { addEdge(printNode(it), nodeId) }
    }
    if (node is IrNode.DataNodeConsumingNode) {
        node.dataInputs.forEach { addEdge(printNode(it), nodeId) }
    }
    if (node is IrNode.SideEffectRelevantNode) {
        addEdge(printNode(node.sideEffect), nodeId, EdgeType.SIDE_EFFECT)
    }
    if (node is IrNode.ControlRelevantNode) {
        addEdge(printNode(node.control), nodeId, EdgeType.CONTROL)
    }

    return nodeId
}

private fun IrNode.displayName(): String {
    val baseName = this.javaClass.simpleName.replace("Ir", "")

    return when (this) {
        is IrNode.IntegerConstantNode -> "$baseName [${this.value}]"
        is IrNode.BooleanConstantNode -> "$baseName [${this.value}]"
        is IrNode.IfProjectionNode -> "$baseName [${this.type}]"
        is IrNode.PhiNode -> "$baseName [${this.name.asString()}]"
        is IrNode.NormalCallNode -> "$baseName [${this.name.asString()}]"
        is IrNode.ParameterNode -> "$baseName [${this.name.asString()}]"
        is IrNode.AllocateStructNode -> "$baseName [${this.type.asString()}]"
        is IrNode.FieldAccessLoadNode -> "$baseName [${this.fieldName.asString()}]"
        is IrNode.FieldAccessStoreNode -> "$baseName [${this.fieldName.asString()}]"
        is IrNode.FieldDereferenceLoadNode -> "$baseName [${this.fieldName.asString()}]"
        is IrNode.FieldDereferenceStoreNode -> "$baseName [${this.fieldName.asString()}]"
        else -> baseName
    }
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
