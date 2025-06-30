package edu.kit.kastel.vads.compiler.backend.x86

import edu.kit.kastel.vads.compiler.backend.*
import edu.kit.kastel.vads.compiler.backend.ir.AsmIr
import edu.kit.kastel.vads.compiler.backend.ir.AsmIr.BinaryOperationType.*
import edu.kit.kastel.vads.compiler.backend.ir.AsmIr.UnaryOperationType.*

class X86CodeGenerator : CodeGenerator<X86Architecture> {
    private val builder = X86AssemblyBuilder()

    override fun generateCode(asmProgram: AsmIr.Program, registerAllocations: Map<AsmIr.Function, RegisterAllocation<X86Architecture>>): Assembly<X86Architecture> {
        builder.extern("putchar")
        builder.extern("getchar")
        builder.extern("fflush")
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

        val numberOfStackVariables = registerAllocation.numberOfStackVariables + X86Architecture.getCallerSavedRegisters().size
        createFunction(name, numberOfStackVariables) {
            jmp("${name}_start")

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
                with(registerAllocation[instruction]) {
                    mov(X86Registers.EAX, instruction.value.toX86Operand())
                    leave()
                    ret()
                }
            }

            is AsmIr.Call -> generateCall(instruction)
            is AsmIr.CallBuiltin -> generateBuiltinCall(instruction)
        }
    }

    context(registerAllocation: RegisterAllocation<X86Architecture>)
    private fun generateBinaryOperation(instruction: AsmIr.BinaryOperation) = with(builder) {
        val (destination, leftSource, rightSource) = with(registerAllocation[instruction]) {
            val destination = instruction.destination.toX86Register()
            val leftSource = instruction.leftSource.toX86Operand()
            val rightSource = instruction.rightSource.toX86Operand()
            Triple(destination, leftSource, rightSource)
        }

        val isCommutative = instruction.operation.isCommutative
        val source = when {
            isCommutative && rightSource == destination -> leftSource
            destination == leftSource -> rightSource
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

            EQUAL, NOT_EQUAL, LESS_THAN, LESS_THAN_OR_EQUAL, GREATER_THAN, GREATER_THAN_OR_EQUAL -> generateCompare(instruction.operation, destination, rightSource)
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
        val (destination, source) = with(registerAllocation[instruction]) {
            instruction.destination.toX86Register() to instruction.source.toX86Operand()
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
        val register = with(registerAllocation[instruction]) {
            instruction.condition.toX86Register()
        }
        test(register, register)
        jne("${functionName}_${instruction.target.name}")
    }

    context(registerAllocation: RegisterAllocation<X86Architecture>)
    private fun generateMove(instruction: AsmIr.Move) = with(builder) {
        val (destination, source) = with(registerAllocation[instruction]) {
            instruction.destination.toX86Register() to instruction.source.toX86Operand()
        }

        mov(destination, source)
    }

    context(registerAllocation: RegisterAllocation<X86Architecture>)
    private fun generateCall(instruction: AsmIr.Call) = with(builder) {
        val (destination, arguments) = with(registerAllocation[instruction]) {
            instruction.destination?.toX86Register() to instruction.arguments.map { it.toX86Operand() }
        }

        generateCallImpl(instruction.functionName, destination, arguments, registerAllocation.numberOfStackVariables)
    }

    context(registerAllocation: RegisterAllocation<X86Architecture>)
    private fun generateBuiltinCall(instruction: AsmIr.CallBuiltin) = with(builder) {
        val (destination, argumentsFromProgram) = with(registerAllocation[instruction]) {
            instruction.destination?.toX86Register() to instruction.arguments.map { it.toX86Operand() }
        }

        val name = when (instruction) {
            is AsmIr.CallPrint -> "putchar"
            is AsmIr.CallRead -> "getchar"
            is AsmIr.CallFlush -> "fflush"
        }

        val arguments = if (instruction is AsmIr.CallFlush) {
            // add stdout as argument for fflush
            listOf(X86Immediate(0u))
        } else {
            argumentsFromProgram
        }

        generateCallImpl(name, destination, arguments, registerAllocation.numberOfStackVariables)

        if (destination != null) {
            // adjust return values according to spec
            when (instruction) {
                is AsmIr.CallPrint, is AsmIr.CallFlush -> mov(destination, X86Immediate(0u)) // print and flush always returns 0
                is AsmIr.CallRead -> {} // read already returns the value or -1 if EOF
            }
        }
    }

    private fun generateCallImpl(name: String, destination: Register<X86Architecture>?, arguments: List<Operand<X86Architecture>>, numberOfStackVariables: Int) = with(builder) {
        val baseIndex = numberOfStackVariables
        for ((index, register) in X86Architecture.getCallerSavedRegisters().withIndex()) {
            mov(X86StackRegister(baseIndex + index), register)
        }

        val numberOfArgumentRegisters = X86Architecture.getArgumentRegisters().size
        val argumentsInRegisters = arguments.take(numberOfArgumentRegisters)
        val argumentsOnStack = arguments.drop(numberOfArgumentRegisters)

        for ((index, argument) in argumentsInRegisters.withIndex()) {
            mov(X86Architecture.getArgumentRegisters()[index], argument)
        }

        for (argument in argumentsOnStack.reversed()) {
            push(argument)
        }

        // align the stack to 16 bytes if necessary (only 32-bit values)
        val misalignment = (argumentsOnStack.size * 4) % 16
        if (misalignment != 0) {
            sub(X86Registers.RSP, X86Immediate(16u - misalignment.toUInt()))
        }

        call(name)

        if (destination != null) {
            mov(destination, X86Registers.EAX)
        }

        for ((index, register) in X86Architecture.getCallerSavedRegisters().withIndex()) {
            mov(register, X86StackRegister(baseIndex + index))
        }
    }

    context(registerToInformation: Map<AsmIr.Register, AllocationInformation<X86Architecture>>)
    private fun AsmIr.Operand.toX86Operand(): Operand<X86Architecture> {
        return when (this) {
            is AsmIr.Register -> {
                val allocationInformation = registerToInformation[this]!!
                when (allocationInformation) {
                    is AllocationInformation.NormalRegister<X86Architecture> -> allocationInformation.register
                    is AllocationInformation.Spill<X86Architecture> -> spill(allocationInformation.register, allocationInformation.spillLocation)
                    is AllocationInformation.Reload<X86Architecture> -> reload(allocationInformation.reloadLocation, allocationInformation.register)
                    is AllocationInformation.SpillAndReload<X86Architecture> -> {
                        spill(allocationInformation.register, allocationInformation.spillLocation)
                        reload(allocationInformation.reloadLocation, allocationInformation.register)
                    }
                }
            }
            is AsmIr.Immediate -> X86Immediate(value)
        }
    }

    context(registerToInformation: Map<AsmIr.Register, AllocationInformation<X86Architecture>>)
    private fun AsmIr.Register.toX86Register(): Register<X86Architecture> {
        val register = this.toX86Operand()
        require(register is Register<X86Architecture>)
        return register
    }

    private fun spill(register: Register<X86Architecture>, spillLocation: Location<X86Architecture>): Operand<X86Architecture> {
        builder.mov(spillLocation, register)
        return register
    }

    private fun reload(reloadLocation: Location<X86Architecture>, register: Register<X86Architecture>): Operand<X86Architecture> {
        builder.mov(register, reloadLocation)
        return register
    }
}