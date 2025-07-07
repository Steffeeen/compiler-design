package edu.kit.kastel.vads.compiler.typechecker

import edu.kit.kastel.vads.compiler.lexer.Token
import edu.kit.kastel.vads.compiler.parser.SymbolName

sealed interface Type {
    fun asString(): String

    object IntType : Type {
        override fun asString(): String {
            return "int"
        }
    }

    object BoolType : Type {
        override fun asString(): String {
            return "bool"
        }
    }

    data class PointerType(val elementType: Type) : Type {
        override fun asString(): String {
            return "${elementType.asString()}*"
        }
    }

    data class StructReferenceType(val structName: SymbolName) : Type {
        override fun asString(): String {
            return "struct $structName"
        }
    }

    data class ArrayType(val elementType: Type) : Type {
        override fun asString(): String {
            return "${elementType.asString()}[]"
        }
    }

    data class FunctionType(val returnType: Type, val parameterTypes: List<Type>) : Type {
        override fun asString(): String {
            return "(${parameterTypes.joinToString(", ")}) -> ${returnType.asString()}"
        }
    }

    companion object {
        val PRINT_FUNCTION = FunctionType(IntType, listOf(IntType))
        val READ_FUNCTION = FunctionType(IntType, emptyList())
        val FLUSH_FUNCTION = FunctionType(IntType, emptyList())

        fun getTypeForBuiltinFunction(keywordType: Token.KeywordType.BuiltinFunctionType): FunctionType = when (keywordType) {
            Token.KeywordType.PRINT -> PRINT_FUNCTION
            Token.KeywordType.READ -> READ_FUNCTION
            Token.KeywordType.FLUSH -> FLUSH_FUNCTION
            Token.KeywordType.ALLOC -> TODO()
            Token.KeywordType.ALLOC_ARRAY -> TODO()
        }
    }
}