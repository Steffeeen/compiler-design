package edu.kit.kastel.vads.compiler.backend

import edu.kit.kastel.vads.compiler.backend.ir.AsmIr
import edu.kit.kastel.vads.compiler.backend.x86.X86Registers

interface ConstraintGenerator<T : Architecture> {
    fun generateConstraints(instructions: List<AsmIr.Instruction>): List<RegisterConstraint<T>>
}

typealias X86RegisterConstraint = RegisterConstraint<X86Architecture>

object X86ConstraintGenerator : ConstraintGenerator<X86Architecture> {
    override fun generateConstraints(instructions: List<AsmIr.Instruction>): List<X86RegisterConstraint> {
        val constraints = mutableListOf<X86RegisterConstraint>()

        for (instruction in instructions) {
            when (instruction) {
                is AsmIr.BinaryOperation if instruction.operation in setOf(
                    AsmIr.BinaryOperationType.DIVIDE,
                    AsmIr.BinaryOperationType.MODULO
                ) -> constraints.add(RegisterConstraint.NeedsFreeRegisters(instruction, setOf(X86Registers.EAX, X86Registers.EDX)))

                else -> {}
            }
        }
        return constraints
    }
}
