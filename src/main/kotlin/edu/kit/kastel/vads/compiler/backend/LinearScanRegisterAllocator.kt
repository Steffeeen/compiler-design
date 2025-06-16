package edu.kit.kastel.vads.compiler.backend

import edu.kit.kastel.vads.compiler.backend.ir.AsmIr
import edu.kit.kastel.vads.compiler.backend.ir.AsmOperand
import edu.kit.kastel.vads.compiler.backend.ir.AsmRegister
import edu.kit.kastel.vads.compiler.backend.x86.X86Register
import edu.kit.kastel.vads.compiler.backend.x86.X86Registers
import edu.kit.kastel.vads.compiler.backend.x86.X86StackRegister

data class X86RegisterAllocation(override val numberOfStackVariables: Int, val allocation: Map<AsmRegister, Location<X86Architecture>>) : RegisterAllocation<X86Architecture> {
    override fun get(operand: AsmRegister): Location<X86Architecture> = allocation[operand]!!
}

object LinearScanRegisterAllocator {
    fun allocateRegisters(function: AsmIr.Function): RegisterAllocation<X86Architecture> {
        val availableRegisters: MutableSet<X86Register> = X86Registers.entries.toMutableSet()
        availableRegisters.remove(X86Registers.EAX)
        availableRegisters.remove(X86Registers.EDX)
        availableRegisters.remove(X86Registers.ECX)
        availableRegisters.remove(X86Registers.RSP)
        availableRegisters.remove(X86Registers.RBP)

        val orderedBlocks = orderBlocks(function)

        val map = mutableMapOf<AsmRegister, Location<X86Architecture>>()
        var stackRegisterCount = 0

        fun handleOperand(operand: AsmOperand) {
            if (operand is AsmRegister) {
                if (map.containsKey(operand)) return
                val register = availableRegisters.firstOrNull()
                if (register != null) {
                    map[operand] = register
                    availableRegisters.remove(register)
                } else {
                    // No registers available, allocate a stack register
                    map[operand] = X86StackRegister(stackRegisterCount++)
                }
            }
        }

        for (block in orderedBlocks) {
            for (instruction in block.instructions) {
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

                    is AsmIr.Return -> handleOperand(instruction.value)
                    is AsmIr.ConditionalJump -> {}
                    is AsmIr.Jump -> {}
                }
            }
        }

        return X86RegisterAllocation(stackRegisterCount, map)
    }
}

private data class LiveRange(val ranges: List<IntRange>) {
    fun overlaps(other: LiveRange): Boolean {
        for (thisRange in ranges) {
            for (otherRange in other.ranges) {
                if (thisRange.first <= otherRange.last && otherRange.first <= thisRange.last) {
                    return true
                }
            }
        }
        return false
    }
}

private fun calculateLiveness(orderedBlocks: List<AsmIr.BasicBlock>): Map<AsmRegister, LiveRange> {
    val numbering = numberInstructions(orderedBlocks)
    fun AsmIr.Instruction.number() = numbering[this]!!

    val liveRanges = mutableMapOf<AsmRegister, MutableList<IntRange>>()

    val lastUse = mutableMapOf<AsmRegister, Int>()

    fun handleDestination(operand: AsmOperand, instructionNumber: Int) {
        if (operand is AsmRegister) {
            val start = instructionNumber + 1
            val end = lastUse[operand]!!
            lastUse.remove(operand)
            liveRanges.getOrPut(operand) { mutableListOf() }.add(IntRange(start, end))
        }
    }

    fun handleSource(operand: AsmOperand, instructionNumber: Int) {
        if (operand is AsmRegister) {
            lastUse[operand] = instructionNumber
        }
    }

    for (block in orderedBlocks.reversed()) {
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
                    handleDestination(instruction.value, instruction.number())
                }

                else -> {}
            }
        }
    }

    require(lastUse.isEmpty())
    return liveRanges.mapValues { LiveRange(it.value) }
}

private fun numberInstructions(orderedBlocks: List<AsmIr.BasicBlock>): Map<AsmIr.Instruction, Int> {
    val numbering = mutableMapOf<AsmIr.Instruction, Int>()
    var count = 0
    for (block in orderedBlocks) {
        for (instruction in block.instructions) {
            numbering[instruction] = count++
        }
    }
    return numbering
}

private fun orderBlocks(function: AsmIr.Function): List<AsmIr.BasicBlock> {
    val orderedBlocks = mutableListOf<AsmIr.BasicBlock>()
    val seen = mutableSetOf<AsmIr.BasicBlock>()
    val predecessors = calculatePredecessors(function)
    val queue = mutableListOf<AsmIr.BasicBlock>()

    queue.add(function.returnBlock)

    while (queue.isNotEmpty()) {
        val block = queue.removeFirst()
        if (seen.contains(block)) continue
        seen.add(block)
        orderedBlocks.addFirst(block)

        for (predecessor in predecessors[block]!!) {
            if (!seen.contains(predecessor)) {
                queue.addLast(predecessor)
            }
        }
    }

    return orderedBlocks
}

private fun calculatePredecessors(function: AsmIr.Function): Map<AsmIr.BasicBlock, Set<AsmIr.BasicBlock>> {
    val predecessors = mutableMapOf<AsmIr.BasicBlock, MutableSet<AsmIr.BasicBlock>>()
    function.blocks.forEach { predecessors.put(it, mutableSetOf()) }

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
    return predecessors
}