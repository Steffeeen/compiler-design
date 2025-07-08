package edu.kit.kastel.vads.compiler.backend

sealed interface Operand<T : Architecture<T>>
sealed interface Location<T : Architecture<T>> : Operand<T>
interface Immediate<T : Architecture<T>> : Operand<T> {
    val value: UInt
}

interface Register<T : Architecture<T>> : Location<T> {
    val name: String
}

interface StackLocation<T : Architecture<T>> : Location<T>

interface SpillLocation<T : Architecture<T>> : StackLocation<T> {
    val index: Int
}

interface ArgumentLocation<T : Architecture<T>> : StackLocation<T> {
    val index: Int
}

interface Label<T : Architecture<T>> : Operand<T> {
    val name: String
}