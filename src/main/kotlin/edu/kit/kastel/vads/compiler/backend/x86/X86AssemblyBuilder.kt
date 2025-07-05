package edu.kit.kastel.vads.compiler.backend.x86

import edu.kit.kastel.vads.compiler.backend.*

typealias Destination = Location<X86Architecture>
typealias Source = Operand<X86Architecture>

private val INDENT = " ".repeat(2)

enum class SymbolType {
    FUNCTION
    ;

    override fun toString(): String = "@${name.lowercase()}"
}

class X86AssemblyBuilder {

    private val prefixBuilder = StringBuilder()
    private val builder: StringBuilder = StringBuilder()

    init {
        prefix()
        builder.appendLine(".text")
    }

    fun generateAssembly(): X86Assembly {
        val assembly = prefixBuilder.append(builder).toString()
        return X86Assembly(assembly)
    }

    fun mov(destination: Destination, source: Source) = generateInstruction("mov", destination, source)
    fun movzx(destination: Destination, source: Source) = generateInstruction("movzx", destination, source)
    fun add(destination: Destination, source: Source) = generateInstruction("add", destination, source)
    fun sub(destination: Destination, source: Source) = generateInstruction("sub", destination, source)
    fun imul(destination: Destination, source: Source) = generateInstruction("imul", destination, source)
    fun idiv(source: Source) = generateInstruction("idiv", source)
    fun cdq() = generateInstruction("cdq")
    fun test(source1: Source, source2: Source) = generateInstruction("test", source1, source2)
    fun cmp(source1: Source, source2: Source) = generateInstruction("cmp", source1, source2)
    fun setz(destination: Destination) = generateInstruction("setz", destination)
    fun sete(destination: Destination) = generateInstruction("sete", destination)
    fun setne(destination: Destination) = generateInstruction("setne", destination)
    fun setl(destination: Destination) = generateInstruction("setl", destination)
    fun setle(destination: Destination) = generateInstruction("setle", destination)
    fun setg(destination: Destination) = generateInstruction("setg", destination)
    fun setge(destination: Destination) = generateInstruction("setge", destination)
    fun neg(destination: Destination) = generateInstruction("neg", destination)
    fun not(destination: Destination) = generateInstruction("not", destination)
    fun and(destination: Destination, source: Source) = generateInstruction("and", destination, source)
    fun or(destination: Destination, source: Source) = generateInstruction("or", destination, source)
    fun xor(destination: Destination, source: Source) = generateInstruction("xor", destination, source)
    fun sal(destination: Destination, source: Source) = generateInstruction("sal", destination, source)
    fun sar(destination: Destination, source: Source) = generateInstruction("sar", destination, source)
    fun jmp(label: String) = generateInstruction("jmp", X86Label(label))
    fun je(label: String) = generateInstruction("je", X86Label(label))
    fun jne(label: String) = generateInstruction("jne", X86Label(label))
    fun jl(label: String) = generateInstruction("jl", X86Label(label))
    fun jle(label: String) = generateInstruction("jle", X86Label(label))
    fun jg(label: String) = generateInstruction("jg", X86Label(label))
    fun jge(label: String) = generateInstruction("jge", X86Label(label))
    fun enter(numberOfStackVariables: X86Immediate) = generateInstruction("enter", numberOfStackVariables, X86Immediate(0u))
    fun leave() = generateInstruction("leave")
    fun ret() = generateInstruction("ret")
    fun call(name: String) = builder.appendLine("${INDENT}call $name")
    fun push(source: Source) {
        sub(X8664BitRegisters.RSP, X86Immediate(4u))
        builder.appendLine("${INDENT}mov DWORD [${X8664BitRegisters.RSP.string()}], ${source.string()}")
    }

    fun createFunction(name: String, numberOfStackVariables: Int, block: X86AssemblyBuilder.() -> Unit) {
        global(name, SymbolType.FUNCTION)
        enter((numberOfStackVariables * 4).toImmediate())
        block()
        builder.appendLine("# End of function $name")
        builder.appendLine()
    }

    fun label(name: String) {
        builder.appendLine()
        builder.appendLine(".$name:")
    }

    fun global(name: String, type: SymbolType) {
        builder.appendLine(".global $name")
        builder.appendLine(".type $name $type")
        builder.appendLine("$name:")
    }

    fun extern(name: String) {
        builder.appendLine(".extern $name")
    }

    private fun generateInstruction(instruction: String, vararg operands: Operand<X86Architecture>) {
        builder.appendLine("$INDENT$instruction ${operands.joinToString(", ") { it.string() }}")
    }

    private fun prefix() {
        prefixBuilder.appendLine(".intel_syntax noprefix")
        prefixBuilder.appendLine()
    }
}

private fun Operand<X86Architecture>.string(): String = when (this) {
    is SpillLocation<X86Architecture> -> "DWORD PTR [${X8664BitRegisters.RBP} - ${4 + (this as X86SpillLocation).index * 4}]"
    is ArgumentLocation<X86Architecture> -> "DWORD PTR [${X8664BitRegisters.RBP} + ${16 + (this as X86ArgumentLocation).index * 4}]"
    is Register<X86Architecture> -> name.lowercase()
    is Immediate<X86Architecture> -> value.toString()
    is Label<X86Architecture> -> ".$name"
    is StackLocation<*> -> error("not used on x86")
}

private fun Int.toImmediate() = X86Immediate(this.toUInt())