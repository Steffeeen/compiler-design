package edu.kit.kastel.vads.compiler.typechecker

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
}