package edu.kit.kastel.vads.compiler.semantic

import edu.kit.kastel.vads.compiler.CompilerOptions
import edu.kit.kastel.vads.compiler.Span
import edu.kit.kastel.vads.compiler.parser.AstNode
import edu.kit.kastel.vads.compiler.parser.SymbolName
import edu.kit.kastel.vads.compiler.parser.visitor.NoOpVisitor
import edu.kit.kastel.vads.compiler.parser.visitor.RecursivePostorderVisitor
import edu.kit.kastel.vads.compiler.parser.visitor.VisitorWithoutData
import edu.kit.kastel.vads.compiler.typechecker.Type
import edu.kit.kastel.vads.compiler.typechecker.TypeChecking
import edu.kit.kastel.vads.compiler.typechecker.TypeError

sealed interface SemanticError {
    data class InvalidIntegerLiteralRange(val node: AstNode.LiteralNode) : SemanticError
    data class MissingReturnStatement(val node: AstNode.FunctionNode, val span: Span) : SemanticError
    data class VariableAlreadyExists(val node: AstNode.NameNode) : SemanticError
    data class VariableNotDeclaredBeforeAssignment(val node: AstNode.NameNode) : SemanticError
    data class VariableNotInitialized(val node: AstNode.NameNode) : SemanticError
    data class BreakNotInLoop(val node: AstNode.BreakNode) : SemanticError
    data class ContinueNotInLoop(val node: AstNode.ContinueNode) : SemanticError
    data class DeclarationInForIncrement(val node: AstNode.DeclarationNode) : SemanticError
    data class TypeErrorWrapper(val error: TypeError) : SemanticError
    object NoMainFunction : SemanticError
    object MainHasWrongSignature : SemanticError
    data class FunctionAlreadyDeclared(val node: AstNode.NameNode) : SemanticError
    data class FunctionNotDeclared(val node: AstNode.NameNode) : SemanticError
    data class FunctionCallWrongNumberOfArguments(val node: AstNode.CallNode, val expected: Int, val actual: Int) : SemanticError
    data class DuplicateStructName(val duplicate: AstNode.StructDeclarationNode, val existing: AstNode.StructDeclarationNode) : SemanticError
    data class DuplicateFieldInStruct(val duplicate: AstNode.StructFieldDeclarationNode, val existing: AstNode.StructFieldDeclarationNode) : SemanticError
    data class RecursiveStruct(val node: AstNode.StructDeclarationNode) : SemanticError
    data class LargeTypeAsVariable(val node: AstNode.DeclarationNode, val type: Type) : SemanticError
    data class LargeTypeAsParameter(val node: AstNode.ParameterNode, val type: Type) : SemanticError
}

interface SemanticAnalysis {
    fun analyze(program: AstNode.ProgramNode): List<SemanticError>
}

context(options: CompilerOptions)
fun analyzeProgram(program: AstNode.ProgramNode): List<SemanticError> {
    val analyses = listOf(
        MainFunctionAnalysis,
        FunctionDefinitionAnalysis,
        FunctionCallAnalysis,
        ReturnAnalysis,
        BreakAndContinueWithinLoopAnalysis,
        IntegerLiteralRangeAnalysis,
        NoDeclarationInForIncrementAnalysis,
        NoDuplicateStructsAnalysis,
        NoRecursiveStructsAnalysis,
        NoDuplicateFieldsInStructsAnalysis,
        NoLargeTypesAsVariablesAnalysis,
        VariableStatusAnalysis,
        TypeChecking,
    )

    for (analysis in analyses) {
        val errors = analysis.analyze(program)
        if (errors.isNotEmpty()) {
            return errors
        }
    }

    return listOf()
}

private object MainFunctionAnalysis : SemanticAnalysis {
    override fun analyze(program: AstNode.ProgramNode): List<SemanticError> {
        val mainFunction = program.topLevelFunctions.firstOrNull { it.name.name.asString() == "main" }
        if (mainFunction == null) {
            return listOf(SemanticError.NoMainFunction)
        }

        if (mainFunction.returnType.type != Type.IntType) {
            return listOf(SemanticError.MainHasWrongSignature)
        }

        if (mainFunction.parameters.isNotEmpty()) {
            return listOf(SemanticError.MainHasWrongSignature)
        }

        return listOf()
    }
}

private object FunctionDefinitionAnalysis : SemanticAnalysis {
    override fun analyze(program: AstNode.ProgramNode): List<SemanticError> {
        val errors = mutableListOf<SemanticError>()
        val functionNames = mutableSetOf<SymbolName>()

        for (function in program.topLevelFunctions) {
            if (function.name.name in functionNames) {
                errors += SemanticError.FunctionAlreadyDeclared(function.name)
            } else {
                functionNames += function.name.name
            }
        }

        return errors
    }
}

private object FunctionCallAnalysis : SemanticAnalysis {
    override fun analyze(program: AstNode.ProgramNode): List<SemanticError> {
        val errors = mutableListOf<SemanticError>()

        val functionMap = program.topLevelFunctions.associateBy { it.name.name }

        program.accept(RecursivePostorderVisitor(object : VisitorWithoutData() {
            override fun visit(callNormalNode: AstNode.CallNormalNode) {
                if (callNormalNode.name.name !in functionMap) {
                    errors += SemanticError.FunctionNotDeclared(callNormalNode.name)
                    return
                }

                val functionNode = functionMap[callNormalNode.name.name]!!
                if (callNormalNode.arguments.size != functionNode.parameters.size) {
                    errors += SemanticError.FunctionCallWrongNumberOfArguments(callNormalNode, functionNode.parameters.size, callNormalNode.arguments.size)
                }
            }

            override fun visit(callBuiltinNode: AstNode.CallBuiltinNode) {
                val type = Type.getTypeForBuiltinFunction(callBuiltinNode.keyword)
                if (callBuiltinNode.arguments.size != type.parameterTypes.size) {
                    errors += SemanticError.FunctionCallWrongNumberOfArguments(callBuiltinNode, type.parameterTypes.size, callBuiltinNode.arguments.size)
                }
            }
        }), Unit)

        return errors
    }
}

private object IntegerLiteralRangeAnalysis : SemanticAnalysis {
    override fun analyze(program: AstNode.ProgramNode): List<SemanticError> {
        val errors = mutableListOf<SemanticError>()

        val visitor = object : NoOpVisitor<Unit> {
            override fun visit(intLiteralNode: AstNode.IntLiteralNode, data: Unit) {
                if (intLiteralNode.parseValue() != null) {
                    return
                }

                errors += SemanticError.InvalidIntegerLiteralRange(intLiteralNode)
            }
        }

        program.accept(RecursivePostorderVisitor(visitor), Unit)
        return errors
    }
}

/**
 * Checks that functions return.
 * Currently only works for straight-line code.
 */
private object ReturnAnalysis : SemanticAnalysis {
    override fun analyze(program: AstNode.ProgramNode): List<SemanticError> {
        return program.topLevelFunctions.flatMap { analyzeFunction(it) }
    }

    private fun analyzeFunction(functionNode: AstNode.FunctionNode): List<SemanticError> {
        if (hasReturn(functionNode.body.statements)) {
            return listOf()
        }
        val span = functionNode.body.statements.lastOrNull()?.span
            ?: functionNode.body.span // Fallback to the body span if no statements are present
        return listOf(SemanticError.MissingReturnStatement(functionNode, span))
    }

    private fun hasReturn(statements: List<AstNode.StatementNode>): Boolean {
        for (statement in statements) {
            if (hasReturn(statement)) {
                return true
            }
        }
        return false
    }

    private fun hasReturn(statement: AstNode.StatementNode): Boolean {
        return when (statement) {
            is AstNode.ReturnNode -> true
            is AstNode.BlockNode -> hasReturn(statement.statements)
            is AstNode.IfNode -> hasReturn(statement.body) &&
                    statement.elseStatement != null && hasReturn(statement.elseStatement)

            else -> false
        }
    }
}

private object NoDeclarationInForIncrementAnalysis : SemanticAnalysis {
    override fun analyze(program: AstNode.ProgramNode): List<SemanticError> {
        val errors = mutableListOf<SemanticError>()

        program.accept(RecursivePostorderVisitor(object : NoOpVisitor<Unit> {
            override fun visit(forNode: AstNode.ForNode, data: Unit) {
                if (forNode.increment is AstNode.DeclarationNode) {
                    errors += SemanticError.DeclarationInForIncrement(forNode.increment)
                }
            }
        }), Unit)

        return errors
    }
}

private object BreakAndContinueWithinLoopAnalysis : SemanticAnalysis {
    override fun analyze(program: AstNode.ProgramNode): List<SemanticError> {
        return program.topLevelFunctions.flatMap { checkBreakNotInLoop(it.body.statements) }
    }

    private fun checkBreakNotInLoop(statements: List<AstNode>): List<SemanticError> {
        val errors = mutableListOf<SemanticError>()
        for (statement in statements) {
            when (statement) {
                is AstNode.BreakNode -> errors += SemanticError.BreakNotInLoop(statement)
                is AstNode.ContinueNode -> errors += SemanticError.ContinueNotInLoop(statement)
                is AstNode.BlockNode -> errors += checkBreakNotInLoop(statement.statements)
                is AstNode.IfNode -> {
                    errors += checkBreakNotInLoop(listOf(statement.body))
                    if (statement.elseStatement != null) {
                        errors += checkBreakNotInLoop(listOf(statement.elseStatement))
                    }
                }

                else -> {}
            }
        }
        return errors
    }
}

private object NoDuplicateStructsAnalysis : SemanticAnalysis {
    override fun analyze(program: AstNode.ProgramNode): List<SemanticError> {
        val errors = mutableListOf<SemanticError>()
        val structNames = mutableSetOf<SymbolName>()

        for (struct in program.structDeclarations) {
            if (struct.name.name in structNames) {
                val existingStruct = program.structDeclarations.first { it.name.name == struct.name.name }
                errors += SemanticError.DuplicateStructName(struct, existingStruct)
            } else {
                structNames += struct.name.name
            }
        }

        return errors
    }
}

private object NoDuplicateFieldsInStructsAnalysis : SemanticAnalysis {
    override fun analyze(program: AstNode.ProgramNode): List<SemanticError> {
        val errors = mutableListOf<SemanticError>()

        for (struct in program.structDeclarations) {
            val fieldNames = mutableSetOf<SymbolName>()
            for (field in struct.fields) {
                if (field.name.name in fieldNames) {
                    val existingField = struct.fields.first { it.name.name == field.name.name }
                    errors += SemanticError.DuplicateFieldInStruct(field, existingField)
                } else {
                    fieldNames += field.name.name
                }
            }
        }

        return errors
    }
}

private object NoRecursiveStructsAnalysis : SemanticAnalysis {
    override fun analyze(program: AstNode.ProgramNode): List<SemanticError> {
        val errors = mutableListOf<SemanticError>()

        val map = program.structDeclarations.associateBy { it.name.name }

        for (struct in program.structDeclarations) {
            if (checkRecursive(struct, map, struct.name.name)) {
                errors += SemanticError.RecursiveStruct(struct)
            }
        }

        return errors
    }

    private fun checkRecursive(struct: AstNode.StructDeclarationNode, map: Map<SymbolName, AstNode.StructDeclarationNode>, name: SymbolName): Boolean {
        for (field in struct.fields) {
            if (field.type.type is Type.StructReferenceType) {
                if (field.type.type.structName == name) {
                    return true
                }
                if (checkRecursive(map[field.type.type.structName]!!, map, name)) {
                    return true
                }
            }
        }
        return false
    }
}

private object NoLargeTypesAsVariablesAnalysis : SemanticAnalysis {
    override fun analyze(program: AstNode.ProgramNode): List<SemanticError> {
        val errors = mutableListOf<SemanticError>()

        program.accept(RecursivePostorderVisitor(object : VisitorWithoutData() {
            override fun visit(declarationNode: AstNode.DeclarationNode) {
                if (declarationNode.type.type.isLargeType()) {
                    errors += SemanticError.LargeTypeAsVariable(declarationNode, declarationNode.type.type)
                }
            }

            override fun visit(parameterNode: AstNode.ParameterNode) {
                if (parameterNode.type.type.isLargeType()) {
                    errors += SemanticError.LargeTypeAsParameter(parameterNode, parameterNode.type.type)
                }
            }
        }), Unit)

        return errors
    }
}