package edu.kit.kastel.vads.compiler.backend

import edu.kit.kastel.vads.compiler.backend.ir.AsmIr

sealed interface EvaluationResult {
    data class Success(val value: Int) : EvaluationResult
    data class DivByZero(val instruction: AsmIr.Instruction) : EvaluationResult, Exception()
}

fun AsmIr.Program.evaluate(input: List<Char>): EvaluationResult = evaluateProgram(this, input)
fun evaluateProgram(program: AsmIr.Program, input: List<Char>): EvaluationResult {
    return AsmIrEvaluator(program, input).evaluate()
}

private class AsmIrEvaluator(private val program: AsmIr.Program, input: List<Char>) {

    private val remainingInput = input.toMutableList()

    fun evaluate(): EvaluationResult {
        val mainFunction = program.functions.firstOrNull { it.name == "main" }!!
        return try {
            val result = evaluateFunction(mainFunction, listOf())
            EvaluationResult.Success(result.toUByte().toInt()) // Return value is an unsigned byte
        } catch (e: EvaluationResult.DivByZero) {
            e
        }
    }

    fun evaluateFunction(functionName: String, parameterValues: List<Int>): Int {
        val function = program.functions.firstOrNull { it.name == functionName }!!
        return evaluateFunction(function, parameterValues)
    }

    private fun evaluateFunction(function: AsmIr.Function, parameterValues: List<Int>): Int {
        val registerValues = mutableMapOf<AsmIr.Register, Int>()
        for ((index, register) in function.parameters.withIndex()) {
            registerValues[register] = parameterValues[index]
        }

        val labelToBlock = function.blocks.associateBy { it.label.name }
        var currentBlock = function.startBlock
        var instructionIndex = 0

        while (true) {
            if (instructionIndex >= currentBlock.instructions.size) {
                // If we run out of instructions, jump to return block if not already there
                if (currentBlock == function.returnBlock) break
                currentBlock = function.returnBlock
                instructionIndex = 0
                continue
            }
            val instruction = currentBlock.instructions[instructionIndex]
            when (instruction) {
                is AsmIr.BinaryOperation -> {
                    val left = evalOperand(instruction.leftSource, registerValues)
                    val right = evalOperand(instruction.rightSource, registerValues)
                    val result = when (instruction.operation) {
                        AsmIr.BinaryOperationType.ADD -> left + right
                        AsmIr.BinaryOperationType.SUBTRACT -> left - right
                        AsmIr.BinaryOperationType.MULTIPLY -> left * right
                        AsmIr.BinaryOperationType.DIVIDE -> {
                            if (right == 0) throw EvaluationResult.DivByZero(instruction)
                            left / right
                        }

                        AsmIr.BinaryOperationType.MODULO -> {
                            if (right == 0) throw EvaluationResult.DivByZero(instruction)
                            left % right
                        }

                        AsmIr.BinaryOperationType.BITWISE_AND -> left and right
                        AsmIr.BinaryOperationType.BITWISE_OR -> left or right
                        AsmIr.BinaryOperationType.BITWISE_XOR -> left xor right
                        AsmIr.BinaryOperationType.SHIFT_LEFT -> left shl right
                        AsmIr.BinaryOperationType.SHIFT_RIGHT -> left shr right
                        AsmIr.BinaryOperationType.EQUAL -> if (left == right) 1 else 0
                        AsmIr.BinaryOperationType.NOT_EQUAL -> if (left != right) 1 else 0
                        AsmIr.BinaryOperationType.LESS_THAN -> if (left < right) 1 else 0
                        AsmIr.BinaryOperationType.LESS_THAN_OR_EQUAL -> if (left <= right) 1 else 0
                        AsmIr.BinaryOperationType.GREATER_THAN -> if (left > right) 1 else 0
                        AsmIr.BinaryOperationType.GREATER_THAN_OR_EQUAL -> if (left >= right) 1 else 0
                    }
                    registerValues[instruction.destination] = result
                    instructionIndex++
                }

                is AsmIr.UnaryOperation -> {
                    val src = evalOperand(instruction.source, registerValues)
                    val result = when (instruction.operation) {
                        AsmIr.UnaryOperationType.NEGATE -> -src
                        AsmIr.UnaryOperationType.LOGICAL_NOT -> if (src == 0) 1 else 0
                        AsmIr.UnaryOperationType.BITWISE_NOT -> src.inv()
                    }
                    registerValues[instruction.destination] = result
                    instructionIndex++
                }

                is AsmIr.Move -> {
                    val src = evalOperand(instruction.source, registerValues)
                    registerValues[instruction.destination] = src
                    instructionIndex++
                }

                is AsmIr.Call -> {
                    val args = instruction.arguments.map { evalOperand(it, registerValues) }
                    val ret = evaluateFunction(instruction.functionName, args)
                    instruction.destination?.let { registerValues[it] = ret }
                    instructionIndex++
                }

                is AsmIr.CallPrint -> {
                    // No-op for evaluator, always return 0 if destination is set
                    instruction.destination?.let { registerValues[it] = 0 }
                    instructionIndex++
                }

                is AsmIr.CallRead -> {
                    val char = remainingInput.removeFirstOrNull()?.code ?: -1
                    instruction.destination?.let { registerValues[it] = char.toInt() }
                    instructionIndex++
                }

                is AsmIr.CallFlush -> {
                    // No-op, always return 0 if destination is set
                    instruction.destination?.let { registerValues[it] = 0 }
                    instructionIndex++
                }

                is AsmIr.ConditionalJump -> {
                    val cond = registerValues[instruction.condition] ?: 0
                    if (cond != 0) {
                        currentBlock = labelToBlock[instruction.target.name]
                            ?: throw IllegalStateException("Unknown label: ${instruction.target.name}")
                        instructionIndex = 0
                    } else {
                        instructionIndex++
                    }
                }

                is AsmIr.Jump -> {
                    currentBlock = labelToBlock[instruction.target.name]
                        ?: throw IllegalStateException("Unknown label: ${instruction.target.name}")
                    instructionIndex = 0
                }

                is AsmIr.Return -> {
                    return evalOperand(instruction.value, registerValues)
                }
            }
        }
        throw IllegalStateException("Function did not return a value")
    }

    private fun evalOperand(op: AsmIr.Operand, registerValues: Map<AsmIr.Register, Int>): Int =
        when (op) {
            is AsmIr.Register -> registerValues[op]!!
            is AsmIr.Immediate -> op.value.toInt()
        }
}
