package edu.kit.kastel.vads.compiler.backend

import edu.kit.kastel.vads.compiler.backend.ir.AsmIr
import edu.kit.kastel.vads.compiler.ir.IrGraph
import edu.kit.kastel.vads.compiler.ir.IrNode
import edu.kit.kastel.vads.compiler.ir.IrProgram

fun lowerIrToAsmIr(irProgram: IrProgram): AsmIr.Program {
    val functions = irProgram.graphs.map { Lowerer(it).lower() }
    return AsmIr.Program(functions)
}

private class Lowerer(private val irGraph: IrGraph) {
    private val blocks: MutableMap<AsmIr.Label, BasicBlockBuilder> = mutableMapOf()
    private var registerCounter = 0
    private val nodesToRegisters: MutableMap<IrNode, AsmIr.Register> = mutableMapOf()
    private val generatedNodes: MutableSet<IrNode> = mutableSetOf()
    private val dataNodesToInstructions: MutableMap<IrNode.DataNode, AsmIr.Instruction> = mutableMapOf()
    private val controlsToBlock: MutableMap<IrNode.ControlNode, BasicBlockBuilder> = mutableMapOf()
    private val successorInfo = SuccessorInfo(irGraph)
    private val finalReturnBlock = block(AsmIr.Label("final_return"))
    private val parameterRegisters = irGraph.parameters.associateWith { register(it) }

    fun lower(): AsmIr.Function {
        val startSuccessors = IrNode.StartNode.controlSuccessors()

        // First, we create all the basic blocks from the sea of nodes.
        val startBlock = block(label(IrNode.StartNode))
        controlsToBlock[IrNode.StartNode] = startBlock
        with(startBlock) {
            for (successor in startSuccessors) {
                generateControlNode(successor)
            }
        }

        // Then we put all the data nodes into the blocks.
        val returnRegister = register(irGraph.endNode)
        finalReturnBlock.addLast(AsmIr.Return(returnRegister))

        for (returnNode in irGraph.endNode.returnNodes) {
            val block = block(label(returnNode))

            with(block) {
                when (returnNode.result) {
                    is IrNode.ParameterNode -> addLast(AsmIr.Move(returnRegister, source(returnNode.result)))

                    is IrNode.ConstantNode<*> -> {
                        nodesToRegisters[returnNode.result] = returnRegister
                        addLast(AsmIr.Move(returnRegister, source(returnNode.result)))
                    }

                    else -> {
                        nodesToRegisters[returnNode.result] = returnRegister
                        generateDataNode(returnNode.result, returnRegister)
                    }
                }
            }

            block.setFinalJump(finalReturnBlock)
        }

        // Generate the data nodes for control nodes that have data input (e.g., Ifs and Calls).
        generateControlNodeDataInputs()

        // Ensure all side effect nodes are generated, as they might not be reachable from the start node.
        for (returnNode in irGraph.endNode.returnNodes) {
            val block = controlsToBlock[returnNode]!!
            with(block) {
                generateSideEffectNode(returnNode.sideEffect)
            }
        }

        return AsmIr.Function(irGraph.name, parameterRegisters.values.toList(), blocks.values.map { it.build() }, startBlock.build(), finalReturnBlock.build())
    }

    private fun generateControlNodeDataInputs() {
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
                IrNode.StartNode -> node.controlSuccessors().forEach { visitNode(it) }

                is IrNode.NormalCallNode -> {
                    val argumentsToGenerate = node.arguments.filter { it in nodesToRegisters }.associateWith { nodesToRegisters[it]!! }
                    val block = controlsToBlock[node]!!
                    with(block) {
                        // Generate the arguments in reverse order, as they are added to the start of the block
                        argumentsToGenerate.toList().asReversed().forEach { (argNode, register) -> generateDataNode(argNode, register) }
                    }
                    node.controlSuccessors().forEach { visitNode(it) }
                }

                is IrNode.PrintNode -> {
                    if (node.parameter in nodesToRegisters) {
                        val destination = nodesToRegisters[node.parameter]!!
                        val block = controlsToBlock[node]!!
                        with(block) { generateDataNode(node.parameter, destination) }
                    }
                    node.controlSuccessors().forEach { visitNode(it) }
                }

                is IrNode.FlushNode -> node.controlSuccessors().forEach { visitNode(it) }
                is IrNode.ReadNode -> node.controlSuccessors().forEach { visitNode(it) }
            }
        }

        visitNode(IrNode.StartNode)
    }

    context(currentBlock: BasicBlockBuilder)
    private fun generateSideEffectNode(sideEffectNode: IrNode.SideEffectNode) {
        if (sideEffectNode in generatedNodes) {
            return
        }

        when (sideEffectNode) {
            is IrNode.BuiltinCallNode -> generateBuiltinCallNode(sideEffectNode, null) // if the result is needed, the node was already generated by data node generation
            is IrNode.NormalCallNode -> generateNormalCallNode(sideEffectNode, null) // if the result is needed, the node was already generated by data node generation
            is IrNode.ReturnNode -> generateSideEffectNode(sideEffectNode.sideEffect)
            is IrNode.DivNode -> {
                generateDataNode(sideEffectNode, register(sideEffectNode))
                generateSideEffectNode(sideEffectNode.sideEffect)
            }

            is IrNode.ModNode -> {
                generateDataNode(sideEffectNode, register(sideEffectNode))
                generateSideEffectNode(sideEffectNode.sideEffect)
            }

            is IrNode.SideEffectPhiNode -> {
                if (sideEffectNode.first is IrNode.SideEffectPhiNode) {
                    generateSideEffectNode(sideEffectNode.first)
                } else {
                    val block1 = controlsToBlock[sideEffectNode.firstControl]!!
                    with(block1) {
                        generateSideEffectNode(sideEffectNode.first)
                    }
                }

                if (sideEffectNode.second is IrNode.SideEffectPhiNode) {
                    generateSideEffectNode(sideEffectNode.second)
                } else {
                    val block2 = controlsToBlock[sideEffectNode.secondControl]!!
                    with(block2) {
                        generateSideEffectNode(sideEffectNode.second)
                    }
                }
            }

            IrNode.StartNode -> {}
        }

        generatedNodes.add(sideEffectNode)
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
            IrNode.StartNode -> {} // handled in the main lower function
            is IrNode.NormalCallNode if controlNode.dataSuccessors().isNotEmpty() -> {
                controlsToBlock[controlNode] = currentBlock
                controlNode.controlSuccessors().forEach { generateControlNode(it) }
                generatedNodes.remove(controlNode)
            }

            is IrNode.NormalCallNode if controlNode.dataSuccessors().isEmpty() -> {
                val arguments = controlNode.arguments.map { source(it) }

                val instruction = AsmIr.Call(controlNode.name.asString(), arguments, null)
                currentBlock.addLast(instruction)
                controlsToBlock[controlNode] = currentBlock
                controlNode.controlSuccessors().forEach { generateControlNode(it) }
            }

            is IrNode.BuiltinCallNode if controlNode.dataSuccessors().isNotEmpty() -> {
                controlNode.controlSuccessors().forEach { generateControlNode(it) }
                generatedNodes.remove(controlNode)
            }

            is IrNode.FlushNode if controlNode.dataSuccessors().isEmpty() -> {
                val instruction = AsmIr.CallFlush(null)
                currentBlock.addLast(instruction)
                controlsToBlock[controlNode] = currentBlock
                controlNode.controlSuccessors().forEach { generateControlNode(it) }
            }

            is IrNode.PrintNode if controlNode.dataSuccessors().isEmpty() -> {
                val instruction = AsmIr.CallPrint(source(controlNode.parameter), null)
                currentBlock.addLast(instruction)
                controlsToBlock[controlNode] = currentBlock
                controlNode.controlSuccessors().forEach { generateControlNode(it) }
            }

            is IrNode.ReadNode if controlNode.dataSuccessors().isEmpty() -> {
                val instruction = AsmIr.CallRead(null)
                currentBlock.addLast(instruction)
                controlsToBlock[controlNode] = currentBlock
                controlNode.controlSuccessors().forEach { generateControlNode(it) }
            }

            else -> error("should not happen")
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

        if (loopRegionNode.backEdge != null) {
            val backEdgeBlock = controlsToBlock[loopRegionNode.backEdge!!]!!
            backEdgeBlock.setFinalJump(loopBlock)
        }
    }

    context(currentBlock: BasicBlockBuilder)
    private fun generateReturnNode(returnNode: IrNode.ReturnNode) {
        val returnBlock = block(label(returnNode))
        controlsToBlock[returnNode] = returnBlock
        returnBlock.setFinalJump(finalReturnBlock)

        currentBlock.setFinalJump(returnBlock)
    }

    context(currentBlock: BasicBlockBuilder)
    private fun generateDataNode(node: IrNode.DataNode, destination: AsmIr.Register) {
        // Constant nodes are data nodes and only result in an immediate move instruction. We thus don't have to ensure that we don't generate them multiple times.
        if (node !is IrNode.ConstantNode<*> && node in generatedNodes) {
            return
        }

        generatedNodes.add(node)

        when (node) {
            is IrNode.UnaryOperationNode -> generateUnaryOperation(node, destination)
            is IrNode.BinaryOperationNode -> generateBinaryOperation(node, destination)
            is IrNode.ConstantNode<*> -> currentBlock.addFirst(AsmIr.Move(destination, source(node)))
            is IrNode.PhiNode -> generatePhiNode(node, destination)
            is IrNode.BuiltinCallNode -> generateBuiltinCallNode(node, destination)
            is IrNode.NormalCallNode -> generateNormalCallNode(node, destination)
            is IrNode.ParameterNode -> {
                val source = source(node)
                currentBlock.addFirst(AsmIr.Move(destination, source))
            }
        }
    }

    context(currentBlock: BasicBlockBuilder)
    private fun generatePhiNode(phiNode: IrNode.PhiNode, destination: AsmIr.Register) {
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
    private fun generateUnaryOperation(operation: IrNode.UnaryOperationNode, destination: AsmIr.Register) {
        val source = source(operation.inNode)

        val unaryOperationType = when (operation) {
            is IrNode.NegateNode -> AsmIr.UnaryOperationType.NEGATE
            is IrNode.LogicalNotNode -> AsmIr.UnaryOperationType.LOGICAL_NOT
            is IrNode.BitwiseNotNode -> AsmIr.UnaryOperationType.BITWISE_NOT
        }


        if (operation.inNode !is IrNode.ConstantNode<*>) {
            generateDataNode(operation.inNode, source as AsmIr.Register)
        }

        val sourceInstruction = dataNodesToInstructions[operation.inNode]
        val instruction = AsmIr.UnaryOperation(unaryOperationType, destination, source)
        currentBlock.addFirst(instruction, setOfNotNull(sourceInstruction))
        dataNodesToInstructions[operation] = instruction
    }

    context(currentBlock: BasicBlockBuilder)
    private fun generateBinaryOperation(operation: IrNode.BinaryOperationNode, destination: AsmIr.Register) {
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

        if (operation.left !is IrNode.ConstantNode<*>) {
            generateDataNode(operation.left, leftSource as AsmIr.Register)
        }

        if (operation.right !is IrNode.ConstantNode<*>) {
            generateDataNode(operation.right, rightSource as AsmIr.Register)
        }

        val leftInstruction = dataNodesToInstructions[operation.left]
        val rightInstruction = dataNodesToInstructions[operation.right]
        val instruction = AsmIr.BinaryOperation(binaryOperationType, destination, leftSource, rightSource)
        currentBlock.addFirst(instruction, setOfNotNull(leftInstruction, rightInstruction))
        dataNodesToInstructions[operation] = instruction
    }

    context(currentBlock: BasicBlockBuilder)
    private fun generateBuiltinCallNode(node: IrNode.BuiltinCallNode, destination: AsmIr.Register?) {
        val instruction = when (node) {
            is IrNode.PrintNode -> {
                val source = source(node.parameter)
                if (source is AsmIr.Register) {
                    generateDataNode(node.parameter, source)
                }
                AsmIr.CallPrint(source, destination)
            }

            is IrNode.ReadNode -> AsmIr.CallRead(destination)
            is IrNode.FlushNode -> AsmIr.CallFlush(destination)
        }
        currentBlock.addFirst(instruction)
        if (destination != null) {
            nodesToRegisters[node] = destination
        }
    }

    context(currentBlock: BasicBlockBuilder)
    private fun generateNormalCallNode(node: IrNode.NormalCallNode, destination: AsmIr.Register?) {
        val arguments = node.arguments.map {
            val source = source(it)
            if (it !is IrNode.ConstantNode<*>) {
                generateDataNode(it, source as AsmIr.Register)
            }
            source
        }

        val instruction = AsmIr.Call(node.name.asString(), arguments, destination)
        currentBlock.addFirst(instruction)

        if (destination != null) {
            nodesToRegisters[node] = destination
        }
    }

    private fun block(label: AsmIr.Label): BasicBlockBuilder {
        return blocks.getOrPut(label) { BasicBlockBuilder(label) }
    }

    private fun label(controlNode: IrNode.ControlNode): AsmIr.Label {
        val name = when (controlNode) {
            is IrNode.StartNode -> "start"
            is IrNode.ReturnNode -> "return_${controlNode.hashCode()}"
            is IrNode.IfNode -> "if_${controlNode.hashCode()}"
            is IrNode.IfProjectionNode if controlNode.type == IrNode.IfProjectionType.TRUE_BRANCH -> "if_true_${controlNode.hashCode()}"
            is IrNode.IfProjectionNode if controlNode.type == IrNode.IfProjectionType.FALSE_BRANCH -> "if_false_${controlNode.hashCode()}"
            is IrNode.IfProjectionNode -> error("Exhaustiveness check in kotlin not good enough, this should not happen")
            is IrNode.LoopRegionNode -> "loop_region_${controlNode.hashCode()}"
            is IrNode.RegionNode -> "region_${controlNode.hashCode()}"
            is IrNode.BuiltinCallNode -> error("BuiltinCallNode does not have a label")
            is IrNode.NormalCallNode -> error("NormalCallNode does not have a label")
        }.replace("-", "_")
        return AsmIr.Label(name)
    }

    private fun source(node: IrNode.DataNode): AsmIr.Operand {
        if (node is IrNode.IntegerConstantNode) {
            return AsmIr.Immediate(node.value)
        }

        if (node is IrNode.BooleanConstantNode) {
            return AsmIr.Immediate(if (node.value) 1u else 0u)
        }

        return register(node)
    }

    private fun register(node: IrNode): AsmIr.Register {
        if (node in nodesToRegisters) {
            return nodesToRegisters[node]!!
        }

        val register = AsmIr.Register(registerCounter++)
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

    private fun IrNode.DataNode.dataSuccessors(): Set<IrNode.DataNodeConsumingNode> = successorInfo.getDataSuccessors(this)
}

private class SuccessorInfo(irGraph: IrGraph) {
    private val controlSuccessors = mutableMapOf<IrNode.ControlNode, MutableSet<IrNode.ControlNode>>()
    private val dataSuccessors = mutableMapOf<IrNode.DataNode, MutableSet<IrNode.DataNodeConsumingNode>>()
    private val seen = mutableSetOf<IrNode>()

    init {
        calculateSuccessorInfo(irGraph.endNode)
    }

    fun getControlSuccessors(node: IrNode.ControlNode): Set<IrNode.ControlNode> {
        return controlSuccessors.getOrDefault(node, emptySet())
    }

    fun getDataSuccessors(node: IrNode.DataNode): Set<IrNode.DataNodeConsumingNode> {
        return dataSuccessors.getOrDefault(node, emptySet())
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
            calculateSuccessorInfo(node.first)
            node.second?.let { controlSuccessors.getOrPut(it) { mutableSetOf() }.add(node) }
            node.second?.let { calculateSuccessorInfo(it) }
            return
        }

        if (node is IrNode.ControlRelevantNode) {
            controlSuccessors.getOrPut(node.control) { mutableSetOf() }.add(node)
            calculateSuccessorInfo(node.control)
        }

        if (node is IrNode.DataNodeConsumingNode) {
            node.dataInputs.forEach {
                dataSuccessors.getOrPut(it) { mutableSetOf() }.add(node)
                calculateSuccessorInfo(it)
            }
        }
    }
}
