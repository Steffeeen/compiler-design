package edu.kit.kastel.vads.compiler.backend

import edu.kit.kastel.vads.compiler.backend.ir.AsmIr
import edu.kit.kastel.vads.compiler.backend.ir.AsmOperand
import edu.kit.kastel.vads.compiler.backend.ir.AsmRegister

interface RegisterAllocation<T : Architecture> {
    operator fun get(operand: AsmRegister): Location<T>
    val numberOfStackVariables: Int
}

data class RegisterInformation<T : Architecture>(val registers: Map<AsmOperand, Location<T>>, val additionalRegisters: Map<AsmOperand, Register<T>> = mapOf()) {
    operator fun get(operand: AsmOperand): Location<T> = registers[operand]!!
}

sealed interface RegisterConstraint<T : Architecture> {
    sealed interface OperandConstraint<T : Architecture> : RegisterConstraint<T> {
        val operand: AsmOperand
    }

    sealed interface InstructionConstraint<T : Architecture> : RegisterConstraint<T> {
        val instruction: AsmIr.Instruction
    }

    data class AnyRegisterExcept<T : Architecture, R : Register<T>>(override val operand: AsmOperand, val except: Set<R>) : OperandConstraint<T>
    data class SpecificRegister<T : Architecture, R : Register<T>>(override val operand: AsmOperand, val register: R) : OperandConstraint<T>
    data class NeedsAdditionalRegisters<T : Architecture>(override val operand: AsmOperand, val additionalRegisters: Set<Register<T>>) : OperandConstraint<T>

    data class NeedsOneRegister<T : Architecture>(override val instruction: AsmIr.Instruction) : InstructionConstraint<T>
}

interface RegisterAllocator<T : Architecture> {
    fun allocateRegisters(availableRegisters: List<Register<T>>, function: AsmIr.Function, constraints: Map<AsmIr.Instruction, List<RegisterConstraint<T>>>): RegisterAllocation<T>
}
