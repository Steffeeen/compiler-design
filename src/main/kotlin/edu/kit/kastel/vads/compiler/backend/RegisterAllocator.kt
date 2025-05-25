package edu.kit.kastel.vads.compiler.backend

import edu.kit.kastel.vads.compiler.ir.IrNode

interface RegisterAllocation<T : Register> {
    operator fun get(node: IrNode): T?
    val numberOfStackVariables: Int
}

data class SimpleRegisterAllocation<T : Register>(private val registerMap: Map<IrNode, T>, override val numberOfStackVariables: Int) : RegisterAllocation<T> {
    override fun get(node: IrNode): T? = registerMap[node]
}

interface RegisterAllocator<T : Register> {
    fun allocateRegisters(nodes: List<IrNode>): RegisterAllocation<T>
}

class SimpleX86RegisterAllocator : RegisterAllocator<X86Register> {
    override fun allocateRegisters(nodes: List<IrNode>): RegisterAllocation<X86Register> {
        val map = mutableMapOf<IrNode, X86Register>()

        val availableRegisters: MutableList<X86Register> = X86Registers.entries.toMutableList()
        availableRegisters.remove(X86Registers.EAX)
        availableRegisters.remove(X86Registers.EDX)
        availableRegisters.remove(X86Registers.ECX)
        availableRegisters.remove(X86Registers.RSP)
        availableRegisters.remove(X86Registers.RBP)

        var stackRegisterCount = 0

        for (node in nodes) {
            if (node.needsRegister()) {
                val register = availableRegisters.removeFirstOrNull()
                if (register != null) {
                    map[node] = register
                } else {
                    map[node] = X86StackRegister(stackRegisterCount++)
                }
            }
        }

        return SimpleRegisterAllocation(map, stackRegisterCount)
    }
}

private fun IrNode.needsRegister(): Boolean = this is IrNode.BinaryOperationNode || this is IrNode.NegateNode