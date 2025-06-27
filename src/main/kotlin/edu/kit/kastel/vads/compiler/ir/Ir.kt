package edu.kit.kastel.vads.compiler.ir

import edu.kit.kastel.vads.compiler.parser.SymbolName

enum class SideEffectType {
    DIVISION_BY_ZERO_EXCEPTION
}

data class IrProgram(val graphs: List<IrGraph>)

data class IrGraph(val endNode: IrNode.EndNode, val parameters: List<IrNode.ParameterNode>, val name: String)

sealed interface IrNode {

    sealed interface DataNode : IrNode {
        val dataInputs: List<DataNode>
    }

    sealed interface DataNodeConsumingNode : IrNode {
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

    object StartNode : SideEffectNode, ControlNode

    data class EndNode(val returnNodes: List<ReturnNode>) : IrNode

    class SideEffectProjectionNode(val type: SideEffectType, override val sideEffect: SideEffectNode) : SideEffectRelevantNode

    data class ParameterNode(val name: SymbolName) : DataNode {
        override val dataInputs: List<DataNode> = listOf()
    }

    sealed interface CallNode : DataNode, SideEffectRelevantNode, ControlRelevantNode

    class NormalCallNode(val name: SymbolName, val arguments: List<DataNode>, override val sideEffect: SideEffectNode, override val control: ControlNode) : CallNode {
        override val dataInputs: List<DataNode> = arguments
    }

    sealed interface BuiltinCallNode : CallNode

    class PrintNode(val parameter: DataNode, override val sideEffect: SideEffectNode, override val control: ControlNode) : BuiltinCallNode {
        override val dataInputs: List<DataNode> = listOf(parameter)
    }

    class ReadNode(override val sideEffect: SideEffectNode, override val control: ControlNode) : BuiltinCallNode {
        override val dataInputs: List<DataNode> = listOf()
    }

    class FlushNode(override val sideEffect: SideEffectNode, override val control: ControlNode) : BuiltinCallNode {
        override val dataInputs: List<DataNode> = listOf()
    }

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
    sealed interface ConstantNode<T> : DataNode {
        val value: T
        override val dataInputs: List<DataNode> get() = listOf()
    }

    data class IntegerConstantNode(override val value: UInt) : ConstantNode<UInt>

    data class BooleanConstantNode(override val value: Boolean) : ConstantNode<Boolean>

    // Control flow
    class ReturnNode(val result: DataNode, override val sideEffect: SideEffectNode, override val control: ControlNode) : DataNodeConsumingNode, SideEffectRelevantNode,
        ControlRelevantNode {
        override val dataInputs: List<DataNode> = listOf(result)
    }

    class IfNode(val condition: DataNode, override val control: ControlNode) : DataNodeConsumingNode, ControlRelevantNode {
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

    class PhiNode(val name: SymbolName, val first: DataNode, val firstControl: ControlNode, var second: DataNode?, var secondControl: ControlNode?, val region: RegionNode) :
        DataNode {
        override val dataInputs: List<DataNode> get() = listOfNotNull(first, second)
    }

    class SideEffectPhiNode(val first: SideEffectNode, val second: SideEffectNode, override val control: RegionNode) : SideEffectRelevantNode, ControlRelevantNode {
        override val sideEffect: SideEffectNode = first // or second, depending on the context
    }
}
