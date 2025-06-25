package edu.kit.kastel.vads.compiler.backend.ir

interface AsmIr {
    sealed interface Instruction

    sealed interface Operand
    data class Register(val id: Int) : Operand {
        override fun toString(): String = "R$id"
    }
    data class Immediate(val value: UInt) : Operand
    data class Label(val name: String)

    class BinaryOperation(val operation: BinaryOperationType, val destination: Register, val leftSource: Operand, val rightSource: Operand) : Instruction

    class UnaryOperation(val operation: UnaryOperationType, val destination: Register, val source: Operand) : Instruction

    class Move(val destination: Register, val source: Operand) : Instruction

    class Jump(val target: Label) : Instruction

    class ConditionalJump(val condition: Register, val target: Label) : Instruction

    class Return(val value: Operand) : Instruction

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

    data class Function(val name: String, val blocks: List<BasicBlock>, val startBlock: BasicBlock, val returnBlock: BasicBlock)

    data class Program(val functions: List<Function>)
}