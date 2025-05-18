package edu.kit.kastel.vads.compiler.backend

interface Register

sealed interface X86Register : Register

data class X86StackRegister(val index: Int) : X86Register {
    // * 4 as c0 is 32 bit
    override fun toString(): String = "[${X86Registers.RSP} + ${index * 4}"
}

enum class X86Registers : X86Register {
    EAX,
    EBX,
    ECX,
    EDX,
    RSP,
    RBP,
    ESI,
    EDI,
    R8D,
    R9D,
    R10D,
    R11D,
    R12D,
    R13D,
    R14D,
    R15D;

    override fun toString(): String = name.lowercase()
}
