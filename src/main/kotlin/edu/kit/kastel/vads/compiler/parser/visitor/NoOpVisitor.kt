package edu.kit.kastel.vads.compiler.parser.visitor

import edu.kit.kastel.vads.compiler.parser.AstNode.*

/** A visitor that does nothing and returns [Unit#INSTANCE] by default.
 * This can be used to implement operations only for specific tree types. */
interface NoOpVisitor<T> : Visitor<T, Unit> {
    override fun visit(assignmentNode: AssignmentNode, data: T) {}
    override fun visit(binaryOperationNode: BinaryOperationNode, data: T) {}
    override fun visit(blockNode: BlockNode, data: T) {}
    override fun visit(declarationNode: DeclarationNode, data: T) {}
    override fun visit(functionNode: FunctionNode, data: T) {}
    override fun visit(identifierExpressionNode: IdentifierExpressionNode, data: T) {}
    override fun visit(intLiteralNode: IntLiteralNode, data: T) {}
    override fun visit(booleanLiteralNode: BooleanLiteralNode, data: T) {}
    override fun visit(lValueIdentifierNode: LValueIdentifierNode, data: T) {}
    override fun visit(nameNode: NameNode, data: T) {}
    override fun visit(unaryOperationNode: UnaryOperationNode, data: T) {}
    override fun visit(programNode: ProgramNode, data: T) {}
    override fun visit(returnNode: ReturnNode, data: T) {}
    override fun visit(typeNode: TypeNode, data: T) {}
    override fun visit(ifNode: IfNode, data: T) {}
    override fun visit(whileNode: WhileNode, data: T) {}
    override fun visit(forNode: ForNode, data: T) {}
    override fun visit(breakNode: BreakNode, data: T) {}
    override fun visit(continueNode: ContinueNode, data: T) {}
    override fun visit(ternaryOperationNode: TernaryOperationNode, data: T) {}
    override fun visit(callNormalNode: CallNormalNode, data: T) {}
    override fun visit(callBuiltinNode: CallBuiltinNode, data: T) {}
    override fun visit(parameterNode: ParameterNode, data: T) {}
    override fun visit(nullLiteralNode: NullLiteralNode, data: T) {}
    override fun visit(pointerDereferenceNode: PointerDereferenceNode, data: T) {}
    override fun visit(fieldAccessNode: FieldAccessNode, data: T) {}
    override fun visit(fieldDereferenceNode: FieldDereferenceNode, data: T) {}
    override fun visit(lValuePointerDereferenceNode: LValuePointerDereferenceNode, data: T) {}
    override fun visit(lValueFieldAccessNode: LValueFieldAccessNode, data: T) {}
    override fun visit(lValueFieldDereferenceNode: LValueFieldDereferenceNode, data: T) {}
    override fun visit(lValueArrayAccessNode: LValueArrayAccessNode, data: T) {}
    override fun visit(structDeclarationNode: StructDeclarationNode, data: T) {}
    override fun visit(structFieldDeclarationNode: StructFieldDeclarationNode, data: T) {}
    override fun visit(callAllocNode: CallAllocNode, data: T) {}
    override fun visit(callAllocArrayNode: CallAllocArrayNode, data: T) {}
    override fun visit(arrayAccessNode: ArrayAccessNode, data: T) {}
}
