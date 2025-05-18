package edu.kit.kastel.vads.compiler.backend

interface Register

sealed interface X86Register : Register

data class X86StackRegister(val index: Int) : X86Register {
    // * 4 as c0 is 32 bit
    override fun toString(): String = "[${X86Registers.RSP} + ${index * 4}"
}

enum class X86Registers : X86Register {
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
