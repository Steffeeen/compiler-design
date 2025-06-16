package edu.kit.kastel.vads.compiler.backend

import edu.kit.kastel.vads.compiler.backend.ir.*
import edu.kit.kastel.vads.compiler.ir.IrGraph
import edu.kit.kastel.vads.compiler.ir.IrNode
import edu.kit.kastel.vads.compiler.ir.IrProgram

fun lowerIrToAsmIr(irProgram: IrProgram): AsmIr.Program {
    val functions = irProgram.graphs.map { Lowerer(it).lower() }
    return AsmIr.Program(functions)
}

private class Lowerer(private val irGraph: IrGraph) {
    private val blocks: MutableMap<AsmLabel, BasicBlockBuilder> = mutableMapOf()
    private var registerCounter = 0
    private val nodesToRegisters: MutableMap<IrNode, AsmRegister> = mutableMapOf()
    private val generatedNodes: MutableSet<IrNode> = mutableSetOf()
    private val controlsToBlock: MutableMap<IrNode.ControlNode, BasicBlockBuilder> = mutableMapOf()
    private val successorInfo = SuccessorInfo(irGraph)
    private val finalReturnBlock = block(AsmLabel("final_return"))

    fun lower(): AsmIr.Function {
        val startSuccessors = IrNode.StartNode.controlSuccessors()

        val startBlock = block(label(IrNode.StartNode))
        controlsToBlock[IrNode.StartNode] = startBlock
        with(startBlock) {
            for (successor in startSuccessors) {
                generateControlNode(successor)
            }
        }

        val returnRegister = register(irGraph.endNode)
        finalReturnBlock.addLast(AsmIr.Return(returnRegister))

        for (returnNode in irGraph.endNode.returnNodes) {
            val block = block(label(returnNode))

            with(block) {
                nodesToRegisters[returnNode.result] = returnRegister
                generateDataNode(returnNode.result, returnRegister)
            }

            block.setFinalJump(finalReturnBlock)
        }

        generateIfNodeConditions()

        return AsmIr.Function(irGraph.name, blocks.values.map { it.build() }, finalReturnBlock.build())
    }

    private fun generateIfNodeConditions() {
        val seen = mutableSetOf<IrNode.ControlNode>()

        fun visitNode(node: IrNode.ControlNode) {
            if (node in seen) return
            seen.add(node)

            when (node) {
                is IrNode.IfNode -> {
                    val destination = nodesToRegisters[node.condition]!!
                    val block = controlsToBlock[node]!!
                    with(block) { generateDataNode(node.condition, destination) }
                    val (trueProjection, falseProjection) = node.projectionNodes()
                    visitNode(trueProjection)
                    visitNode(falseProjection)
                }

                is IrNode.LoopRegionNode -> {
                    visitNode(node.controlSuccessor())
                }

                is IrNode.RegionNode -> visitNode(node.controlSuccessor())
                is IrNode.IfProjectionNode -> visitNode(node.controlSuccessors().first())
                is IrNode.ReturnNode -> {}
                is IrNode.SideEffectPhiNode -> TODO()
                IrNode.StartNode -> node.controlSuccessors().forEach { visitNode(it) }
            }
        }

        visitNode(IrNode.StartNode)
    }

    context(currentBlock: BasicBlockBuilder)
    private fun generateControlNode(controlNode: IrNode.ControlNode) {
        if (controlNode in generatedNodes) {
            if (controlNode is IrNode.RegionNode) {
                // Ensure that the final jump is set for all nodes that are predecessors of this region
                controlsToBlock[controlNode]?.let {
                    controlsToBlock[controlNode.first]?.setFinalJump(it)
                    controlsToBlock[controlNode.second!!]?.setFinalJump(it)
                }
            }
            return
        }

        generatedNodes.add(controlNode)

        when (controlNode) {
            is IrNode.IfNode -> generateIf(controlNode)
            is IrNode.IfProjectionNode -> {} // handled in if node generation
            is IrNode.LoopRegionNode -> generateLoopRegionNode(controlNode)
            is IrNode.RegionNode -> generateRegionNode(controlNode)
            is IrNode.ReturnNode -> generateReturnNode(controlNode)
            is IrNode.SideEffectPhiNode -> TODO()
            IrNode.StartNode -> {} // handled in the main lower function
        }
    }

    context(currentBlock: BasicBlockBuilder)
    private fun generateIf(ifNode: IrNode.IfNode) {
        controlsToBlock[ifNode] = currentBlock
        val (trueProjection, falseProjection) = ifNode.projectionNodes()

        val conditionRegister = register(ifNode.condition)

        val trueBlock = block(label(trueProjection))
        controlsToBlock[trueProjection] = trueBlock

        val falseBlock = block(label(falseProjection))
        controlsToBlock[falseProjection] = falseBlock

        currentBlock.addLast(AsmIr.ConditionalJump(conditionRegister, trueBlock.label))
        currentBlock.setFinalJump(falseBlock)

        with(trueBlock) { generateControlNode(trueProjection.controlSuccessors().first()) }
        with(falseBlock) { generateControlNode(falseProjection.controlSuccessors().first()) }
    }

    context(currentBlock: BasicBlockBuilder)
    private fun generateRegionNode(regionNode: IrNode.RegionNode) {
        val regionBlock = block(label(regionNode))
        controlsToBlock[regionNode] = regionBlock

        currentBlock.setFinalJump(regionBlock)

        with(regionBlock) { generateControlNode(regionNode.controlSuccessor()) }
    }

    context(currentBlock: BasicBlockBuilder)
    private fun generateLoopRegionNode(loopRegionNode: IrNode.LoopRegionNode) {
        val loopBlock = block(label(loopRegionNode))
        controlsToBlock[loopRegionNode] = loopBlock

        currentBlock.setFinalJump(loopBlock)

        with(loopBlock) {
            generateControlNode(loopRegionNode.controlSuccessor())
        }

        val backEdgeBlock = controlsToBlock[loopRegionNode.backEdge!!]!!
        backEdgeBlock.setFinalJump(loopBlock)
    }

    context(currentBlock: BasicBlockBuilder)
    private fun generateReturnNode(returnNode: IrNode.ReturnNode) {
        val returnBlock = block(label(returnNode))
        controlsToBlock[returnNode] = returnBlock
        returnBlock.setFinalJump(finalReturnBlock)

        currentBlock.setFinalJump(returnBlock)
    }

    context(currentBlock: BasicBlockBuilder)
    private fun generateDataNode(node: IrNode.DataNode, destination: AsmRegister) {
        if (node in generatedNodes) {
            return
        }

        when (node) {
            is IrNode.UnaryOperationNode -> generateUnaryOperation(node, destination)
            is IrNode.BinaryOperationNode -> generateBinaryOperation(node, destination)
            is IrNode.ConstantNode<*> -> error("Constants are handled directly in unary and binary operations")
            is IrNode.PhiNode -> generatePhiNode(node, destination)
        }
    }

    context(currentBlock: BasicBlockBuilder)
    private fun generatePhiNode(phiNode: IrNode.PhiNode, destination: AsmRegister) {
        generatedNodes.add(phiNode)

        val block1 = controlsToBlock[phiNode.firstControl]!!
        with(block1) {
            generateDataNode(phiNode.first, destination)
        }

        val block2 = controlsToBlock[phiNode.secondControl!!]!!
        with(block2) {
            generateDataNode(phiNode.second!!, destination)
        }
    }

    context(currentBlock: BasicBlockBuilder)
    private fun generateUnaryOperation(operation: IrNode.UnaryOperationNode, destination: AsmRegister) {
        val source = source(operation.inNode)

        val unaryOperationType = when (operation) {
            is IrNode.NegateNode -> AsmIr.UnaryOperationType.NEGATE
            is IrNode.LogicalNotNode -> AsmIr.UnaryOperationType.LOGICAL_NOT
            is IrNode.BitwiseNotNode -> AsmIr.UnaryOperationType.BITWISE_NOT
        }

        currentBlock.addFirst(AsmIr.UnaryOperation(unaryOperationType, destination, source))

        if (operation.inNode !is IrNode.ConstantNode<*>) {
            generateDataNode(operation.inNode, source as AsmRegister)
        }
    }

    context(currentBlock: BasicBlockBuilder)
    private fun generateBinaryOperation(operation: IrNode.BinaryOperationNode, destination: AsmRegister) {
        val leftSource = source(operation.left)
        val rightSource = source(operation.right)

        val binaryOperationType = when (operation) {
            is IrNode.AddNode -> AsmIr.BinaryOperationType.ADD
            is IrNode.SubNode -> AsmIr.BinaryOperationType.SUBTRACT
            is IrNode.MulNode -> AsmIr.BinaryOperationType.MULTIPLY
            is IrNode.DivNode -> AsmIr.BinaryOperationType.DIVIDE
            is IrNode.ModNode -> AsmIr.BinaryOperationType.MODULO
            is IrNode.LeftShiftNode -> AsmIr.BinaryOperationType.SHIFT_LEFT
            is IrNode.RightShiftNode -> AsmIr.BinaryOperationType.SHIFT_RIGHT
            is IrNode.LessThanNode -> AsmIr.BinaryOperationType.LESS_THAN
            is IrNode.LessThanOrEqualNode -> AsmIr.BinaryOperationType.LESS_THAN_OR_EQUAL
            is IrNode.GreaterThanNode -> AsmIr.BinaryOperationType.GREATER_THAN
            is IrNode.GreaterThanOrEqualNode -> AsmIr.BinaryOperationType.GREATER_THAN_OR_EQUAL
            is IrNode.EqualNode -> AsmIr.BinaryOperationType.EQUAL
            is IrNode.NotEqualNode -> AsmIr.BinaryOperationType.NOT_EQUAL
            is IrNode.BitwiseAndNode -> AsmIr.BinaryOperationType.BITWISE_AND
            is IrNode.BitwiseXorNode -> AsmIr.BinaryOperationType.BITWISE_XOR
            is IrNode.BitwiseOrNode -> AsmIr.BinaryOperationType.BITWISE_OR
        }

        currentBlock.addFirst(AsmIr.BinaryOperation(binaryOperationType, destination, leftSource, rightSource))

        if (operation.left !is IrNode.ConstantNode<*>) {
            generateDataNode(operation.left, leftSource as AsmRegister)
        }

        if (operation.right !is IrNode.ConstantNode<*>) {
            generateDataNode(operation.right, rightSource as AsmRegister)
        }
    }

    private fun block(label: AsmLabel): BasicBlockBuilder {
        return blocks.getOrPut(label) { BasicBlockBuilder(label) }
    }

    private fun label(controlNode: IrNode.ControlNode): AsmLabel {
        val name = when (controlNode) {
            is IrNode.StartNode -> "start"
            is IrNode.ReturnNode -> "return_${controlNode.hashCode()}"
            is IrNode.IfNode -> "if_${controlNode.hashCode()}"
            is IrNode.IfProjectionNode if controlNode.type == IrNode.IfProjectionType.TRUE_BRANCH -> "if_true_${controlNode.hashCode()}"
            is IrNode.IfProjectionNode if controlNode.type == IrNode.IfProjectionType.FALSE_BRANCH -> "if_false_${controlNode.hashCode()}"
            is IrNode.IfProjectionNode -> error("Exhaustiveness check in kotlin not good enough, this should not happen")
            is IrNode.LoopRegionNode -> "loop_region_${controlNode.hashCode()}"
            is IrNode.RegionNode -> "region_${controlNode.hashCode()}"
            is IrNode.SideEffectPhiNode -> "side_effect_phi_${controlNode.hashCode()}"
        }.replace("-", "_")
        return AsmLabel(name)
    }

    private fun source(node: IrNode.DataNode): AsmOperand {
        if (node is IrNode.IntegerConstantNode) {
            return AsmImmediate(node.value)
        }

        if (node is IrNode.BooleanConstantNode) {
            return AsmImmediate(if (node.value) 1u else 0u)
        }

        return register(node)
    }

    private fun register(node: IrNode): AsmRegister {
        if (node in nodesToRegisters) {
            return nodesToRegisters[node]!!
        }

        val register = AsmRegister(registerCounter++)
        nodesToRegisters[node] = register
        return register
    }

    private fun IrNode.ControlNode.controlSuccessors() = successorInfo.getControlSuccessors(this)

    private fun IrNode.RegionNode.controlSuccessor(): IrNode.ControlNode {
        val successors = this.controlSuccessors()
        require(successors.size == 1) { "RegionNode should have exactly one control successor, found ${successors.size}." }
        return successors.first()
    }

    private fun IrNode.IfNode.projectionNodes(): Pair<IrNode.IfProjectionNode, IrNode.IfProjectionNode> {
        val successors = this.controlSuccessors()
        val trueProjection = successors.find { it is IrNode.IfProjectionNode && it.type == IrNode.IfProjectionType.TRUE_BRANCH } ?: error("IfNode should have a true projection")
        val falseProjection = successors.find { it is IrNode.IfProjectionNode && it.type == IrNode.IfProjectionType.FALSE_BRANCH } ?: error("IfNode should have a false projection")
        return Pair(trueProjection as IrNode.IfProjectionNode, falseProjection as IrNode.IfProjectionNode)
    }
}

private class SuccessorInfo(irGraph: IrGraph) {
    private val controlSuccessors = mutableMapOf<IrNode.ControlNode, MutableSet<IrNode.ControlNode>>()
    private val seen = mutableSetOf<IrNode>()

    init {
        calculateSuccessorInfo(irGraph.endNode)
    }

    fun getControlSuccessors(node: IrNode.ControlNode): Set<IrNode.ControlNode> {
        return controlSuccessors.getOrDefault(node, emptySet())
    }

    private fun calculateSuccessorInfo(node: IrNode) {
        if (node in seen) {
            return
        }

        seen.add(node)

        if (node is IrNode.EndNode) {
            for (returnNode in node.returnNodes) {
                calculateSuccessorInfo(returnNode)
            }
            return
        }

        if (node is IrNode.RegionNode) {
            controlSuccessors.getOrPut(node.first) { mutableSetOf() }.add(node)
            controlSuccessors.getOrPut(node.second!!) { mutableSetOf() }.add(node)
            calculateSuccessorInfo(node.first)
            calculateSuccessorInfo(node.second!!)
            return
        }

        if (node is IrNode.ControlRelevantNode) {
            controlSuccessors.getOrPut(node.control) { mutableSetOf() }.add(node)
            calculateSuccessorInfo(node.control)
        }
    }
}

private class BasicBlockBuilder(val label: AsmLabel) {
    private val instructions: MutableList<AsmIr.Instruction> = mutableListOf()
    private var finalJump: AsmIr.Jump? = null

    fun addFirst(instruction: AsmIr.Instruction) = instructions.addFirst(instruction)
    fun addLast(instruction: AsmIr.Instruction) = instructions.add(instruction)
    fun setFinalJump(block: BasicBlockBuilder) {
        finalJump = AsmIr.Jump(block.label)
    }

    fun build(): AsmIr.BasicBlock {
        require(label.name == "final_return" || finalJump != null) { "Block ${label.name} does not have a final jump." }
        if (finalJump != null) {
            instructions.addLast(finalJump!!)
        }
        return AsmIr.BasicBlock(label, instructions)
    }
}