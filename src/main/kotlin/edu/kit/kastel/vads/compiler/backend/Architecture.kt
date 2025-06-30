package edu.kit.kastel.vads.compiler.backend

import edu.kit.kastel.vads.compiler.backend.x86.X86Assembler
import edu.kit.kastel.vads.compiler.backend.x86.X86CodeGenerator
import edu.kit.kastel.vads.compiler.backend.x86.X86Registers

interface Architecture {
    val name: String
    fun createCodeGenerator(): CodeGenerator<out Architecture>
    fun createAssembler(): Assembler<out Architecture>
    fun createConstraintGenerator(): ConstraintGenerator<out Architecture>
    fun getAvailableRegisters(): Set<Register<out Architecture>>
}

object X86Architecture : Architecture {
    val TEMP_REGISTER = X86Registers.R15D

    private val RESERVED_REGISTERS = setOf(
        X86Registers.EAX,
        X86Registers.EDX,
        X86Registers.RSP,
        X86Registers.RBP,
        TEMP_REGISTER
    )

    override val name: String = "x86"
    override fun createCodeGenerator(): CodeGenerator<out X86Architecture> = X86CodeGenerator()
    override fun createAssembler(): Assembler<out X86Architecture> = X86Assembler()
    override fun createConstraintGenerator(): ConstraintGenerator<out X86Architecture> = X86ConstraintGenerator
    override fun getAvailableRegisters(): Set<Register<X86Architecture>> = (X86Registers.entries - RESERVED_REGISTERS).toSet()
}

