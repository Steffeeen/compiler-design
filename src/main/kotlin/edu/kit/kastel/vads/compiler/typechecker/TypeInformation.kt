package edu.kit.kastel.vads.compiler.typechecker

import edu.kit.kastel.vads.compiler.parser.AstNode
import edu.kit.kastel.vads.compiler.parser.SymbolName

fun createTypeInformation(programNode: AstNode.ProgramNode): TypeInformation {
    val structTypes = programNode.structDeclarations.associate { it.name.name to createStructType(it) }
    return TypeInformation(structTypes)
}

private fun createStructType(structDeclaration: AstNode.StructDeclarationNode): Type.StructType {
    val map = structDeclaration.fields.associateTo(linkedMapOf()) { it.name.name to it.type.type }
    return Type.StructType(structDeclaration.name.name, map)
}

data class TypeInformation(private val structTypes: Map<SymbolName, Type.StructType>) {
    fun structTypeForStructReferenceType(structReferenceType: Type.StructReferenceType): Type.StructType {
        return structTypes[structReferenceType.structName]!!
    }

    fun structSize(structType: Type.StructType): Int {
        val (nonStructFields, structFields) = structType.fields.toList().partition { it.second !is Type.StructReferenceType }

        val structFieldSizes = structFields.map { it.second as Type.StructReferenceType }.map { type -> structSize(structTypeForStructReferenceType(type)) }

        return nonStructFields.size * 4 + structFieldSizes.sum()
    }
}