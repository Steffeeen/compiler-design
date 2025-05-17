package edu.kit.kastel.vads.compiler.parser.visitor

import edu.kit.kastel.vads.compiler.parser.AstNode.*

interface Visitor<T, R> {
    fun visit(assignmentNode: AssignmentNode, data: T?): R?
    fun visit(binaryOperationNode: BinaryOperationNode, data: T?): R?
    fun visit(blockNode: BlockNode, data: T?): R?
    fun visit(declarationNode: DeclarationNode, data: T?): R?
    fun visit(functionNode: FunctionNode, data: T?): R?
    fun visit(identifierExpressionNode: IdentifierExpressionNode, data: T?): R?
    fun visit(literalNode: LiteralNode, data: T?): R?
    fun visit(lValueIdentifierNode: LValueIdentifierNode, data: T?): R?
    fun visit(nameNode: NameNode, data: T?): R?
    fun visit(negateNode: NegateNode, data: T?): R?
    fun visit(programNode: ProgramNode, data: T?): R?
    fun visit(returnNode: ReturnNode, data: T?): R?
    fun visit(typeNode: TypeNode, data: T?): R?
}
