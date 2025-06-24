package edu.kit.kastel.vads.compiler.backend

import edu.kit.kastel.vads.compiler.backend.ir.AsmIr

interface RegisterAllocation<T : Architecture> {
    operator fun get(operand: AsmIr.Register): Location<T>
    val numberOfStackVariables: Int
}

data class RegisterInformation<T : Architecture>(val registers: Map<AsmIr.Operand, Location<T>>, val additionalRegisters: Map<AsmIr.Operand, Register<T>> = mapOf()) {
    operator fun get(operand: AsmIr.Operand): Location<T> = registers[operand]!!
}

sealed interface RegisterConstraint<T : Architecture> {
    sealed interface InstructionConstraint<T : Architecture> : RegisterConstraint<T> {
        val instruction: AsmIr.Instruction
    }

    data class NeedsFreeRegisters<T : Architecture>(override val instruction: AsmIr.Instruction, val registers: Set<Register<T>>) : InstructionConstraint<T>
}

interface RegisterAllocator<T : Architecture> {
    fun allocateRegisters(
        availableRegisters: List<Register<T>>,
        function: AsmIr.Function,
        stackSlotCreator: (Int) -> StackLocation<T>,
    ): RegisterAllocation<T>
}
