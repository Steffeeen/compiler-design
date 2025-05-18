package edu.kit.kastel.vads.compiler.backend

import edu.kit.kastel.vads.compiler.ir.IrNode

interface RegisterAllocator<T : Register> {
    fun allocateRegisters(nodes: List<IrNode>): Map<IrNode, T>
}

class SimpleX86RegisterAllocator : RegisterAllocator<X86Register> {
    override fun allocateRegisters(nodes: List<IrNode>): Map<IrNode, X86Register> {
        val map = mutableMapOf<IrNode, X86Register>()

        val availableRegisters: MutableList<X86Register> = X86Registers.entries.toMutableList()
        availableRegisters.remove(X86Registers.RAX)
        availableRegisters.remove(X86Registers.RDX)
        availableRegisters.remove(X86Registers.RCX)
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

        return map
    }
}

private fun IrNode.needsRegister(): Boolean = this is IrNode.BinaryOperationNode || this is IrNode.NegateNode