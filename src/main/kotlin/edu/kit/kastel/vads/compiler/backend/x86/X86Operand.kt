package edu.kit.kastel.vads.compiler.backend.x86

import edu.kit.kastel.vads.compiler.backend.*
import edu.kit.kastel.vads.compiler.backend.x86.X86Lower8BitRegisters.*

sealed interface X86Register : Register<X86Architecture>

data class X86StackRegister(override val index: Int) : StackLocation<X86Architecture>

data class X86Immediate(override val value: UInt) : Immediate<X86Architecture>

data class X86Label(override val name: String) : Label<X86Architecture>

enum class X86Registers(val lower8BitRegister: X86Lower8BitRegisters) : X86Register {
    EAX(AL),
    EBX(BL),
    ECX(CL),
    EDX(DL),
    RSP(SPL),
    RBP(BPL),
    ESI(SIL),
    EDI(DIL),
    R8D(R8B),
    R9D(R9B),
    R10D(R10B),
    R11D(R11B),
    R12D(R12B),
    R13D(R13B),
    R14D(R14B),
    R15D(R15B),
}

enum class X86Lower8BitRegisters : X86Register {
    AL,
    BL,
    CL,
    DL,
    SPL,
    BPL,
    SIL,
    DIL,
    R8B,
    R9B,
    R10B,
    R11B,
    R12B,
    R13B,
    R14B,
    R15B;

    override fun toString(): String = name.lowercase()
}