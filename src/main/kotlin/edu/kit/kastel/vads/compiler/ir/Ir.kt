package edu.kit.kastel.vads.compiler.ir

enum class SideEffectType {
    DIVISION_BY_ZERO_EXCEPTION
}

data class IrGraph(val returnNode: IrNode.ReturnNode, val name: String)

sealed interface IrNode {
    sealed interface SideEffectNode : IrNode

    sealed interface SideEffectEmittingNode : SideEffectNode {
        val sideEffect: SideEffectNode
    }

    sealed interface BinaryOperationNode : IrNode {
        val left: IrNode
        val right: IrNode
    }

    object NoOpNode : IrNode
    object StartNode : IrNode, SideEffectNode
    class SideEffectProjectionNode(val type: SideEffectType, val inNode: SideEffectNode) : IrNode, SideEffectNode
    class ReturnNode(val result: IrNode, val sideEffect: SideEffectNode) : IrNode
    class IntegerConstantNode(val value: Long) : IrNode
    class AddNode(override val left: IrNode, override val right: IrNode) : BinaryOperationNode
    class SubNode(override val left: IrNode, override val right: IrNode) : BinaryOperationNode
    class MulNode(override val left: IrNode, override val right: IrNode) : BinaryOperationNode
    class DivNode(override val left: IrNode, override val right: IrNode, override val sideEffect: SideEffectNode) : BinaryOperationNode, SideEffectEmittingNode
    class ModNode(override val left: IrNode, override val right: IrNode, override val sideEffect: SideEffectNode) : BinaryOperationNode, SideEffectEmittingNode
    class NegateNode(val inNode: IrNode) : IrNode
}
