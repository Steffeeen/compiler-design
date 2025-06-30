package edu.kit.kastel.vads.compiler.backend.ir

interface AsmIr {
    sealed interface Instruction {
        fun usedRegisters(): Set<Register>
    }

    sealed interface Operand
    data class Register(val id: Int) : Operand {
        override fun toString(): String = "R$id"
    }
    data class Immediate(val value: UInt) : Operand
    data class Label(val name: String)

    class BinaryOperation(val operation: BinaryOperationType, val destination: Register, val leftSource: Operand, val rightSource: Operand) : Instruction {
        override fun usedRegisters(): Set<Register> = setOfNotNull(destination, leftSource as? Register, rightSource as? Register)
    }

    class UnaryOperation(val operation: UnaryOperationType, val destination: Register, val source: Operand) : Instruction {
        override fun usedRegisters(): Set<Register> = setOfNotNull(destination, source as? Register)
    }

    class Move(val destination: Register, val source: Operand) : Instruction {
        override fun usedRegisters(): Set<Register> = setOfNotNull(destination, source as? Register)
    }

    class Call(val functionName: String, val arguments: List<Operand>, val destination: Register? = null) : Instruction {
        override fun usedRegisters(): Set<Register> = setOfNotNull(destination) + arguments.mapNotNull { it as? Register }
    }

    sealed interface CallBuiltin : Instruction {
        val arguments: List<Operand>
        val destination: Register?
        override fun usedRegisters(): Set<Register> = setOfNotNull(destination) + arguments.mapNotNull { it as? Register }
    }

    class CallPrint(val argument: Operand, override val destination: Register?) : CallBuiltin {
        override val arguments: List<Operand> = listOf(argument)
    }

    class CallRead(override val destination: Register?) : CallBuiltin {
        override val arguments: List<Operand> = listOf()
    }

    class CallFlush(override val destination: Register?) : CallBuiltin {
        override val arguments: List<Operand> = listOf()
    }

    class Jump(val target: Label) : Instruction {
        override fun usedRegisters(): Set<Register> = emptySet()
    }

    class ConditionalJump(val condition: Register, val target: Label) : Instruction {
        override fun usedRegisters(): Set<Register> = setOf(condition)
    }

    class Return(val value: Operand) : Instruction {
        override fun usedRegisters(): Set<Register> = setOfNotNull(value as? Register)
    }

    enum class BinaryOperationType(val isCommutative: Boolean) {
        ADD(true),
        SUBTRACT(false),
        MULTIPLY(true),
        DIVIDE(false),
        MODULO(false),
        BITWISE_AND(true),
        BITWISE_OR(true),
        BITWISE_XOR(true),
        SHIFT_LEFT(false),
        SHIFT_RIGHT(false),
        EQUAL(true),
        NOT_EQUAL(true),
        LESS_THAN(false),
        LESS_THAN_OR_EQUAL(false),
        GREATER_THAN(false),
        GREATER_THAN_OR_EQUAL(false)
    }

    enum class UnaryOperationType {
        NEGATE, LOGICAL_NOT, BITWISE_NOT
    }

    data class BasicBlock(val label: Label, val instructions: List<Instruction>)

    data class Function(val name: String, val parameters: List<Register>, val blocks: List<BasicBlock>, val startBlock: BasicBlock, val returnBlock: BasicBlock)

    data class Program(val functions: List<Function>)
}