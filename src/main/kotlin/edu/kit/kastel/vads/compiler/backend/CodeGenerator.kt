package edu.kit.kastel.vads.compiler.backend

import edu.kit.kastel.vads.compiler.ir.IrGraph
import edu.kit.kastel.vads.compiler.ir.IrNode

// x86 instructions
private enum class Instruction {
    MOV, ADD, SUB, IMUL, IDIV, RET, CDQ, NEG, ENTER, LEAVE, CALL;

    override fun toString(): String = name.lowercase()
}

@JvmInline
value class X86Assembly(val assembly: String)

fun generateX86Assembly(irGraphs: List<IrGraph>): X86Assembly = X86Assembly(buildString {
    prefix()

    for (irGraph in irGraphs) {
        generateFunction(irGraph)
    }

    suffix()
})

private fun IrGraph.linearize(): List<IrNode> {
    return linearizeNode(this.returnNode).reversed()
}

private fun linearizeNode(node: IrNode): List<IrNode> {
    return when (node) {
        is IrNode.DivNode, is IrNode.ModNode -> {
            if (node.left is IrNode.IntegerConstantNode && node.right is IrNode.IntegerConstantNode) {
                listOf(node)
            } else {
                listOf(node) + linearizeNode(node.left) + linearizeNode(node.right) + linearizeNode(node.sideEffect)
            }
        }
        is IrNode.BinaryOperationNode -> {
            if (node.left is IrNode.IntegerConstantNode && node.right is IrNode.IntegerConstantNode) {
                listOf(node)
            } else {
                listOf(node) + linearizeNode(node.left) + linearizeNode(node.right)
            }
        }

        is IrNode.IntegerConstantNode -> listOf(node)
        is IrNode.NegateNode -> listOf(node) + linearizeNode(node.inNode)
        IrNode.NoOpNode -> listOf()
        is IrNode.ReturnNode -> listOf(node) + linearizeNode(node.result) + linearizeNode(node.sideEffect)
        is IrNode.SideEffectProjectionNode -> linearizeNode(node.inNode)
        IrNode.StartNode -> listOf()
    }
}

private fun StringBuilder.prefix() {
    appendLine(".intel_syntax noprefix")
    appendLine(".global main")
    appendLine()
    appendLine("section .text")
    appendLine("main:")
    generateInstruction(Instruction.CALL, "mainimpl")
    generateInstruction(Instruction.RET)
    appendLine()
}

private fun StringBuilder.suffix() {}

private fun StringBuilder.generateFunctionPrefix(name: String) {
    appendLine("$name:")
}

private fun StringBuilder.generateFunction(irGraph: IrGraph) {
    // use mainimpl for the main function, the actual main function just calls mainimpl
    val name = if (irGraph.name == "main") "mainimpl" else irGraph.name
    generateFunctionPrefix(name)

    val nodes = irGraph.linearize()
    val registerAllocation = SimpleX86RegisterAllocator().allocateRegisters(nodes)

    with(registerAllocation) {
        generateInstruction(Instruction.ENTER, (numberOfStackVariables * 4).toString(), 0.toString())

        for (node in nodes) {
            generateNode(node)
        }

        generateInstruction(Instruction.LEAVE)
        generateInstruction(Instruction.RET)
    }

    appendLine()
}

context(registerAllocation: RegisterAllocation<X86Register>)
private fun StringBuilder.generateNode(node: IrNode) {
    when (node) {
        is IrNode.AddNode -> generateBinaryOperation(Instruction.ADD, node, commutative = true)
        is IrNode.SubNode -> generateBinaryOperation(Instruction.SUB, node, commutative = false)
        is IrNode.MulNode -> generateBinaryOperation(Instruction.IMUL, node, commutative = true)
        is IrNode.DivNode, is IrNode.ModNode -> generateDiv(node)
        is IrNode.NegateNode -> generateNegate(node)
        is IrNode.ReturnNode -> generateReturn(node)
        is IrNode.IntegerConstantNode -> {} // handled in the generation of the add, sub, mul, div, mod, negate nodes
        is IrNode.SideEffectProjectionNode -> {}
        IrNode.NoOpNode -> {}
        IrNode.StartNode -> {}
    }
}

context(registerAllocation: RegisterAllocation<X86Register>)
private fun StringBuilder.generateBinaryOperation(instruction: Instruction, node: IrNode.BinaryOperationNode, commutative: Boolean) {
    val register = registerAllocation[node]
    val leftRegister = registerAllocation[node.left]
    val rightRegister = registerAllocation[node.right]

    when (register) {
        leftRegister -> generateInstruction(instruction, register.toString(), node.right.valueOrRegister())
        rightRegister if commutative -> generateInstruction(instruction, register.toString(), node.left.valueOrRegister())
        !is X86Registers -> {
            generateInstruction(Instruction.MOV, X86Registers.EAX.toString(), node.left.valueOrRegister())
            generateInstruction(instruction, X86Registers.EAX.toString(), node.right.valueOrRegister())
            generateInstruction(Instruction.MOV, register.toString(), X86Registers.EAX.toString())
        }

        else -> {
            generateInstruction(Instruction.MOV, register.toString(), node.left.valueOrRegister())
            generateInstruction(instruction, register.toString(), node.right.valueOrRegister())
        }
    }
}

context(registerAllocation: RegisterAllocation<X86Register>)
private fun StringBuilder.generateDiv(node: IrNode.BinaryOperationNode) {
    require(node is IrNode.DivNode || node is IrNode.ModNode)

    generateInstruction(Instruction.MOV, X86Registers.EAX.toString(), node.left.valueOrRegister())
    generateInstruction(Instruction.CDQ)

    if (node.right is IrNode.IntegerConstantNode) {
        generateInstruction(Instruction.MOV, X86Registers.ECX.toString(), node.right.valueOrRegister())
        generateInstruction(Instruction.IDIV, X86Registers.ECX.toString())
    } else {
        generateInstruction(Instruction.IDIV, node.right.valueOrRegister())
    }

    if (node is IrNode.DivNode) {
        generateInstruction(Instruction.MOV, registerAllocation[node].toString(), X86Registers.EAX.toString())
    } else {
        generateInstruction(Instruction.MOV, registerAllocation[node].toString(), X86Registers.EDX.toString())
    }
}

context(registerAllocation: RegisterAllocation<X86Register>)
private fun StringBuilder.generateNegate(node: IrNode.NegateNode) {
    generateInstruction(Instruction.MOV, registerAllocation[node].toString(), node.inNode.valueOrRegister())
    generateInstruction(Instruction.NEG, registerAllocation[node].toString())
}

context(registerAllocation: RegisterAllocation<X86Register>)
private fun StringBuilder.generateReturn(node: IrNode.ReturnNode) {
    generateInstruction(Instruction.MOV, X86Registers.EAX.toString(), node.result.valueOrRegister())
    // ret instruction is generated by function generation
}

private fun StringBuilder.generateInstruction(instruction: Instruction, vararg operands: String) {
    appendLine("$instruction ${operands.joinToString(", ")}")
}

context(registerAllocation: RegisterAllocation<X86Register>)
private fun IrNode.valueOrRegister(): String = if (this is IrNode.IntegerConstantNode) this.value.toString() else registerAllocation[this].toString()
