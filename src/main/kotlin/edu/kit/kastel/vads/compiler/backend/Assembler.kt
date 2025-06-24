package edu.kit.kastel.vads.compiler.backend

import java.nio.file.Path

interface Assembler<T : Architecture> {
    fun assemble(assembly: Assembly<T>, path: Path)
}