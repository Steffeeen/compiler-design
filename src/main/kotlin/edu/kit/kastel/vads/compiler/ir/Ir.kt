package edu.kit.kastel.vads.compiler.ir

import edu.kit.kastel.vads.compiler.parser.SymbolName
import edu.kit.kastel.vads.compiler.util.NamespaceStack

enum class SideEffectType {
    DIVISION_BY_ZERO_EXCEPTION
}

data class IrProgram(val graphs: List<IrGraph>)

data class IrGraph(val endNode: IrNode.EndNode, val name: String)

sealed interface IrNode {

    sealed interface DataNode : IrNode {
        val dataInputs: List<DataNode>
    }

    sealed interface SideEffectNode : IrNode

    sealed interface SideEffectRelevantNode : SideEffectNode {
        val sideEffect: SideEffectNode
    }

    sealed interface ControlNode : IrNode

    sealed interface ControlRelevantNode : ControlNode {
        val control: ControlNode
    }

    open class ScopeNode : IrNode {
        protected val symbolTable: NamespaceStack<DataNode>

        constructor() {
            this.symbolTable = NamespaceStack<DataNode>()
        }

        protected constructor(symbolTable: NamespaceStack<DataNode>) {
            this.symbolTable = symbolTable
        }

        open fun duplicate(): ScopeNode {
            return ScopeNode(this.symbolTable.duplicate())
        }

        fun merge(other: ScopeNode): Map<SymbolName, Pair<DataNode, DataNode>> {
            return symbolTable.merge(other.symbolTable)
        }

        open operator fun set(name: SymbolName, value: DataNode) {
            symbolTable[name] = value
        }

        open operator fun get(name: SymbolName): DataNode? = symbolTable[name]

        fun pushNamespace() = symbolTable.pushNamespace()
        fun popNamespace() = symbolTable.popNamespace()

        fun getAll() = symbolTable.getAll()
    }

    object StartNode : SideEffectNode, ControlNode

    data class EndNode(val returnNodes: List<ReturnNode>, override val sideEffect: SideEffectNode, override val control: ControlNode) : SideEffectRelevantNode, ControlRelevantNode

    class SideEffectProjectionNode(val type: SideEffectType, override val sideEffect: SideEffectNode) : SideEffectRelevantNode

    // Binary operations
    sealed interface BinaryOperationNode : DataNode {
        val left: DataNode
        val right: DataNode

        override val dataInputs: List<DataNode> get() = listOf(left, right)
    }

    class AddNode(override val left: DataNode, override val right: DataNode) : BinaryOperationNode
    class SubNode(override val left: DataNode, override val right: DataNode) : BinaryOperationNode
    class MulNode(override val left: DataNode, override val right: DataNode) : BinaryOperationNode
    class DivNode(override val left: DataNode, override val right: DataNode, override val sideEffect: SideEffectNode) : BinaryOperationNode, SideEffectRelevantNode
    class ModNode(override val left: DataNode, override val right: DataNode, override val sideEffect: SideEffectNode) : BinaryOperationNode, SideEffectRelevantNode
    class LeftShiftNode(override val left: DataNode, override val right: DataNode) : BinaryOperationNode
    class RightShiftNode(override val left: DataNode, override val right: DataNode) : BinaryOperationNode
    class LessThanNode(override val left: DataNode, override val right: DataNode) : BinaryOperationNode
    class LessThanOrEqualNode(override val left: DataNode, override val right: DataNode) : BinaryOperationNode
    class GreaterThanNode(override val left: DataNode, override val right: DataNode) : BinaryOperationNode
    class GreaterThanOrEqualNode(override val left: DataNode, override val right: DataNode) : BinaryOperationNode
    class EqualNode(override val left: DataNode, override val right: DataNode) : BinaryOperationNode
    class NotEqualNode(override val left: DataNode, override val right: DataNode) : BinaryOperationNode
    class BitwiseAndNode(override val left: DataNode, override val right: DataNode) : BinaryOperationNode
    class BitwiseXorNode(override val left: DataNode, override val right: DataNode) : BinaryOperationNode
    class BitwiseOrNode(override val left: DataNode, override val right: DataNode) : BinaryOperationNode

    // Unary operations
    sealed interface UnaryOperationNode : DataNode {
        val inNode: DataNode

        override val dataInputs: List<DataNode> get() = listOf(inNode)
    }

    class NegateNode(override val inNode: DataNode) : UnaryOperationNode
    class LogicalNotNode(override val inNode: DataNode) : UnaryOperationNode
    class BitwiseNotNode(override val inNode: DataNode) : UnaryOperationNode

    // Constants
    sealed interface ConstantNode : DataNode {
        override val dataInputs: List<DataNode> get() = listOf()
    }

    data class IntegerConstantNode(val value: UInt) : ConstantNode

    data class BooleanConstantNode(val value: Boolean) : ConstantNode

    // Control flow
    class ReturnNode(val result: DataNode, override val sideEffect: SideEffectNode, override val control: ControlNode) : DataNode, SideEffectRelevantNode, ControlRelevantNode {
        override val dataInputs: List<DataNode> = listOf(result)
    }

    class IfNode(val condition: DataNode, override val control: ControlNode) : DataNode, ControlRelevantNode {
        override val dataInputs: List<DataNode> = listOf(condition)
    }

    enum class IfProjectionType { TRUE_BRANCH, FALSE_BRANCH }
    data class IfProjectionNode(override val control: IfNode, val type: IfProjectionType) : ControlRelevantNode

    open class RegionNode(val first: ControlNode, open var second: ControlNode?) : ControlRelevantNode {
        override val control: ControlNode = first
    }

    class LoopRegionNode(val entryPoint: ControlNode, var backEdge: ControlNode?) : RegionNode(entryPoint, backEdge) {
        override val control: ControlNode = entryPoint
        override var second: ControlNode? = null
            get() = backEdge
    }

    class PhiNode(val name: SymbolName, val first: DataNode, var second: DataNode?, val region: RegionNode) : DataNode {
        override val dataInputs: List<DataNode> get() = listOfNotNull(first, second)
    }

    class SideEffectPhiNode(val first: SideEffectNode, val second: SideEffectNode, override val control: RegionNode) : SideEffectRelevantNode, ControlRelevantNode {
        override val sideEffect: SideEffectNode = first // or second, depending on the context
    }
}
