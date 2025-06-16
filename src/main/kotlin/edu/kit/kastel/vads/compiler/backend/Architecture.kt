package edu.kit.kastel.vads.compiler.backend

import edu.kit.kastel.vads.compiler.backend.x86.X86Assembler
import edu.kit.kastel.vads.compiler.backend.x86.X86CodeGenerator

interface Architecture {
    val name: String
    fun createCodeGenerator(): CodeGenerator<out Architecture>
    fun createAssembler(): Assembler<out Architecture>
    fun createConstraintGenerator(): ConstraintGenerator<out Architecture>
}

object X86Architecture : Architecture {
    override val name: String = "x86"
    override fun createCodeGenerator(): CodeGenerator<out Architecture> = X86CodeGenerator()
    override fun createAssembler(): Assembler<X86Architecture> = X86Assembler()
    override fun createConstraintGenerator(): ConstraintGenerator<out Architecture> = X86ConstraintGenerator
}

