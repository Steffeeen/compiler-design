package edu.kit.kastel.vads.compiler.backend

import edu.kit.kastel.vads.compiler.backend.ir.AsmIr

class BasicBlockBuilder(val label: AsmIr.Label) {
    private val instructions: MutableList<AsmIr.Instruction> = mutableListOf()
    private var finalJump: AsmIr.Jump? = null

    fun addFirst(instruction: AsmIr.Instruction, ensureAfter: Set<AsmIr.Instruction> = setOf()) {
        val insertionIndex = ensureAfter.maxOfOrNull { instructions.indexOf(it) }?.plus(1) ?: 0
        instructions.add(insertionIndex, instruction)
    }

    fun addLast(instruction: AsmIr.Instruction) = instructions.add(instruction)

    fun addBefore(instructionToAdd: AsmIr.Instruction, before: AsmIr.Instruction) {
        val index = instructions.indexOf(before)
        require(index != -1) { "Instruction to add before not found in the block." }
        instructions.add(index, instructionToAdd)
    }

    fun setFinalJump(block: BasicBlockBuilder) {
        finalJump = AsmIr.Jump(block.label)
    }

    fun build(): AsmIr.BasicBlock {
        require(label.name == "final_return" || finalJump != null) { "Block ${label.name} does not have a final jump." }
        if (finalJump != null) {
            return AsmIr.BasicBlock(label, instructions + finalJump!!)
        }
        return AsmIr.BasicBlock(label, instructions)
    }
}