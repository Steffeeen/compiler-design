package edu.kit.kastel.vads.compiler.backend

import edu.kit.kastel.vads.compiler.backend.ir.AsmImmediate
import edu.kit.kastel.vads.compiler.backend.ir.AsmIr
import edu.kit.kastel.vads.compiler.backend.x86.X86Register
import edu.kit.kastel.vads.compiler.backend.x86.X86Registers

interface ConstraintGenerator<T : Architecture> {
    fun generateConstraints(instructions: List<AsmIr.Instruction>): Map<AsmIr.Instruction, List<RegisterConstraint<T>>>
}

typealias X86RegisterConstraint = RegisterConstraint<X86Architecture>

object X86ConstraintGenerator : ConstraintGenerator<X86Architecture> {
    override fun generateConstraints(instructions: List<AsmIr.Instruction>): Map<AsmIr.Instruction, List<X86RegisterConstraint>> {
        val constraints = mutableMapOf<AsmIr.Instruction, List<X86RegisterConstraint>>()
        for (instruction in instructions) {
            val constraintsForInstructions = when (instruction) {
                is AsmIr.BinaryOperation if instruction.operation in setOf(
                    AsmIr.BinaryOperationType.DIVIDE,
                    AsmIr.BinaryOperationType.MODULO
                ) -> createConstraintsForDivideOrModulo(instruction)

                is AsmIr.UnaryOperation if instruction.operation == AsmIr.UnaryOperationType.LOGICAL_NOT -> listOf(
                    RegisterConstraint.AnyRegisterExcept(instruction.destination, setOf<X86Register>())
                )

                is AsmIr.BinaryOperation -> listOf(RegisterConstraint.NeedsOneRegister(instruction))
                is AsmIr.ConditionalJump -> listOf(RegisterConstraint.NeedsOneRegister(instruction))

                else -> null
            }
            if (constraintsForInstructions != null) {
                constraints[instruction] = constraintsForInstructions
            }
        }
        return constraints
    }

    private fun createConstraintsForDivideOrModulo(instruction: AsmIr.BinaryOperation): List<X86RegisterConstraint> {
        val baseConstraints = listOf(
            RegisterConstraint.SpecificRegister(instruction.leftSource, X86Registers.EAX),
            RegisterConstraint.NeedsAdditionalRegisters(instruction.leftSource, setOf(X86Registers.EDX)),
            RegisterConstraint.NeedsAdditionalRegisters(instruction.destination, setOf(X86Registers.EAX, X86Registers.EDX))
        )

        if (instruction.rightSource is AsmImmediate) {
            return baseConstraints + RegisterConstraint.AnyRegisterExcept(
                instruction.rightSource,
                setOf(X86Registers.EAX, X86Registers.EDX)
            )
        }

        return baseConstraints
    }

}
