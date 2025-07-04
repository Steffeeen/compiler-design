package edu.kit.kastel.vads.compiler.backend

import edu.kit.kastel.vads.compiler.backend.ir.AsmIr
import kotlin.math.max
import kotlin.math.min

private data class SimpleRegisterAllocation<T : Architecture>(
    override val numberOfStackVariables: Int,
    private val allocations: Map<AsmIr.Register, AllocationForRegister<T>>,
) : RegisterAllocation<T> {
    override fun get(instruction: AsmIr.Instruction): Map<AsmIr.Register, AllocationInformation<T>> {
        return instruction.usedRegisters().associateWith { allocations[it]!![instruction] }
    }
}

data class AllocationForRegister<T : Architecture>(private val map: Map<AsmIr.Instruction, AllocationInformation<T>>) {
    operator fun get(instruction: AsmIr.Instruction): AllocationInformation<T> = map[instruction]!!
}

private data class Predecessors(val predecessors: Map<AsmIr.BasicBlock, Set<AsmIr.BasicBlock>>) {
    operator fun get(block: AsmIr.BasicBlock): Set<AsmIr.BasicBlock> = predecessors[block]!!
}

private data class Successors(val successors: Map<AsmIr.BasicBlock, Set<AsmIr.BasicBlock>>) {
    operator fun get(block: AsmIr.BasicBlock): Set<AsmIr.BasicBlock> = successors[block]!!
}

private class LiveRange() {
    private var _min: Int? = null
    val min: Int get() = _min!!
    private var _max: Int? = null
    val max: Int get() = _max!!

    fun extendLower(value: Int) {
        _min = if (_min == null) value else min(value, _min!!)
    }

    fun extendUpper(value: Int) {
        _max = if (_max == null) value else max(value, _max!!)
    }

    fun overlaps(other: LiveRange): Boolean {
        return (this.min <= other.max && this.max >= other.min) || (other.min <= this.max && other.max >= this.min)
    }
}

class GraphColoringRegisterAllocator<T : Architecture>(val architecture: T) : RegisterAllocator<T> {
    override fun allocateRegisters(availableRegisters: Set<Register<T>>, function: AsmIr.Function, stackSlotCreator: (Int) -> StackLocation<T>): RegisterAllocation<T> {
        val predecessors = calculatePredecessors(function)
        val successors = calculateSuccessors(predecessors)

        val availableRegistersSequence: Sequence<Location<T>> = availableRegisters.asSequence() + generateSequence(0) { it + 1 }
            .map { stackSlotCreator(it) }

        return with(predecessors) {
            with(successors) {
                with(architecture) {
                    allocate(availableRegistersSequence, function)
                }
            }
        }
    }
}

context(predecessors: Predecessors, successors: Successors, architecture: T)
private fun <T : Architecture> allocate(availableRegisters: Sequence<Location<T>>, function: AsmIr.Function): RegisterAllocation<T> {
    val instructionNumbering = numberInstructions(function.startBlock)

    val interferenceGraph = buildInterferenceGraph(instructionNumbering, function.returnBlock)

    val allocations = mutableMapOf<AsmIr.Register, MutableMap<AsmIr.Instruction, AllocationInformation<T>>>()
    val currentAllocations = mutableMapOf<AsmIr.Register, Location<T>>()

    val parameterHardwareRegisters = architecture.getArgumentRegisters()
    require(function.parameters.size <= parameterHardwareRegisters.size) { TODO("Implement stack arguments in register allocator") }
    for ((index, parameterRegister) in function.parameters.withIndex()) {
        @Suppress("UNCHECKED_CAST")
        currentAllocations[parameterRegister] = parameterHardwareRegisters[index] as Register<T>
        allocations[parameterRegister] = mutableMapOf()
    }

    for (instruction in instructionNumbering.entries.sortedBy { it.value }.map { it.key }) {
        fun handleOperand(instruction: AsmIr.Instruction, operand: AsmIr.Operand) {
            if (operand is AsmIr.Immediate) {
                return
            }
            require(operand is AsmIr.Register) // should be a register

            if (operand in currentAllocations && currentAllocations[operand]!! is Register<T>) {
                allocations[operand]!![instruction] = AllocationInformation.NormalRegister(currentAllocations[operand] as Register<T>)
                return
            }

            val interfering = (interferenceGraph[operand] ?: setOf()).mapNotNull { currentAllocations[it] }
            val available = availableRegisters - interfering

            val locationToUse = available.first()
            if (locationToUse is Register<T>) {
                // If we have a register available, use it
                if (operand in currentAllocations) {
                    // reload already allocated register
                    val reloadLocation = currentAllocations[operand]!!
                    require(reloadLocation is StackLocation<T>) // should be a stack location
                    allocations.getOrPut(operand) { mutableMapOf() }[instruction] = AllocationInformation.Reload(locationToUse, reloadLocation)
                } else {
                    // first time we see this operand, allocate a register
                    currentAllocations[operand] = locationToUse
                    allocations.getOrPut(operand) { mutableMapOf() }[instruction] = AllocationInformation.NormalRegister(locationToUse)
                }

                return
            }

            // No register available, we need to spill
            require(locationToUse is StackLocation<T>) // should be a stack location

            // FIXME: Implement a proper heuristic to choose which register to spill
            // for now, choose a register to spill randomly
            val (asmRegister, registerToSpill) = currentAllocations.entries.filter { it.value is Register<T> }.randomOrNull()!!

            val allocationInformation = if (operand in currentAllocations) {
                val reloadLocation = currentAllocations[operand]!!
                require(reloadLocation is StackLocation<T>) { "should be a stack location" }
                AllocationInformation.SpillAndReload(registerToSpill as Register<T>, locationToUse, reloadLocation)
            } else {
                AllocationInformation.Spill(registerToSpill as Register<T>, locationToUse)
            }

            currentAllocations[asmRegister] = locationToUse
            currentAllocations[operand] = registerToSpill
            allocations.getOrPut(operand) { mutableMapOf() }[instruction] = allocationInformation
        }

        when (instruction) {
            is AsmIr.BinaryOperation -> {
                handleOperand(instruction, instruction.destination)
                handleOperand(instruction, instruction.leftSource)
                handleOperand(instruction, instruction.rightSource)
            }

            is AsmIr.UnaryOperation -> {
                handleOperand(instruction, instruction.destination)
                handleOperand(instruction, instruction.source)
            }

            is AsmIr.Move -> {
                handleOperand(instruction, instruction.destination)
                handleOperand(instruction, instruction.source)
            }

            is AsmIr.Return -> handleOperand(instruction, instruction.value)
            is AsmIr.ConditionalJump -> handleOperand(instruction, instruction.condition)
            is AsmIr.Jump -> {}
            is AsmIr.Call -> {
                instruction.destination?.let { handleOperand(instruction, it) }
                instruction.arguments.forEach { handleOperand(instruction, it) }
            }

            is AsmIr.CallBuiltin -> {
                instruction.destination?.let { handleOperand(instruction, it) }
                instruction.arguments.forEach { handleOperand(instruction, it) }
            }
        }
    }

    val maxSpillIndex1 = allocations.values.flatMap { it.values }.filterIsInstance<AllocationInformation.Spill<T>>().maxOfOrNull { it.spillLocation.index }
    val maxSpillIndex2 = allocations.values.flatMap { it.values }.filterIsInstance<AllocationInformation.SpillAndReload<T>>().maxOfOrNull { it.spillLocation.index }
    val numberOfStackVariables = maxOf(maxSpillIndex1 ?: 0, maxSpillIndex2 ?: 0) + 1 // +1 because we start counting from 0
    val finalAllocations = allocations.mapValues { AllocationForRegister(it.value) }
    return SimpleRegisterAllocation(numberOfStackVariables, finalAllocations)
}

context(predecessors: Predecessors, successors: Successors)
private fun buildInterferenceGraph(instructionNumbering: Map<AsmIr.Instruction, Int>, returnBlock: AsmIr.BasicBlock): Map<AsmIr.Register, Set<AsmIr.Register>> {
    val liveness = calculateLiveness(returnBlock)

    val liveRanges = mutableMapOf<AsmIr.Register, LiveRange>()

    for ((instruction, number) in instructionNumbering.entries.sortedBy { it.value }) {
        val liveVariables = liveness[instruction] ?: emptySet()
        for (operand in liveVariables) {
            val range = liveRanges.getOrPut(operand) { LiveRange() }
            range.extendLower(number)
            range.extendUpper(number)
        }
    }

    val interferenceGraph = mutableMapOf<AsmIr.Register, MutableSet<AsmIr.Register>>()

    for ((register, liveRange) in liveRanges) {
        interferenceGraph[register] = mutableSetOf()
        for ((otherRegister, otherLiveRange) in liveRanges) {
            if (register != otherRegister && liveRange.overlaps(otherLiveRange)) {
                interferenceGraph[register]!!.add(otherRegister)
            }
        }
    }

    return interferenceGraph
}

context(predecessors: Predecessors, successors: Successors)
private fun calculateLiveness(returnBlock: AsmIr.BasicBlock): Map<AsmIr.Instruction, Set<AsmIr.Register>> {
    val inLive = mutableMapOf<AsmIr.BasicBlock, Set<AsmIr.Register>>()
    val outLive = mutableMapOf<AsmIr.BasicBlock, Set<AsmIr.Register>>()
    val liveVariables = mutableMapOf<AsmIr.Instruction, Set<AsmIr.Register>>()

    val worklist = mutableListOf(returnBlock)

    while (worklist.isNotEmpty()) {
        val block = worklist.removeFirst()
        val oldInLive = inLive[block] ?: setOf()

        outLive[block] = block.successors().flatMap { inLive[it] ?: emptySet() }.toSet()
        val liveInCurrentBlock = outLive[block]!!.toMutableSet()

        for (instruction in block.instructions.reversed()) {
            val kill = mutableSetOf<AsmIr.Register>()
            val gen = mutableSetOf<AsmIr.Register>()
            val killAfterInstruction = mutableSetOf<AsmIr.Register>()

            fun recordUse(operand: AsmIr.Operand) {
                if (operand is AsmIr.Register) {
                    gen.add(operand)
                }
            }

            when (instruction) {
                is AsmIr.BinaryOperation -> {
                    recordUse(instruction.leftSource)
                    recordUse(instruction.rightSource)

                    if (instruction.operation.isCommutative && instruction.destination != instruction.leftSource && instruction.destination != instruction.rightSource) {
                        // If the operation is commutative, we can have the destination and the sources not interfere as we can swap operands and can thus avoid the situation
                        // where the destination and the right source are the same. If the destination is the same as the right source and the operation is not commutative,
                        // we would overwrite the right source by moving the left source into the destination. We have to do that move to transform the three-address AsmIr
                        // into two-address assembly like X86. We thus remove the destination from the live variables immediately.
                        kill.add(instruction.destination)
                    } else {
                        // The destination has to interfere with both sources. We thus remove the destination from the live variables only after writing the interfering registers
                        // for this instruction.
                        killAfterInstruction.add(instruction.destination)
                    }
                }

                is AsmIr.UnaryOperation -> {
                    recordUse(instruction.source)
                    kill.add(instruction.destination)
                }

                is AsmIr.Move -> {
                    recordUse(instruction.source)
                    kill.add(instruction.destination)
                }

                is AsmIr.Call -> {
                    instruction.arguments.forEach { recordUse(it) }
                    instruction.destination?.let { kill.add(it) }
                }

                is AsmIr.CallBuiltin -> {
                    instruction.arguments.forEach { recordUse(it) }
                    instruction.destination?.let { kill.add(it) }
                }

                is AsmIr.Return -> recordUse(instruction.value)
                is AsmIr.ConditionalJump -> recordUse(instruction.condition)
                is AsmIr.Jump -> {}
            }

            liveInCurrentBlock.removeAll(kill)
            liveInCurrentBlock.addAll(gen)
            liveVariables[instruction] = liveInCurrentBlock.toSet()
        }

        inLive[block] = liveInCurrentBlock

        if (oldInLive != inLive[block]) {
            for (pred in block.predecessors()) {
                worklist.add(pred)
            }
        }
    }

    return liveVariables
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