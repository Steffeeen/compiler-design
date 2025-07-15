package edu.kit.kastel.vads.compiler.ir

import edu.kit.kastel.vads.compiler.parser.SymbolName
import edu.kit.kastel.vads.compiler.typechecker.Type

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

    sealed interface SideEffectEmittingNode : SideEffectRelevantNode {
        val sideEffectType: SideEffectType
    }

    sealed interface ControlNode : IrNode

    sealed interface ControlRelevantNode : ControlNode {
        val control: ControlNode
    }

    object StartNode : SideEffectNode, ControlNode

    data class EndNode(val returnNodes: List<ReturnNode>) : IrNode

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

    class AllocateStructNode(val type: Type.StructType) : DataNode {
        override val dataInputs: List<DataNode> = listOf()
    }

    class StoreNode(val location: DataNode, val value: DataNode, override val sideEffect: SideEffectNode) : DataNodeConsumingNode, SideEffectRelevantNode {
        override val dataInputs: List<DataNode> = listOf(location, value)
    }

    class LoadNode(val location: DataNode, override val sideEffect: SideEffectNode) : DataNode, SideEffectRelevantNode {
        override val dataInputs: List<DataNode> = listOf(location)
    }

    class FieldAccessNode(val struct: DataNode, val fieldName: SymbolName, override val sideEffect: SideEffectNode) : DataNode, SideEffectRelevantNode {
        override val dataInputs: List<DataNode> = listOf(struct)
    }

    class FieldDereferenceNode(val struct: DataNode, val fieldName: SymbolName, override val sideEffect: SideEffectNode) : DataNode, SideEffectRelevantNode {
        override val dataInputs: List<DataNode> = listOf(struct)
    }

    class PointerDereferenceNode(val pointer: DataNode, override val sideEffect: SideEffectNode) : DataNode, SideEffectRelevantNode {
        override val dataInputs: List<DataNode> = listOf(pointer)
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
    class DivNode(override val left: DataNode, override val right: DataNode, override val sideEffect: SideEffectNode) : BinaryOperationNode, SideEffectEmittingNode {
        override val sideEffectType: SideEffectType = SideEffectType.DIVISION_BY_ZERO_EXCEPTION
    }

    class ModNode(override val left: DataNode, override val right: DataNode, override val sideEffect: SideEffectNode) : BinaryOperationNode, SideEffectEmittingNode {
        override val sideEffectType: SideEffectType = SideEffectType.DIVISION_BY_ZERO_EXCEPTION
    }

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

    object NullConstantNode : ConstantNode<Nothing> {
        override val value: Nothing get() = throw UnsupportedOperationException("NullConstantNode does not have a value")
    }

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

    class SideEffectPhiNode(val first: SideEffectNode, val second: SideEffectNode, val firstControl: ControlNode?, val secondControl: ControlNode?) : SideEffectRelevantNode {
        override val sideEffect: SideEffectNode = first // or second, depending on the context
    }
}
