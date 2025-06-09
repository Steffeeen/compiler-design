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
