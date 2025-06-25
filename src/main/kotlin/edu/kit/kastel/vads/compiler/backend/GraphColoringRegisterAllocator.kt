package edu.kit.kastel.vads.compiler.backend

import edu.kit.kastel.vads.compiler.backend.ir.AsmIr

data class SimpleRegisterAllocation<T : Architecture>(override val numberOfStackVariables: Int, val allocation: Map<AsmIr.Register, Location<T>>) : RegisterAllocation<T> {
    override fun get(operand: AsmIr.Register): Location<T> = allocation[operand]!!
}

private data class Predecessors(val predecessors: Map<AsmIr.BasicBlock, Set<AsmIr.BasicBlock>>) {
    operator fun get(block: AsmIr.BasicBlock): Set<AsmIr.BasicBlock> = predecessors[block]!!
}

private data class Successors(val successors: Map<AsmIr.BasicBlock, Set<AsmIr.BasicBlock>>) {
    operator fun get(block: AsmIr.BasicBlock): Set<AsmIr.BasicBlock> = successors[block]!!
}

private data class LiveRange(val ranges: List<IntRange>) {
    fun overlaps(other: LiveRange): Boolean {
        for (thisRange in ranges) {
            for (otherRange in other.ranges) {
                if (thisRange.intersect(otherRange).isNotEmpty()) {
                    return true
                }
            }
        }
        return false
    }
}

class GraphColoringRegisterAllocator<T : Architecture> : RegisterAllocator<T> {
    override fun allocateRegisters(availableRegisters: List<Register<T>>, function: AsmIr.Function, stackSlotCreator: (Int) -> StackLocation<T>): RegisterAllocation<T> {
        val predecessors = calculatePredecessors(function)
        val successors = calculateSuccessors(predecessors)

        val availableRegistersSequence: Sequence<Location<T>> = availableRegisters.asSequence() + generateSequence(0) { it + 1 }
            .map { stackSlotCreator(it) }

        return with(predecessors) {
            with(successors) {
                allocate(availableRegistersSequence, function)
            }
        }
    }
}

context(predecessors: Predecessors, successors: Successors)
private fun <T : Architecture> allocate(availableRegisters: Sequence<Location<T>>, function: AsmIr.Function): RegisterAllocation<T> {
    val instructionNumbering = numberInstructions(function.startBlock)

    val interferenceGraph = buildInterferenceGraph(instructionNumbering, function.returnBlock)

    val registerAllocation = mutableMapOf<AsmIr.Register, Location<T>>()

    for (instruction in instructionNumbering.entries.sortedBy { it.value }.map { it.key }) {
        fun handleOperand(operand: AsmIr.Operand) {
            if (operand is AsmIr.Immediate) {
                return
            }

            require(operand is AsmIr.Register)
            val interfering = (interferenceGraph[operand]!!).mapNotNull { registerAllocation[it] }
            val registerAvailableForOperand = availableRegisters - interfering
            val register = registerAvailableForOperand.first()
            registerAllocation[operand] = register
        }

        when (instruction) {
            is AsmIr.BinaryOperation -> {
                handleOperand(instruction.destination)
                handleOperand(instruction.leftSource)
                handleOperand(instruction.rightSource)
            }

            is AsmIr.UnaryOperation -> {
                handleOperand(instruction.destination)
                handleOperand(instruction.source)
            }

            is AsmIr.Move -> {
                handleOperand(instruction.destination)
                handleOperand(instruction.source)
            }

            is AsmIr.Return -> handleOperand(instruction.value)
            is AsmIr.ConditionalJump -> handleOperand(instruction.condition)
            is AsmIr.Jump -> {}
        }
    }

    val numberOfStackVariables = registerAllocation.values.mapNotNull { it as? StackLocation<T> }.maxOfOrNull { it.index }?.plus(1) ?: 0

    return SimpleRegisterAllocation(numberOfStackVariables, registerAllocation)
}

context(predecessors: Predecessors, successors: Successors)
private fun buildInterferenceGraph(instructionNumbering: Map<AsmIr.Instruction, Int>, returnBlock: AsmIr.BasicBlock): Map<AsmIr.Register, Set<AsmIr.Register>> {
    val liveness = calculateLiveness(instructionNumbering, returnBlock)
    val interferenceGraph = mutableMapOf<AsmIr.Register, MutableSet<AsmIr.Register>>()

    for ((register, liveRange) in liveness) {
        interferenceGraph[register] = mutableSetOf()
        for ((otherRegister, otherLiveRange) in liveness) {
            if (register != otherRegister && liveRange.overlaps(otherLiveRange)) {
                interferenceGraph[register]!!.add(otherRegister)
            }
        }
    }

    return interferenceGraph
}

context(predecessors: Predecessors, successors: Successors)
private fun calculateLiveness(instructionNumbering: Map<AsmIr.Instruction, Int>, returnBlock: AsmIr.BasicBlock): Map<AsmIr.Register, LiveRange> {
    fun AsmIr.Instruction.number() = instructionNumbering[this]!!

    val liveRanges = mutableMapOf<AsmIr.Register, MutableList<IntRange>>()

    val lastUse = mutableMapOf<AsmIr.Register, Int>()

    fun handleDestination(operand: AsmIr.Operand, instructionNumber: Int) {
        if (operand is AsmIr.Register) {
            val start = instructionNumber + 1
            val end = lastUse[operand]!!
            lastUse.remove(operand)
            liveRanges.getOrPut(operand) { mutableListOf() }.add(IntRange(start, end))
        }
    }

    fun handleSource(operand: AsmIr.Operand, instructionNumber: Int) {
        if (operand is AsmIr.Register && operand !in lastUse) {
            lastUse[operand] = instructionNumber
        }
    }

    // Walk blocks backwards using breadth-first search
    val queue = mutableListOf(returnBlock)
    val visited = mutableSetOf<AsmIr.BasicBlock>()

    while (queue.isNotEmpty()) {
        val block = queue.removeFirst()
        if (visited.contains(block)) continue
        visited.add(block)

        for (instruction in block.instructions.reversed()) {
            when (instruction) {
                is AsmIr.BinaryOperation -> {
                    handleDestination(instruction.destination, instruction.number())
                    handleSource(instruction.leftSource, instruction.number())
                    handleSource(instruction.rightSource, instruction.number())
                }

                is AsmIr.UnaryOperation -> {
                    handleDestination(instruction.destination, instruction.number())
                    handleSource(instruction.source, instruction.number())
                }

                is AsmIr.Return -> {
                    handleSource(instruction.value, instruction.number())
                }

                is AsmIr.Move -> {
                    handleDestination(instruction.destination, instruction.number())
                    handleSource(instruction.source, instruction.number())
                }

                else -> {}
            }
        }

        for (pred in block.predecessors()) {
            if (!visited.contains(pred)) {
                queue.add(pred)
            }
        }
    }

    require(lastUse.isEmpty())
    return liveRanges.mapValues { LiveRange(it.value) }
}

context(successors: Successors)
private fun numberInstructions(start: AsmIr.BasicBlock): Map<AsmIr.Instruction, Int> {
    val numbering = mutableMapOf<AsmIr.Instruction, Int>()

    val visited = mutableSetOf<AsmIr.BasicBlock>()
    var count = 0
    val queue = mutableListOf(start)

    // breadth-first search to number instructions
    while (queue.isNotEmpty()) {
        val block = queue.removeFirst()
        if (visited.contains(block)) continue
        visited.add(block)

        for (instruction in block.instructions) {
            numbering[instruction] = count++
        }

        for (successor in block.successors()) {
            if (!visited.contains(successor)) {
                queue.add(successor)
            }
        }
    }

    return numbering
}

private fun calculatePredecessors(function: AsmIr.Function): Predecessors {
    val predecessors = mutableMapOf<AsmIr.BasicBlock, MutableSet<AsmIr.BasicBlock>>()
    function.blocks.forEach { predecessors[it] = mutableSetOf() }

    for (block in function.blocks) {
        for (instruction in block.instructions) {
            val target = when (instruction) {
                is AsmIr.Jump -> instruction.target
                is AsmIr.ConditionalJump -> instruction.target
                else -> null
            }

            if (target == null) continue // not a jump instruction

            val targetBlock = function.blocks.find { it.label == target }
            predecessors[targetBlock]!!.add(block)
        }
    }
    return Predecessors(predecessors)
}

private fun calculateSuccessors(predecessors: Predecessors): Successors {
    val successors = mutableMapOf<AsmIr.BasicBlock, MutableSet<AsmIr.BasicBlock>>()
    predecessors.predecessors.keys.forEach { successors[it] = mutableSetOf() }
    for ((block, preds) in predecessors.predecessors) {
        for (pred in preds) {
            successors[pred]!!.add(block)
        }
    }
    return Successors(successors)
}

context(predecessors: Predecessors)
private fun AsmIr.BasicBlock.predecessors(): Set<AsmIr.BasicBlock> = predecessors[this]

context(successors: Successors)
private fun AsmIr.BasicBlock.successors(): Set<AsmIr.BasicBlock> = successors[this]