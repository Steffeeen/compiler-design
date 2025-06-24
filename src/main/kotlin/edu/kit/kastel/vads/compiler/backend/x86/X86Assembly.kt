package edu.kit.kastel.vads.compiler.backend.x86

import edu.kit.kastel.vads.compiler.backend.Assembly
import edu.kit.kastel.vads.compiler.backend.X86Architecture

data class X86Assembly(val assemblyCode: String) : Assembly<X86Architecture>