package edu.kit.kastel.vads.compiler.backend

sealed interface Operand<T : Architecture>
sealed interface Location<T : Architecture> : Operand<T>
interface Immediate<T : Architecture> : Operand<T> {
    val value: UInt
}

interface Register<T : Architecture> : Location<T> {
    val name: String
}

interface StackLocation<T : Architecture> : Location<T> {
    val index: Int
}

interface Label<T : Architecture> : Operand<T> {
    val name: String
}