# Compiler Design

This project contains my compiler for the [Compiler Design](https://symbolaris.com/course/compiler.html) labs at KIT in
the summer term of 2025.

My code is originally based on
the [Java starter code](https://github.com/LS-Lab/Compilers-course-code-template/tree/java)
provided by the course.
However, I have rewritten nearly everything from scratch in switching to Kotlin.
Only the code for the [Lexer](./src/main/kotlin/edu/kit/kastel/vads/compiler/lexer/Lexer.kt) is still mostly a 1:1
translation from the Java version.

## Compiled Language

The language we compile is a simple, C-like language.
It gained more features with each new lab ([L1](https://logic.kastel.kit.edu/teaching/compiler/Lab1.pdf), [L2](https://logic.kastel.kit.edu/teaching/compiler/Lab2.pdf), [L3](https://logic.kastel.kit.edu/teaching/compiler/Lab3.pdf), [L4](https://logic.kastel.kit.edu/teaching/compiler/Lab4.pdf)).

The core features include:

- Integer variables and arithmetic
- Control flow (if, while)
- Functions (with parameters and return values)
- Arrays
- Structs
- Pointers
- Dynamic memory allocation

## Compiler Overview

The compiler implements lexing, parsing, semantic analysis, SSA translation and code generation.
The only direct dependency it uses is [Clikt](https://ajalt.github.io/clikt/) for command-line parsing.
Additionally, it uses [gcc](https://gcc.gnu.org/) on Linux and [nasm](https://www.nasm.us/) & [clang](https://clang.llvm.org/) on macOS for assembling and linking the generated code.

### Lexing & Parsing

The compiler uses a handwritten [lexer](./src/main/kotlin/edu/kit/kastel/vads/compiler/lexer/Lexer.kt) and recursive-descent [parser](./src/main/kotlin/edu/kit/kastel/vads/compiler/parser/Parser.kt).

### Semantic Analysis

The semantic analysis checks for various semantic errors in the AST.
It checks the following:

- `MainFunctionAnalysis`: The program contains a `main` function with the correct signature.
- `FunctionDefinitionAnalysis`: No function is defined more than once.
- `FunctionCallAnalysis`: All function calls refer to existing functions and have the correct number of arguments.
- `ReturnAnalysis`: All paths in a function with a non-void return type return a value.
- `BreakAndContinueWithinLoopAnalysis`: `break` and `continue` statements are only used within loops.
- `IntegerLiteralRangeAnalysis`: All integer literals are within the range of 32-bit unsigned integers.
- `NoDeclarationInForIncrementAnalysis`: No variable declarations are allowed in the increment part of a for loop.
- `NoDuplicateStructsAnalysis`: No struct is defined more than once.
- `NoRecursiveStructsAnalysis`: Structs cannot be recursive (i.e., a struct cannot contain itself directly or indirectly).
- `NoDuplicateFieldsInStructsAnalysis`: No struct can contain multiple fields with the same name.
- `NoLargeTypesAsVariablesAnalysis`: Variables cannot have large types (i.e., arrays or structs) as their type.
- `VariableStatusAnalysis`: All variables are declared before they are used, and no variable is declared more than once in the same scope.
- `TypeChecking`: All expressions have the correct types (e.g., you cannot add an integer and a pointer, or call a non-function).

### IR

The IR is in SSA form and inspired by [libFirm](https://libfirm.github.io/) and [Sea of Nodes](https://github.com/SeaOfNodes/).
Currently, no optimizations are implemented, but the SSA translation already applies some optimizations (e.g., constant propagation and dead code elimination).
This IR is then used lowered to the AsmIR, which is a simple, abstract assembly language.

### Code Generation

The code generation takes the AsmIR and generates assembly code.
Currently, only x86-64 assembly is supported, but the code generation is structured in a way that it should be easy to add support for other architectures (e.g., ARM).
For register allocation a graph coloring approach is used.

## Compiler Options

The compiler supports a few options for debugging and testing purposes.
These options can be passed as command-line arguments or set via environment variables.

| Option             | Env Variable     | Description                                                  |
|--------------------|------------------|--------------------------------------------------------------|
| `--print-ast`      | `PRINT_AST`      | Print the AST to stdout.                                     |
| `--print-ir`       | `PRINT_IR`       | Print the IR to a `graph.dot` file in the working directory. |
| `--overwrite-ir`   | `OVERWRITE_IR`   | Overwrite the IR file if it already exists.                  |
| `--print-assembly` | `PRINT_ASSEMBLY` | Print the generated assembly.                                |

