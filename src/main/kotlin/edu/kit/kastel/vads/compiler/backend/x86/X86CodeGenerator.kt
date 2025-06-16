package edu.kit.kastel.vads.compiler.backend.x86

import edu.kit.kastel.vads.compiler.backend.*
import edu.kit.kastel.vads.compiler.backend.ir.AsmImmediate
import edu.kit.kastel.vads.compiler.backend.ir.AsmIr
import edu.kit.kastel.vads.compiler.backend.ir.AsmIr.*
import edu.kit.kastel.vads.compiler.backend.ir.AsmIr.BinaryOperationType.*
import edu.kit.kastel.vads.compiler.backend.ir.AsmIr.UnaryOperationType.*
import edu.kit.kastel.vads.compiler.backend.ir.AsmOperand
import edu.kit.kastel.vads.compiler.backend.ir.AsmRegister

class X86CodeGenerator : CodeGenerator<X86Architecture> {
    private val builder = X86AssemblyBuilder()

    override fun generateCode(asmProgram: Program, registerAllocations: Map<AsmIr.Function, RegisterAllocation<X86Architecture>>): Assembly<X86Architecture> {
        builder.global("main", SymbolType.FUNCTION)
        builder.call("mainimpl")
        builder.ret()

        for (function in asmProgram.functions) {
            with(registerAllocations[function]!!) {
                generateFunction(function)
            }
        }

        return builder.generateAssembly()
    }

    context(registerAllocation: RegisterAllocation<X86Architecture>)
    private fun generateFunction(function: AsmIr.Function) = with(builder) {
        val name = if (function.name == "main") {
            "mainimpl"
        } else {
            function.name
        }

        createFunction(name, registerAllocation.numberOfStackVariables) {
            jmp("mainimpl_start")

            with(name) {
                for (block in function.blocks) {
                    generateBasicBlock(block)
                }
            }
        }
    }

    context(registerAllocation: RegisterAllocation<X86Architecture>, functionName: String)
    private fun generateBasicBlock(block: BasicBlock) = with(builder) {
        label("${functionName}_${block.label.name}")
        for (instruction in block.instructions) {
            generateInstruction(instruction)
        }
    }

    context(registerAllocation: RegisterAllocation<X86Architecture>, functionName: String)
    private fun generateInstruction(instruction: Instruction) = with(builder) {
        when (instruction) {
            is BinaryOperation -> generateBinaryOperation(instruction)
            is UnaryOperation -> generateUnaryOperation(instruction)
            is ConditionalJump -> generateConditionalJump(instruction)
            is Jump -> jmp("${functionName}_${instruction.target.name}")
            is Return -> {
                mov(X86Registers.EAX, instruction.value.toX86Operand())
                leave()
                ret()
            }
        }
    }

    context(registerAllocation: RegisterAllocation<X86Architecture>)
    private fun generateBinaryOperation(instruction: BinaryOperation) = with(builder) {
        val destination = registerAllocation[instruction.destination]
        val leftSource = instruction.leftSource.toX86Operand()
        val rightSource = instruction.rightSource.toX86Operand()

        val isCommutative = instruction.operation.isCommutative

        val source = when {
            !isCommutative -> {
                if (destination != leftSource) {
                    mov(destination, leftSource)
                }
                rightSource
            }

            destination == leftSource -> rightSource
            destination == rightSource -> leftSource
            else -> {
                mov(destination, leftSource)
                rightSource
            }
        }

        when (instruction.operation) {
            ADD -> add(destination, source)
            SUBTRACT -> sub(destination, source)
            MULTIPLY -> imul(destination, source)
            DIVIDE, MODULO -> generateDivideOrModulo(instruction.operation, destination, source)
            SHIFT_LEFT -> sal(destination, source)
            SHIFT_RIGHT -> sar(destination, source)
            BITWISE_AND -> and(destination, source)
            BITWISE_OR -> or(destination, source)
            BITWISE_XOR -> xor(destination, source)

            EQUAL, NOT_EQUAL, LESS_THAN, LESS_THAN_OR_EQUAL, GREATER_THAN, GREATER_THAN_OR_EQUAL -> generateCompare(instruction.operation, destination, source)
        }
    }

    private fun generateCompare(operationType: BinaryOperationType, destination: Destination, source: Source) = with(builder) {
        require(destination is X86Registers)

        cmp(destination, source)

        when (operationType) {
            EQUAL -> sete(destination.lower8BitRegister)
            NOT_EQUAL -> setne(destination.lower8BitRegister)
            LESS_THAN -> setl(destination.lower8BitRegister)
            LESS_THAN_OR_EQUAL -> setle(destination.lower8BitRegister)
            GREATER_THAN -> setg(destination.lower8BitRegister)
            GREATER_THAN_OR_EQUAL -> setge(destination.lower8BitRegister)
            else -> error("Unsupported operation type: $operationType")
        }

        movzx(destination, destination.lower8BitRegister)
    }

    private fun generateDivideOrModulo(operationType: BinaryOperationType, destination: Destination, source: Source) = with(builder) {
        require(operationType == DIVIDE || operationType == MODULO)

        idiv(source)

        when (operationType) {
            DIVIDE -> mov(destination, X86Registers.EAX)
            MODULO -> mov(destination, X86Registers.EDX)
            else -> error("Unsupported operation type: $operationType")
        }
    }

    context(registerAllocation: RegisterAllocation<X86Architecture>)
    private fun generateUnaryOperation(instruction: UnaryOperation) = with(builder) {
        val destination = registerAllocation[instruction.destination]
        val source = instruction.source.toX86Operand()

        if (destination != source) {
            mov(destination, source)
        }

        when (instruction.operation) {
            NEGATE -> neg(destination)
            BITWISE_NOT -> not(destination)
            LOGICAL_NOT -> {
                // this is ensured by the constraint generator
                require(destination is X86Registers)
                test(destination, destination)
                setz(destination.lower8BitRegister)
                movzx(destination, destination.lower8BitRegister)
            }
        }
    }

    context(registerAllocation: RegisterAllocation<X86Architecture>, functionName: String)
    private fun generateConditionalJump(instruction: ConditionalJump) = with(builder) {
        val register = registerAllocation[instruction.condition]
        test(register, register)
        jne("${functionName}_${instruction.target.name}")
    }

    context(registerAllocation: RegisterAllocation<X86Architecture>)
    private fun AsmOperand.toX86Operand(): Operand<X86Architecture> {
        return when (this) {
            is AsmRegister -> registerAllocation[this]
            is AsmImmediate -> X86Immediate(value)
            else -> error("Unsupported operand type: $this")
        }
    }
}