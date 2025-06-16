package edu.kit.kastel.vads.compiler.backend.ir

sealed interface AsmOperand

data class AsmRegister(val id: Int) : AsmOperand
data class AsmImmediate(val value: UInt) : AsmOperand
data class AsmLabel(val name: String) : AsmOperand

interface AsmIr {
    sealed interface Instruction

    data class BinaryOperation(val operation: BinaryOperationType, val destination: AsmRegister, val leftSource: AsmOperand, val rightSource: AsmOperand) : Instruction

    data class UnaryOperation(val operation: UnaryOperationType, val destination: AsmRegister, val source: AsmOperand) : Instruction

    data class Jump(val target: AsmLabel) : Instruction

    data class ConditionalJump(val condition: AsmRegister, val target: AsmLabel) : Instruction

    data class Return(val value: AsmOperand) : Instruction

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

    data class BasicBlock(val label: AsmLabel, val instructions: List<Instruction>)

    data class Function(val name: String, val blocks: List<BasicBlock>, val returnBlock: BasicBlock)

    data class Program(val functions: List<Function>)
}