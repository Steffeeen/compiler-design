package edu.kit.kastel.vads.compiler.parser

import edu.kit.kastel.vads.compiler.Span
import edu.kit.kastel.vads.compiler.parser.AstNode.*

private const val INDENT = 2

fun printAst(ast: AstNode): String {
    return printNode(ast, 0)
}

private fun printNode(node: AstNode, depth: Int): String {
    return when (node) {
        is ProgramNode -> printProgramNode(node, depth)
        is FunctionNode -> printFunctionNode(node, depth)
        is BinaryOperationNode -> printBinaryOperationNode(node, depth)
        is IdentifierExpressionNode -> printIdentifierExpressionNode(node, depth)
        is LiteralNode -> printLiteralNode(node, depth)
        is UnaryOperationNode -> printUnaryOperationNode(node, depth)
        is LValueIdentifierNode -> printLValueIdentifierNode(node, depth)
        is NameNode -> printNameNode(node, depth)
        is AssignmentNode -> printAssignmentNode(node, depth)
        is BlockNode -> printBlockNode(node, depth + INDENT)
        is DeclarationNode -> printDeclarationNode(node, depth)
        is ReturnNode -> printReturnNode(node, depth)
        is TypeNode -> printTypeNode(node, depth)
        is TernaryOperationNode -> printTernaryOperationNode(node, depth)
        is BreakNode -> printBreakNode(node, depth)
        is ContinueNode -> printContinueNode(node, depth)
        is ForNode -> printForNode(node, depth)
        is IfNode -> printIfNode(node, depth)
        is WhileNode -> printWhileNode(node, depth)
    }
}

private fun printProgramNode(node: ProgramNode, depth: Int): String {
    return "${printNodeNameAndSpan(node, depth)}\n${
        node.topLevelFunctions.joinToString("\n") {
            printFunctionNode(it, depth + INDENT)
        }
    }"
}

private fun printFunctionNode(node: FunctionNode, depth: Int): String {
    val line = "${printNodeNameAndSpan(node, depth)} ${node.name.name.asString()}(): ${node.returnType.type.asString()}"
    val body = printBlockNode(node.body, depth + INDENT)
    return "$line\n$body"
}

private fun printBinaryOperationNode(node: BinaryOperationNode, depth: Int): String {
    val lhs = printNode(node.left, depth + INDENT)
    val rhs = printNode(node.right, depth + INDENT)
    return "${printNodeNameAndSpan(node, depth)} ${node.operatorType.value}\n$lhs\n$rhs"
}

private fun printIdentifierExpressionNode(node: IdentifierExpressionNode, depth: Int): String {
    return "${printNodeNameAndSpan(node, depth)}\n${printNameNode(node.name, depth + INDENT)}"
}

private fun printLiteralNode(node: LiteralNode, depth: Int): String {
    val value = when (node) {
        is BooleanLiteralNode -> node.value.toString()
        is IntLiteralNode -> node.value
    }
    return "${printNodeNameAndSpan(node, depth)} $value"
}

private fun printUnaryOperationNode(node: UnaryOperationNode, depth: Int): String {
    return "${printNodeNameAndSpan(node, depth)} ${node.operator.value}\n${printNode(node.expression, depth + INDENT)}"
}

private fun printLValueIdentifierNode(node: LValueIdentifierNode, depth: Int): String {
    return "${printNodeNameAndSpan(node, depth)}\n${printNameNode(node.name, depth + INDENT)}"
}

private fun printNameNode(node: NameNode, depth: Int): String {
    return "${printNodeNameAndSpan(node, depth)} ${node.name.asString()}"
}

private fun printAssignmentNode(node: AssignmentNode, depth: Int): String {
    val lValue = printNode(node.lValue, depth + INDENT)
    val expression = printNode(node.expression, depth + INDENT)
    return "${printNodeNameAndSpan(node, depth)}\n$lValue\n$expression"
}

private fun printBlockNode(node: BlockNode, depth: Int): String {
    return "${printNodeNameAndSpan(node, depth)}\n${
        node.statements.joinToString("\n") {
            printNode(it, depth + INDENT)
        }
    }"
}

private fun printDeclarationNode(node: DeclarationNode, depth: Int): String {
    val type = printNode(node.type, depth + INDENT)
    val name = printNameNode(node.name, depth + INDENT)
    val initializer = node.initializer?.let { printNode(it, depth + INDENT) }
    return "${printNodeNameAndSpan(node, depth)}\n$type\n$name\n$initializer"
}

private fun printReturnNode(node: ReturnNode, depth: Int): String {
    val expr = printNode(node.expression, depth + INDENT)
    return "${printNodeNameAndSpan(node, depth)}\n$expr"
}

private fun printTypeNode(node: TypeNode, depth: Int): String {
    return "${printNodeNameAndSpan(node, depth)} ${node.type.asString()}"
}

private fun printTernaryOperationNode(node: TernaryOperationNode, depth: Int): String {
    val condition = printNode(node.condition, depth + INDENT)
    val trueBranch = printNode(node.trueExpression, depth + INDENT)
    val falseBranch = printNode(node.falseExpression, depth + INDENT)
    return "${printNodeNameAndSpan(node, depth)}\n$condition\n$trueBranch\n$falseBranch"
}

private fun printBreakNode(node: BreakNode, depth: Int): String {
    return printNodeNameAndSpan(node, depth)
}

private fun printContinueNode(node: ContinueNode, depth: Int): String {
    return printNodeNameAndSpan(node, depth)
}

private fun printForNode(node: ForNode, depth: Int): String {
    val init = node.initializer?.let { printNode(it, depth + INDENT) } ?: "no initializer"
    val condition = printNode(node.condition, depth + INDENT)
    val update = node.increment?.let { printNode(it, depth + INDENT) } ?: "no update"
    val body = printNode(node.body, depth + INDENT)
    return "${printNodeNameAndSpan(node, depth)}\n$init\n$condition\n$update\n$body"
}

private fun printIfNode(node: IfNode, depth: Int): String {
    val condition = printNode(node.condition, depth + INDENT)
    val thenBranch = printNode(node.body, depth + INDENT)
    val elseBranch = node.elseStatement?.let { printNode(it, depth + INDENT) } ?: "no else branch"
    return "${printNodeNameAndSpan(node, depth)}\n$condition\n$thenBranch\n$elseBranch"
}

private fun printWhileNode(node: WhileNode, depth: Int): String {
    val condition = printNode(node.condition, depth + INDENT)
    val body = printNode(node.body, depth + INDENT)
    return "${printNodeNameAndSpan(node, depth)}\n$condition\n$body"
}

private fun printNodeNameAndSpan(node: AstNode, depth: Int): String {
    return " ".repeat(depth) + "${node::class.simpleName} ${printSpan(node.span)}"
}

private fun printSpan(span: Span): String {
    return "(${span.start.line}, ${span.start.column}) - (${span.end.line}, ${span.end.column})"
}

