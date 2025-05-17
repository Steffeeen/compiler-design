package edu.kit.kastel.vads.compiler.parser.visitor

import edu.kit.kastel.vads.compiler.parser.AstNode
import edu.kit.kastel.vads.compiler.parser.AstNode.*

/** A visitor that traverses a tree in postorder
 * @param <T> a type for additional data
 * @param <R> a type for a return type
</R></T> */
open class RecursivePostorderVisitor<T, R>(private val visitor: Visitor<T?, R?>) : Visitor<T?, R?> {
    override fun visit(assignmentNode: AssignmentNode, data: T?): R? {
        return visitInOrderAndAccumulate(assignmentNode, data, assignmentNode.lValue, assignmentNode.expression)
    }

    override fun visit(binaryOperationNode: BinaryOperationNode, data: T?): R? {
        return visitInOrderAndAccumulate(binaryOperationNode, data, binaryOperationNode.lhs, binaryOperationNode.rhs)
    }

    override fun visit(blockNode: BlockNode, data: T?): R? {
        return visitInOrderAndAccumulate(blockNode, data, *blockNode.statements.toTypedArray())
    }

    override fun visit(declarationNode: DeclarationNode, data: T?): R? {
        return visitInOrderAndAccumulate(
            declarationNode,
            data,
            declarationNode.type,
            declarationNode.name,
            declarationNode.initializer
        )
    }

    override fun visit(functionNode: FunctionNode, data: T?): R? {
        return visitInOrderAndAccumulate(
            functionNode,
            data,
            functionNode.returnType,
            functionNode.name,
            functionNode.body
        )
    }

    override fun visit(identifierExpressionNode: IdentifierExpressionNode, data: T?): R? {
        return visitInOrderAndAccumulate(identifierExpressionNode, data, identifierExpressionNode.name)
    }

    override fun visit(literalNode: LiteralNode, data: T?): R? {
        return visitor.visit(literalNode, data)
    }

    override fun visit(lValueIdentifierNode: LValueIdentifierNode, data: T?): R? {
        return visitInOrderAndAccumulate(lValueIdentifierNode, data, lValueIdentifierNode.name)
    }

    override fun visit(nameNode: NameNode, data: T?): R? {
        return visitor.visit(nameNode, data)
    }

    override fun visit(negateNode: NegateNode, data: T?): R? {
        return visitInOrderAndAccumulate(negateNode, data, negateNode.expression)
    }

    override fun visit(programNode: ProgramNode, data: T?): R? {
        return visitInOrderAndAccumulate(programNode, data, *programNode.topLevelFunctions.toTypedArray())
    }

    override fun visit(returnNode: ReturnNode, data: T?): R? {
        return visitInOrderAndAccumulate(returnNode, data, returnNode.expression)
    }

    override fun visit(typeNode: TypeNode, data: T?): R? {
        return visitor.visit(typeNode, data)
    }

    private fun visitInOrderAndAccumulate(
        currentNode: AstNode,
        data: T?,
        vararg nodes: AstNode?
    ): R? {
        var result = nodes.first()?.accept<T?, R?>(this, data)

        for (node in nodes.drop(1).filterNotNull()) {
            result = node.accept<T?, R?>(this, accumulate(data, result))
        }

        return visitHelper(currentNode, data, result)
    }

    private fun visitHelper(
        node: AstNode,
        data: T?,
        result: R?
    ): R? {
        val accumulatedData = accumulate(data, result)
        return when (node) {
            is AssignmentNode -> visitor.visit(node, accumulatedData)
            is BinaryOperationNode -> visitor.visit(node, accumulatedData)
            is BlockNode -> visitor.visit(node, accumulatedData)
            is DeclarationNode -> visitor.visit(node, accumulatedData)
            is FunctionNode -> visitor.visit(node, accumulatedData)
            is IdentifierExpressionNode -> visitor.visit(node, accumulatedData)
            is LiteralNode -> visitor.visit(node, accumulatedData)
            is LValueIdentifierNode -> visitor.visit(node, accumulatedData)
            is NameNode -> visitor.visit(node, accumulatedData)
            is NegateNode -> visitor.visit(node, accumulatedData)
            is ProgramNode -> visitor.visit(node, accumulatedData)
            is ReturnNode -> visitor.visit(node, accumulatedData)
            is TypeNode -> visitor.visit(node, accumulatedData)
        }
    }

    protected fun accumulate(data: T?, value: R?): T? {
        return data
    }
}
