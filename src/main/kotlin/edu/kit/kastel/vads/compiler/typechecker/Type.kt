package edu.kit.kastel.vads.compiler.typechecker

import edu.kit.kastel.vads.compiler.lexer.Token
import edu.kit.kastel.vads.compiler.parser.SymbolName

sealed interface Type {
    fun asString(): String
    val kind: Kind
    fun isLargeType(): Boolean = kind.isLargeType()

    enum class Kind {
        INT, BOOL, POINTER, STRUCT_REFERENCE, ARRAY, STRUCT, FUNCTION;

        fun isLargeType(): Boolean = when (this) {
            FUNCTION, STRUCT_REFERENCE, STRUCT -> true
            INT, BOOL, POINTER, ARRAY -> false
        }
    }

    object IntType : Type {
        override fun asString(): String {
            return "int"
        }

        override val kind: Kind = Kind.INT
    }

    object BoolType : Type {
        override fun asString(): String {
            return "bool"
        }

        override val kind: Kind = Kind.BOOL
    }

    object NullType : Type {
        override fun asString(): String {
            return "null"
        }

        override val kind: Kind = Kind.POINTER
    }

    data class PointerType(val elementType: Type) : Type {
        override fun asString(): String {
            return "${elementType.asString()}*"
        }

        override val kind: Kind = Kind.POINTER
    }

    data class StructReferenceType(val structName: SymbolName) : Type {
        override fun asString(): String {
            return "struct $structName"
        }

        override val kind: Kind = Kind.STRUCT_REFERENCE
    }

    data class ArrayType(val elementType: Type) : Type {
        override fun asString(): String {
            return "${elementType.asString()}[]"
        }

        override val kind: Kind = Kind.ARRAY
    }

    data class StructType(val name: SymbolName, val fields: LinkedHashMap<SymbolName, Type>) : Type {
        override fun asString(): String {
            return "struct $name { ${fields.entries.joinToString(", ") { "${it.key}: ${it.value.asString()}" }} }"
        }

        override val kind: Kind = Kind.STRUCT
    }

    data class FunctionType(val returnType: Type, val parameterTypes: List<Type>) : Type {
        override fun asString(): String {
            return "(${parameterTypes.joinToString(", ")}) -> ${returnType.asString()}"
        }

        override val kind: Kind = Kind.FUNCTION
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