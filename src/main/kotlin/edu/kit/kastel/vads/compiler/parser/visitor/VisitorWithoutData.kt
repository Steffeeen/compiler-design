package edu.kit.kastel.vads.compiler.parser.visitor

import edu.kit.kastel.vads.compiler.parser.AstNode.*

abstract class VisitorWithoutData : Visitor<Unit, Unit> {
    final override fun visit(assignmentNode: AssignmentNode, data: Unit) = visit(assignmentNode)
    open fun visit(assignmentNode: AssignmentNode) {}
    final override fun visit(binaryOperationNode: BinaryOperationNode, data: Unit) = visit(binaryOperationNode)
    open fun visit(binaryOperationNode: BinaryOperationNode) {}
    final override fun visit(blockNode: BlockNode, data: Unit) = visit(blockNode)
    open fun visit(blockNode: BlockNode) {}
    final override fun visit(declarationNode: DeclarationNode, data: Unit) = visit(declarationNode)
    open fun visit(declarationNode: DeclarationNode) {}
    final override fun visit(functionNode: FunctionNode, data: Unit) = visit(functionNode)
    open fun visit(functionNode: FunctionNode) {}
    final override fun visit(identifierExpressionNode: IdentifierExpressionNode, data: Unit) = visit(identifierExpressionNode)
    open fun visit(identifierExpressionNode: IdentifierExpressionNode) {}
    final override fun visit(intLiteralNode: IntLiteralNode, data: Unit) = visit(intLiteralNode)
    open fun visit(intLiteralNode: IntLiteralNode) {}
    final override fun visit(booleanLiteralNode: BooleanLiteralNode, data: Unit) = visit(booleanLiteralNode)
    open fun visit(booleanLiteralNode: BooleanLiteralNode) {}
    final override fun visit(lValueIdentifierNode: LValueIdentifierNode, data: Unit) = visit(lValueIdentifierNode)
    open fun visit(lValueIdentifierNode: LValueIdentifierNode) {}
    final override fun visit(nameNode: NameNode, data: Unit) = visit(nameNode)
    open fun visit(nameNode: NameNode) {}
    final override fun visit(unaryOperationNode: UnaryOperationNode, data: Unit) = visit(unaryOperationNode)
    open fun visit(unaryOperationNode: UnaryOperationNode) {}
    final override fun visit(programNode: ProgramNode, data: Unit) = visit(programNode)
    open fun visit(programNode: ProgramNode) {}
    final override fun visit(returnNode: ReturnNode, data: Unit) = visit(returnNode)
    open fun visit(returnNode: ReturnNode) {}
    final override fun visit(typeNode: TypeNode, data: Unit) = visit(typeNode)
    open fun visit(typeNode: TypeNode) {}
    final override fun visit(ifNode: IfNode, data: Unit) = visit(ifNode)
    open fun visit(ifNode: IfNode) {}
    final override fun visit(whileNode: WhileNode, data: Unit) = visit(whileNode)
    open fun visit(whileNode: WhileNode) {}
    final override fun visit(forNode: ForNode, data: Unit) = visit(forNode)
    open fun visit(forNode: ForNode) {}
    final override fun visit(breakNode: BreakNode, data: Unit) = visit(breakNode)
    open fun visit(breakNode: BreakNode) {}
    final override fun visit(continueNode: ContinueNode, data: Unit) = visit(continueNode)
    open fun visit(continueNode: ContinueNode) {}
    final override fun visit(ternaryOperationNode: TernaryOperationNode, data: Unit) = visit(ternaryOperationNode)
    open fun visit(ternaryOperationNode: TernaryOperationNode) {}
    final override fun visit(callNormalNode: CallNormalNode, data: Unit) = visit(callNormalNode)
    open fun visit(callNormalNode: CallNormalNode) {}
    final override fun visit(callBuiltinNode: CallBuiltinNode, data: Unit) = visit(callBuiltinNode)
    open fun visit(callBuiltinNode: CallBuiltinNode) {}
    final override fun visit(parameterNode: ParameterNode, data: Unit) = visit(parameterNode)
    open fun visit(parameterNode: ParameterNode) {}
    final override fun visit(nullLiteralNode: NullLiteralNode, data: Unit) = visit(nullLiteralNode)
    open fun visit(nullLiteralNode: NullLiteralNode) {}
    final override fun visit(pointerDereferenceNode: PointerDereferenceNode, data: Unit) = visit(pointerDereferenceNode)
    open fun visit(pointerDereferenceNode: PointerDereferenceNode) {}
    final override fun visit(fieldAccessNode: FieldAccessNode, data: Unit) = visit(fieldAccessNode)
    open fun visit(fieldAccessNode: FieldAccessNode) {}
    final override fun visit(fieldDereferenceNode: FieldDereferenceNode, data: Unit) = visit(fieldDereferenceNode)
    open fun visit(fieldDereferenceNode: FieldDereferenceNode) {}
    final override fun visit(lValuePointerDereferenceNode: LValuePointerDereferenceNode, data: Unit) = visit(lValuePointerDereferenceNode)
    open fun visit(lValuePointerDereferenceNode: LValuePointerDereferenceNode) {}
    final override fun visit(lValueFieldAccessNode: LValueFieldAccessNode, data: Unit) = visit(lValueFieldAccessNode)
    open fun visit(lValueFieldAccessNode: LValueFieldAccessNode) {}
    final override fun visit(lValueFieldDereferenceNode: LValueFieldDereferenceNode, data: Unit) = visit(lValueFieldDereferenceNode)
    open fun visit(lValueFieldDereferenceNode: LValueFieldDereferenceNode) {}
    final override fun visit(lValueArrayAccessNode: LValueArrayAccessNode, data: Unit) = visit(lValueArrayAccessNode)
    open fun visit(lValueArrayAccessNode: LValueArrayAccessNode) {}
    final override fun visit(structDeclarationNode: StructDeclarationNode, data: Unit) = visit(structDeclarationNode)
    open fun visit(structDeclarationNode: StructDeclarationNode) {}
    final override fun visit(structFieldDeclarationNode: StructFieldDeclarationNode, data: Unit) = visit(structFieldDeclarationNode)
    open fun visit(structFieldDeclarationNode: StructFieldDeclarationNode) {}
    final override fun visit(callAllocNode: CallAllocNode, data: Unit) = visit(callAllocNode)
    open fun visit(callAllocNode: CallAllocNode) {}
    final override fun visit(callAllocArrayNode: CallAllocArrayNode, data: Unit) = visit(callAllocArrayNode)
    open fun visit(callAllocArrayNode: CallAllocArrayNode) {}
    final override fun visit(arrayAccessNode: ArrayAccessNode, data: Unit) = visit(arrayAccessNode)
    open fun visit(arrayAccessNode: ArrayAccessNode) {}
}
