package edu.kit.kastel.vads.compiler.parser.visitor

import edu.kit.kastel.vads.compiler.parser.AstNode.*

interface Visitor<T, R> {
    fun visit(assignmentNode: AssignmentNode, data: T): R
    fun visit(binaryOperationNode: BinaryOperationNode, data: T): R
    fun visit(blockNode: BlockNode, data: T): R
    fun visit(declarationNode: DeclarationNode, data: T): R
    fun visit(functionNode: FunctionNode, data: T): R
    fun visit(identifierExpressionNode: IdentifierExpressionNode, data: T): R
    fun visit(intLiteralNode: IntLiteralNode, data: T): R
    fun visit(booleanLiteralNode: BooleanLiteralNode, data: T): R
    fun visit(lValueIdentifierNode: LValueIdentifierNode, data: T): R
    fun visit(nameNode: NameNode, data: T): R
    fun visit(unaryOperationNode: UnaryOperationNode, data: T): R
    fun visit(programNode: ProgramNode, data: T): R
    fun visit(returnNode: ReturnNode, data: T): R
    fun visit(typeNode: TypeNode, data: T): R
    fun visit(ifNode: IfNode, data: T): R
    fun visit(whileNode: WhileNode, data: T): R
    fun visit(forNode: ForNode, data: T): R
    fun visit(breakNode: BreakNode, data: T): R
    fun visit(continueNode: ContinueNode, data: T): R
    fun visit(ternaryOperationNode: TernaryOperationNode, data: T): R
    fun visit(callNormalNode: CallNormalNode, data: T): R
    fun visit(callBuiltinNode: CallBuiltinNode, data: T): R
    fun visit(parameterNode: ParameterNode, data: T): R
}