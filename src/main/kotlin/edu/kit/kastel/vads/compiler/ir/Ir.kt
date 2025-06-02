package edu.kit.kastel.vads.compiler.ir

enum class SideEffectType {
    DIVISION_BY_ZERO_EXCEPTION
}

data class IrGraph(val returnNode: IrNode.ReturnNode, val name: String)

sealed interface IrNode {
    val inputs: List<IrNode>

    sealed interface SideEffectNode : IrNode

    sealed interface SideEffectEmittingNode : SideEffectNode {
        val sideEffect: SideEffectNode
    }

    sealed interface BinaryOperationNode : IrNode {
        val left: IrNode
        val right: IrNode

        override val inputs: List<IrNode> get() = listOf(left, right)
    }

    object NoOpNode : IrNode {
        override val inputs: List<IrNode> get() = listOf()
    }

    object StartNode : IrNode, SideEffectNode {
        override val inputs: List<IrNode> get() = listOf()
    }

    class SideEffectProjectionNode(val type: SideEffectType, override val sideEffect: SideEffectNode) : IrNode, SideEffectEmittingNode {
        override val inputs: List<IrNode> get() = listOf()
    }

    data class IntegerConstantNode(val value: UInt) : IrNode {
        override val inputs: List<IrNode> get() = listOf()
    }

    class ReturnNode(val result: IrNode, val sideEffect: SideEffectNode) : IrNode {
        override val inputs: List<IrNode> get() = listOf(result)
    }

    class AddNode(override val left: IrNode, override val right: IrNode) : BinaryOperationNode

    class SubNode(override val left: IrNode, override val right: IrNode) : BinaryOperationNode

    class MulNode(override val left: IrNode, override val right: IrNode) : BinaryOperationNode

    class DivNode(override val left: IrNode, override val right: IrNode, override val sideEffect: SideEffectNode) : BinaryOperationNode, SideEffectEmittingNode

    class ModNode(override val left: IrNode, override val right: IrNode, override val sideEffect: SideEffectNode) : BinaryOperationNode, SideEffectEmittingNode

    class NegateNode(val inNode: IrNode) : IrNode {
        override val inputs: List<IrNode> get() = listOf(inNode)
    }
}
