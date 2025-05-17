package edu.kit.kastel.vads.compiler.parser

sealed interface Type {
    fun asString(): String

    object IntType : Type {
        override fun asString(): String {
            return "int"
        }
    }
}