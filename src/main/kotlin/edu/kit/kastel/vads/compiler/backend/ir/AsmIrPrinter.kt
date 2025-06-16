package edu.kit.kastel.vads.compiler.backend.ir

private const val INDENT_SIZE = 2
private val INDENT = " ".repeat(INDENT_SIZE)

fun asmIrToString(program: AsmIr.Program): String {
    val builder = StringBuilder()

    for (function in program.functions) {
        builder.appendLine("Function: ${function.name}")
        for (block in function.blocks) {
            builder.appendLine("${INDENT}Block: ${block.label.name}")
            for (instruction in block.instructions) {
                builder.appendLine("${INDENT}${INDENT}${printInstruction(instruction)}")
            }
            builder.appendLine()
        }
        builder.appendLine()
    }

    return builder.toString()
}

private fun printInstruction(instruction: AsmIr.Instruction): String {
    return when (instruction) {
        is AsmIr.BinaryOperation -> {
            val leftSource = printOperand(instruction.leftSource)
            val rightSource = printOperand(instruction.rightSource)
            val operation = instruction.operation.stringRepresentation()
            "${printOperand(instruction.destination)} = $leftSource $operation $rightSource"
        }

        is AsmIr.UnaryOperation -> "${printOperand(instruction.destination)} = ${instruction.operation.stringRepresentation()}${printOperand(instruction.source)})"
        is AsmIr.Jump -> "Jump ${printOperand(instruction.target)}"
        is AsmIr.ConditionalJump -> "ConditionalJump ${printOperand(instruction.condition)}, ${printOperand(instruction.target)}"
        is AsmIr.Return -> "Return ${printOperand(instruction.value)}"
    }
}

private fun printOperand(operand: AsmOperand): String {
    return when (operand) {
        is AsmRegister -> "R${operand.id}"
        is AsmImmediate -> operand.value.toString()
        is AsmLabel -> operand.name
    }
}

private fun AsmIr.UnaryOperationType.stringRepresentation(): String {
    return when (this) {
        AsmIr.UnaryOperationType.NEGATE -> "-"
        AsmIr.UnaryOperationType.LOGICAL_NOT -> "!"
        AsmIr.UnaryOperationType.BITWISE_NOT -> "~"
    }
}

private fun AsmIr.BinaryOperationType.stringRepresentation(): String {
    return when (this) {
        AsmIr.BinaryOperationType.ADD -> "+"
        AsmIr.BinaryOperationType.SUBTRACT -> "-"
        AsmIr.BinaryOperationType.MULTIPLY -> "*"
        AsmIr.BinaryOperationType.DIVIDE -> "/"
        AsmIr.BinaryOperationType.MODULO -> "%"
        AsmIr.BinaryOperationType.BITWISE_AND -> "&"
        AsmIr.BinaryOperationType.BITWISE_OR -> "|"
        AsmIr.BinaryOperationType.BITWISE_XOR -> "^"
        AsmIr.BinaryOperationType.SHIFT_LEFT -> "<<"
        AsmIr.BinaryOperationType.SHIFT_RIGHT -> ">>"
        AsmIr.BinaryOperationType.EQUAL -> "=="
        AsmIr.BinaryOperationType.NOT_EQUAL -> "!="
        AsmIr.BinaryOperationType.LESS_THAN -> "<"
        AsmIr.BinaryOperationType.LESS_THAN_OR_EQUAL -> "<="
        AsmIr.BinaryOperationType.GREATER_THAN -> ">"
        AsmIr.BinaryOperationType.GREATER_THAN_OR_EQUAL -> ">="
    }
}
