package edu.kit.kastel.vads.compiler.backend

import edu.kit.kastel.vads.compiler.backend.ir.AsmIr

interface RegisterAllocation<T : Architecture<T>> {
    operator fun get(instruction: AsmIr.Instruction): Map<AsmIr.Register, AllocationInformation<T>>
    val numberOfStackVariables: Int
    val calleeSavedRegisterReloads: List<AllocationInformation.Reload<T>>
}

sealed interface AllocationInformation<T : Architecture<T>> {
    data class NormalRegister<T : Architecture<T>>(val register: Register<T>) : AllocationInformation<T>
    data class Spill<T : Architecture<T>>(val register: Register<T>, val spillLocation: SpillLocation<T>) : AllocationInformation<T>
    data class Reload<T : Architecture<T>>(val register: Register<T>, val reloadLocation: StackLocation<T>) : AllocationInformation<T>
    data class SpillAndReload<T : Architecture<T>>(val register: Register<T>, val spillLocation: SpillLocation<T>, val reloadLocation: StackLocation<T>) : AllocationInformation<T>
}

sealed interface RegisterConstraint<T : Architecture<T>> {
    sealed interface InstructionConstraint<T : Architecture<T>> : RegisterConstraint<T> {
        val instruction: AsmIr.Instruction
    }

    data class NeedsFreeRegisters<T : Architecture<T>>(override val instruction: AsmIr.Instruction, val registers: Set<Register<T>>) : InstructionConstraint<T>
}

interface RegisterAllocator<T : Architecture<T>> {
    fun allocateRegisters(availableRegisters: Set<Register<T>>, function: AsmIr.Function): RegisterAllocation<T>
}
