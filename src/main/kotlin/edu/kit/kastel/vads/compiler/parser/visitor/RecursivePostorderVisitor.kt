package edu.kit.kastel.vads.compiler.parser.visitor

import edu.kit.kastel.vads.compiler.parser.AstNode
import edu.kit.kastel.vads.compiler.parser.AstNode.*

/** A visitor that traverses a tree in postorder
 * @param <T> a type for additional data
 * @param <R> a type for a return type
</R></T> */
open class RecursivePostorderVisitor<T, R>(private val visitor: Visitor<T, R>) : Visitor<T, R> {
    override fun visit(assignmentNode: AssignmentNode, data: T): R = visitInOrderAndAccumulate(assignmentNode, data)
    override fun visit(binaryOperationNode: BinaryOperationNode, data: T): R = visitInOrderAndAccumulate(binaryOperationNode, data)
    override fun visit(blockNode: BlockNode, data: T): R = visitInOrderAndAccumulate(blockNode, data)
    override fun visit(declarationNode: DeclarationNode, data: T): R = visitInOrderAndAccumulate(declarationNode, data)
    override fun visit(functionNode: FunctionNode, data: T): R = visitInOrderAndAccumulate(functionNode, data)
    override fun visit(identifierExpressionNode: IdentifierExpressionNode, data: T): R = visitInOrderAndAccumulate(identifierExpressionNode, data)
    override fun visit(intLiteralNode: IntLiteralNode, data: T): R = visitInOrderAndAccumulate(intLiteralNode, data)
    override fun visit(booleanLiteralNode: BooleanLiteralNode, data: T): R = visitInOrderAndAccumulate(booleanLiteralNode, data)
    override fun visit(lValueIdentifierNode: LValueIdentifierNode, data: T): R = visitInOrderAndAccumulate(lValueIdentifierNode, data)
    override fun visit(nameNode: NameNode, data: T): R = visitInOrderAndAccumulate(nameNode, data)
    override fun visit(unaryOperationNode: UnaryOperationNode, data: T): R = visitInOrderAndAccumulate(unaryOperationNode, data)
    override fun visit(programNode: ProgramNode, data: T): R = visitInOrderAndAccumulate(programNode, data)
    override fun visit(returnNode: ReturnNode, data: T): R = visitInOrderAndAccumulate(returnNode, data)
    override fun visit(typeNode: TypeNode, data: T): R = visitInOrderAndAccumulate(typeNode, data)
    override fun visit(ifNode: IfNode, data: T): R = visitInOrderAndAccumulate(ifNode, data)
    override fun visit(whileNode: WhileNode, data: T): R = visitInOrderAndAccumulate(whileNode, data)
    override fun visit(forNode: ForNode, data: T): R = visitInOrderAndAccumulate(forNode, data)
    override fun visit(breakNode: BreakNode, data: T): R = visitInOrderAndAccumulate(breakNode, data)
    override fun visit(continueNode: ContinueNode, data: T): R = visitInOrderAndAccumulate(continueNode, data)
    override fun visit(ternaryOperationNode: TernaryOperationNode, data: T): R = visitInOrderAndAccumulate(ternaryOperationNode, data)
    override fun visit(callNormalNode: CallNormalNode, data: T): R = visitInOrderAndAccumulate(callNormalNode, data)
    override fun visit(callBuiltinNode: CallBuiltinNode, data: T): R = visitInOrderAndAccumulate(callBuiltinNode, data)
    override fun visit(parameterNode: ParameterNode, data: T): R = visitInOrderAndAccumulate(parameterNode, data)

    private fun visitInOrderAndAccumulate(currentNode: AstNode, data: T): R {
        val nodes = currentNode.children
        if (nodes.isEmpty()) {
            return visitHelper(currentNode, data)
        }
        var result = nodes.first().accept<T, R>(this, data)

        for (node in nodes.drop(1)) {
            result = node.accept(this, accumulate(data, result))
        }

        return visitHelper(currentNode, data)
    }

    private fun visitHelper(node: AstNode, data: T): R {
        val accumulatedData = data
        return when (node) {
            is AssignmentNode -> visitor.visit(node, accumulatedData)
            is BinaryOperationNode -> visitor.visit(node, accumulatedData)
            is BlockNode -> visitor.visit(node, accumulatedData)
            is DeclarationNode -> visitor.visit(node, accumulatedData)
            is FunctionNode -> visitor.visit(node, accumulatedData)
            is IdentifierExpressionNode -> visitor.visit(node, accumulatedData)
            is IntLiteralNode -> visitor.visit(node, accumulatedData)
            is BooleanLiteralNode -> visitor.visit(node, accumulatedData)
            is LValueIdentifierNode -> visitor.visit(node, accumulatedData)
            is NameNode -> visitor.visit(node, accumulatedData)
            is UnaryOperationNode -> visitor.visit(node, accumulatedData)
            is ProgramNode -> visitor.visit(node, accumulatedData)
            is ReturnNode -> visitor.visit(node, accumulatedData)
            is TypeNode -> visitor.visit(node, accumulatedData)
            is IfNode -> visitor.visit(node, accumulatedData)
            is WhileNode -> visitor.visit(node, accumulatedData)
            is ForNode -> visitor.visit(node, accumulatedData)
            is BreakNode -> visitor.visit(node, accumulatedData)
            is ContinueNode -> visitor.visit(node, accumulatedData)
            is TernaryOperationNode -> visitor.visit(node, accumulatedData)
            is CallNormalNode -> visitor.visit(node, accumulatedData)
            is CallBuiltinNode -> visitor.visit(node, accumulatedData)
            is ParameterNode -> visitor.visit(node, accumulatedData)
        }
    }

    protected fun accumulate(data: T, @Suppress("unused") value: R): T {
        return data
    }
}