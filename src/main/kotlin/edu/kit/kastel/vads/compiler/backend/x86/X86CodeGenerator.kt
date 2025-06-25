package edu.kit.kastel.vads.compiler.backend.x86

import edu.kit.kastel.vads.compiler.backend.*
import edu.kit.kastel.vads.compiler.backend.ir.AsmIr
import edu.kit.kastel.vads.compiler.backend.ir.AsmIr.BinaryOperationType.*
import edu.kit.kastel.vads.compiler.backend.ir.AsmIr.UnaryOperationType.*

class X86CodeGenerator : CodeGenerator<X86Architecture> {
    private val builder = X86AssemblyBuilder()

    override fun generateCode(asmProgram: AsmIr.Program, registerAllocations: Map<AsmIr.Function, RegisterAllocation<X86Architecture>>): Assembly<X86Architecture> {
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
    private fun generateBasicBlock(block: AsmIr.BasicBlock) = with(builder) {
        label("${functionName}_${block.label.name}")
        for (instruction in block.instructions) {
            generateInstruction(instruction)
        }
    }

    context(registerAllocation: RegisterAllocation<X86Architecture>, functionName: String)
    private fun generateInstruction(instruction: AsmIr.Instruction) = with(builder) {
        when (instruction) {
            is AsmIr.BinaryOperation -> generateBinaryOperation(instruction)
            is AsmIr.UnaryOperation -> generateUnaryOperation(instruction)
            is AsmIr.ConditionalJump -> generateConditionalJump(instruction)
            is AsmIr.Jump -> jmp("${functionName}_${instruction.target.name}")
            is AsmIr.Move -> generateMove(instruction)
            is AsmIr.Return -> {
                mov(X86Registers.EAX, instruction.value.toX86Operand())
                leave()
                ret()
            }
        }
    }

    context(registerAllocation: RegisterAllocation<X86Architecture>)
    private fun generateBinaryOperation(instruction: AsmIr.BinaryOperation) = with(builder) {
        val allocatedDestination = registerAllocation[instruction.destination]
        val leftSource = instruction.leftSource.toX86Operand()
        val rightSource = instruction.rightSource.toX86Operand()

        val isCommutative = instruction.operation.isCommutative

        val source = when {
            allocatedDestination == leftSource -> rightSource // The ideal case, the register allocator put the left source in the destination register, so we can just use the right operand as the source
            isCommutative && allocatedDestination == rightSource -> leftSource // If the operation is commutative, we can use the left operand as the source
            allocatedDestination == rightSource -> {
                // If the destination also happens to be the right operand, we need to move the right operand into a temporary register to avoid overwriting it by putting the left operand in the destination
                mov(X86Architecture.TEMP_REGISTER, rightSource)
                if (allocatedDestination is StackLocation<X86Architecture> && leftSource is StackLocation<X86Architecture>) {
                    // FIXME using eax here isn't really optimal, but it works for now as it is excluded from the register allocation
                    // If both source and destination are stack locations, we need to use a temporary register
                    mov(X86Registers.EAX, leftSource)
                    mov(allocatedDestination, X86Registers.EAX)
                } else {
                    // Otherwise, we can directly move the left operand into the destination register
                    mov(allocatedDestination, leftSource)
                }
                X86Architecture.TEMP_REGISTER
            }

            else -> {
                // The default case, as we are translating from a three-address IR, we move the left operand into the destination register
                if (allocatedDestination is StackLocation<X86Architecture> && leftSource is StackLocation<X86Architecture>) {
                    // If both source and destination are stack locations, we need to use a temporary register
                    mov(X86Architecture.TEMP_REGISTER, leftSource)
                    mov(allocatedDestination, X86Architecture.TEMP_REGISTER)
                } else {
                    // Otherwise, we can directly move the left operand into the destination register
                    mov(allocatedDestination, leftSource)
                }
                rightSource
            }
        }

        val destination = when {
            allocatedDestination is StackLocation<X86Architecture> && source is StackLocation<X86Architecture> -> {
                // If both source and destination are stack locations, we need to use a temporary register
                mov(X86Architecture.TEMP_REGISTER, allocatedDestination)
                X86Architecture.TEMP_REGISTER
            }

            instruction.operation == MULTIPLY && allocatedDestination is StackLocation<X86Architecture> -> {
                // FIXME: again using eax here isn't really optimal, but it works for now as it is excluded from the register allocation
                mov(X86Registers.EAX, allocatedDestination)
                X86Registers.EAX
            }

            else -> {
                allocatedDestination
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

        if (destination != allocatedDestination) {
            // If we used a temporary register, we need to move the result back to the allocated destination
            mov(allocatedDestination, destination)
        }
    }

    private fun generateCompare(operationType: AsmIr.BinaryOperationType, destination: Destination, source: Source) = with(builder) {
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

    private fun generateDivideOrModulo(operationType: AsmIr.BinaryOperationType, destination: Destination, source: Source) = with(builder) {
        require(operationType == DIVIDE || operationType == MODULO)

        val divSource = if (source is Immediate<X86Architecture>) {
            mov(X86Architecture.TEMP_REGISTER, source)
            X86Architecture.TEMP_REGISTER
        } else {
            source
        }

        mov(X86Registers.EAX, destination)
        cdq()
        idiv(divSource)

        when (operationType) {
            DIVIDE -> mov(destination, X86Registers.EAX)
            MODULO -> mov(destination, X86Registers.EDX)
            else -> error("Unsupported operation type: $operationType")
        }
    }

    context(registerAllocation: RegisterAllocation<X86Architecture>)
    private fun generateUnaryOperation(instruction: AsmIr.UnaryOperation) = with(builder) {
        val (source, destination) = when {
            instruction.destination.toX86Operand() is StackLocation<X86Architecture> && instruction.source.toX86Operand() is StackLocation<X86Architecture> -> {
                // If both source and destination are stack locations, we need to use a temporary register
                mov(X86Architecture.TEMP_REGISTER, instruction.destination.toX86Location())
                Pair(instruction.source.toX86Operand(), X86Architecture.TEMP_REGISTER)
            }

            else -> Pair(instruction.source.toX86Operand(), instruction.destination.toX86Location())
        }

        if (destination != source) {
            mov(destination, source)
        }

        when (instruction.operation) {
            NEGATE -> neg(destination)
            BITWISE_NOT -> not(destination)
            LOGICAL_NOT -> {
                require(destination is X86Registers)
                test(destination, destination)
                setz(destination.lower8BitRegister)
                movzx(destination, destination.lower8BitRegister)
            }
        }
    }

    context(registerAllocation: RegisterAllocation<X86Architecture>, functionName: String)
    private fun generateConditionalJump(instruction: AsmIr.ConditionalJump) = with(builder) {
        val register = registerAllocation[instruction.condition]
        test(register, register)
        jne("${functionName}_${instruction.target.name}")
    }

    context(registerAllocation: RegisterAllocation<X86Architecture>)
    private fun generateMove(instruction: AsmIr.Move) = with(builder) {
        val destination = instruction.destination.toX86Location()
        val source = instruction.source.toX86Operand()

        if (destination is StackLocation<X86Architecture> && source is StackLocation<X86Architecture>) {
            // If both source and destination are stack locations, we need to use a temporary register
            mov(X86Architecture.TEMP_REGISTER, source)
            mov(destination, X86Architecture.TEMP_REGISTER)
            return
        }

        mov(destination, source)
    }

    context(registerAllocation: RegisterAllocation<X86Architecture>)
    private fun AsmIr.Register.toX86Location() = registerAllocation[this]

    context(registerAllocation: RegisterAllocation<X86Architecture>)
    private fun AsmIr.Operand.toX86Operand(): Operand<X86Architecture> {
        return when (this) {
            is AsmIr.Register -> registerAllocation[this]
            is AsmIr.Immediate -> X86Immediate(value)
        }
    }
}