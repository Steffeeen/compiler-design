package edu.kit.kastel.vads.compiler.backend.x86

import edu.kit.kastel.vads.compiler.backend.*
import edu.kit.kastel.vads.compiler.backend.x86.X8664BitRegisters.*
import edu.kit.kastel.vads.compiler.backend.x86.X86Lower8BitRegisters.*

sealed interface X86Register : Register<X86Architecture>

data class X86SpillLocation(override val index: Int) : SpillLocation<X86Architecture>

data class X86ArgumentLocation(override val index: Int) : ArgumentLocation<X86Architecture>

data class X86Immediate(override val value: UInt) : Immediate<X86Architecture>

data class X86Label(override val name: String) : Label<X86Architecture>

enum class X86Registers(val lower8BitRegister: X86Lower8BitRegisters, val x8664BitRegister: X8664BitRegisters) : X86Register {
    EAX(AL, RAX),
    EBX(BL, RBX),
    ECX(CL, RCX),
    EDX(DL, RDX),
    ESP(SPL, RSP),
    EBP(BPL, RBP),
    ESI(SIL, RSI),
    EDI(DIL, RDI),
    R8D(R8B, R8),
    R9D(R9B, R9),
    R10D(R10B, R10),
    R11D(R11B, R11),
    R12D(R12B, R12),
    R13D(R13B, R13),
    R14D(R14B, R14),
    R15D(R15B, R15),
}

enum class X8664BitRegisters : X86Register {
    RAX,
    RBX,
    RCX,
    RDX,
    RSP,
    RBP,
    RSI,
    RDI,
    R8,
    R9,
    R10,
    R11,
    R12,
    R13,
    R14,
    R15;

    override fun toString(): String = name.lowercase()
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