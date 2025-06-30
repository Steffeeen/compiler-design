package edu.kit.kastel.vads.compiler.backend

import edu.kit.kastel.vads.compiler.backend.x86.X86Assembler
import edu.kit.kastel.vads.compiler.backend.x86.X86CodeGenerator
import edu.kit.kastel.vads.compiler.backend.x86.X86Registers
import edu.kit.kastel.vads.compiler.backend.x86.X86Registers.*

interface Architecture {
    val name: String
    fun createCodeGenerator(): CodeGenerator<out Architecture>
    fun createAssembler(): Assembler<out Architecture>
    fun createConstraintGenerator(): ConstraintGenerator<out Architecture>
    fun getAvailableRegisters(): Set<Register<out Architecture>>
    fun getCallerSavedRegisters(): List<Register<out Architecture>>
    fun getCalleeSavedRegisters(): List<Register<out Architecture>>
    fun getArgumentRegisters(): List<Register<out Architecture>>
}

object X86Architecture : Architecture {
    val TEMP_REGISTER = X86Registers.R15D

    private val RESERVED_REGISTERS = setOf(EAX, EDX, RSP, RBP, TEMP_REGISTER)

    override val name: String = "x86"
    override fun createCodeGenerator(): CodeGenerator<out X86Architecture> = X86CodeGenerator()
    override fun createAssembler(): Assembler<out X86Architecture> = X86Assembler()
    override fun createConstraintGenerator(): ConstraintGenerator<out X86Architecture> = X86ConstraintGenerator
    override fun getAvailableRegisters(): Set<Register<X86Architecture>> = (X86Registers.entries - RESERVED_REGISTERS).toSet()
    override fun getCallerSavedRegisters(): List<Register<X86Architecture>> = listOf(EAX, ECX, EDX, ESI, EDI, R8D, R9D, R10D, R11D)
    override fun getCalleeSavedRegisters(): List<Register<X86Architecture>> = listOf(EBX, R12D, R13D, R14D, R15D)
    override fun getArgumentRegisters(): List<Register<X86Architecture>> = listOf(EDI, ESI, EDX, ECX, R8D, R9D)
}

