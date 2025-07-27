package edu.kit.kastel.vads.compiler.backend

import edu.kit.kastel.vads.compiler.backend.ir.AsmIr

interface CodeGenerator<T : Architecture<T>> {
    fun generateCode(asmProgram: AsmIr.Program, registerAllocations: Map<AsmIr.Function, RegisterAllocation<T>>): Assembly<T>
}