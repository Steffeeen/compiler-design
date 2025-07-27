package edu.kit.kastel.vads.compiler.backend

import edu.kit.kastel.vads.compiler.backend.x86.*
import edu.kit.kastel.vads.compiler.backend.x86.X86Registers.*

interface Architecture<T : Architecture<T>> {
    val name: String
    fun createCodeGenerator(): CodeGenerator<T>
    fun createAssembler(): Assembler<T>
    fun createConstraintGenerator(): ConstraintGenerator<T>
    fun getAvailableRegisters(): Set<Register<T>>
    fun getCallerSavedRegisters(): List<Register<T>>
    fun getCalleeSavedRegisters(): List<Register<T>>
    fun getArgumentRegisters(): List<Register<T>>
    fun createStackSlot(index: Int): SpillLocation<T>
    fun createArgumentLocation(index: Int): ArgumentLocation<T>
}

object X86Architecture : Architecture<X86Architecture> {
    val TEMP_REGISTER = R15D

    private val RESERVED_REGISTERS: Set<X86Register> = setOf(EAX, EDX, ESP, EBP, TEMP_REGISTER)

    override val name: String = "x86"
    override fun createCodeGenerator(): CodeGenerator<X86Architecture> = X86CodeGenerator()
    override fun createAssembler(): Assembler<X86Architecture> = X86Assembler()
    override fun createConstraintGenerator(): ConstraintGenerator<X86Architecture> = X86ConstraintGenerator
    override fun getAvailableRegisters(): Set<Register<X86Architecture>> = (X86Registers.entries - RESERVED_REGISTERS).toSet()
    override fun getCallerSavedRegisters(): List<Register<X86Architecture>> = listOf(EAX, ECX, EDX, ESI, EDI, R8D, R9D, R10D, R11D)
    override fun getCalleeSavedRegisters(): List<Register<X86Architecture>> = listOf(EBX, R12D, R13D, R14D, R15D)
    override fun getArgumentRegisters(): List<Register<X86Architecture>> = listOf(EDI, ESI, EDX, ECX, R8D, R9D)
    override fun createStackSlot(index: Int): SpillLocation<X86Architecture> = X86SpillLocation(index)
    override fun createArgumentLocation(index: Int): ArgumentLocation<X86Architecture> = X86ArgumentLocation(index)
}

