package edu.kit.kastel.vads.compiler.parser.visitor

import edu.kit.kastel.vads.compiler.parser.AstNode
import edu.kit.kastel.vads.compiler.parser.AstNode.*

abstract class VisitorWithParents : Visitor<Unit, Unit> {
    private val parents = mutableListOf<AstNode>()

    private fun withParent(node: AstNode, block: () -> Unit) {
        parents.addLast(node)
        block()
        parents.removeLast()
    }

    final override fun visit(assignmentNode: AssignmentNode, data: Unit) = withParent(assignmentNode) { visit(assignmentNode, parents) }
    open fun visit(assignmentNode: AssignmentNode, parents: List<AstNode>) {}
    final override fun visit(binaryOperationNode: BinaryOperationNode, data: Unit) = withParent(binaryOperationNode) { visit(binaryOperationNode, parents) }
    open fun visit(binaryOperationNode: BinaryOperationNode, parents: List<AstNode>) {}
    final override fun visit(blockNode: BlockNode, data: Unit) = withParent(blockNode) { visit(blockNode, parents) }
    open fun visit(blockNode: BlockNode, parents: List<AstNode>) {}
    final override fun visit(declarationNode: DeclarationNode, data: Unit) = withParent(declarationNode) { visit(declarationNode, parents) }
    open fun visit(declarationNode: DeclarationNode, parents: List<AstNode>) {}
    final override fun visit(functionNode: FunctionNode, data: Unit) = withParent(functionNode) { visit(functionNode, parents) }
    open fun visit(functionNode: FunctionNode, parents: List<AstNode>) {}
    final override fun visit(identifierExpressionNode: IdentifierExpressionNode, data: Unit) = withParent(identifierExpressionNode) { visit(identifierExpressionNode, parents) }
    open fun visit(identifierExpressionNode: IdentifierExpressionNode, parents: List<AstNode>) {}
    final override fun visit(intLiteralNode: IntLiteralNode, data: Unit) = withParent(intLiteralNode) { visit(intLiteralNode, parents) }
    open fun visit(intLiteralNode: IntLiteralNode, parents: List<AstNode>) {}
    final override fun visit(booleanLiteralNode: BooleanLiteralNode, data: Unit) = withParent(booleanLiteralNode) { visit(booleanLiteralNode, parents) }
    open fun visit(booleanLiteralNode: BooleanLiteralNode, parents: List<AstNode>) {}
    final override fun visit(lValueIdentifierNode: LValueIdentifierNode, data: Unit) = withParent(lValueIdentifierNode) { visit(lValueIdentifierNode, parents) }
    open fun visit(lValueIdentifierNode: LValueIdentifierNode, parents: List<AstNode>) {}
    final override fun visit(nameNode: NameNode, data: Unit) = withParent(nameNode) { visit(nameNode, parents) }
    open fun visit(nameNode: NameNode, parents: List<AstNode>) {}
    final override fun visit(unaryOperationNode: UnaryOperationNode, data: Unit) = withParent(unaryOperationNode) { visit(unaryOperationNode, parents) }
    open fun visit(unaryOperationNode: UnaryOperationNode, parents: List<AstNode>) {}
    final override fun visit(programNode: ProgramNode, data: Unit) = withParent(programNode) { visit(programNode, parents) }
    open fun visit(programNode: ProgramNode, parents: List<AstNode>) {}
    final override fun visit(returnNode: ReturnNode, data: Unit) = withParent(returnNode) { visit(returnNode, parents) }
    open fun visit(returnNode: ReturnNode, parents: List<AstNode>) {}
    final override fun visit(typeNode: TypeNode, data: Unit) = withParent(typeNode) { visit(typeNode, parents) }
    open fun visit(typeNode: TypeNode, parents: List<AstNode>) {}
    final override fun visit(ifNode: IfNode, data: Unit) = withParent(ifNode) { visit(ifNode, parents) }
    open fun visit(ifNode: IfNode, parents: List<AstNode>) {}
    final override fun visit(whileNode: WhileNode, data: Unit) = withParent(whileNode) { visit(whileNode, parents) }
    open fun visit(whileNode: WhileNode, parents: List<AstNode>) {}
    final override fun visit(forNode: ForNode, data: Unit) = withParent(forNode) { visit(forNode, parents) }
    open fun visit(forNode: ForNode, parents: List<AstNode>) {}
    final override fun visit(breakNode: BreakNode, data: Unit) = withParent(breakNode) { visit(breakNode, parents) }
    open fun visit(breakNode: BreakNode, parents: List<AstNode>) {}
    final override fun visit(continueNode: ContinueNode, data: Unit) = withParent(continueNode) { visit(continueNode, parents) }
    open fun visit(continueNode: ContinueNode, parents: List<AstNode>) {}
    final override fun visit(ternaryOperationNode: TernaryOperationNode, data: Unit) = withParent(ternaryOperationNode) { visit(ternaryOperationNode, parents) }
    open fun visit(ternaryOperationNode: TernaryOperationNode, parents: List<AstNode>) {}
    final override fun visit(callNormalNode: CallNormalNode, data: Unit) = withParent(callNormalNode) { visit(callNormalNode, parents) }
    open fun visit(callNormalNode: CallNormalNode, parents: List<AstNode>) {}
    final override fun visit(callBuiltinNode: CallBuiltinNode, data: Unit) = withParent(callBuiltinNode) { visit(callBuiltinNode, parents) }
    open fun visit(callBuiltinNode: CallBuiltinNode, parents: List<AstNode>) {}
    final override fun visit(parameterNode: ParameterNode, data: Unit) = withParent(parameterNode) { visit(parameterNode, parents) }
    open fun visit(parameterNode: ParameterNode, parents: List<AstNode>) {}
    final override fun visit(nullLiteralNode: NullLiteralNode, data: Unit) = withParent(nullLiteralNode) { visit(nullLiteralNode, parents) }
    open fun visit(nullLiteralNode: NullLiteralNode, parents: List<AstNode>) {}
    final override fun visit(pointerDereferenceNode: PointerDereferenceNode, data: Unit) = withParent(pointerDereferenceNode) { visit(pointerDereferenceNode, parents) }
    open fun visit(pointerDereferenceNode: PointerDereferenceNode, parents: List<AstNode>) {}
    final override fun visit(fieldAccessNode: FieldAccessNode, data: Unit) = withParent(fieldAccessNode) { visit(fieldAccessNode, parents) }
    open fun visit(fieldAccessNode: FieldAccessNode, parents: List<AstNode>) {}
    final override fun visit(fieldDereferenceNode: FieldDereferenceNode, data: Unit) = withParent(fieldDereferenceNode) { visit(fieldDereferenceNode, parents) }
    open fun visit(fieldDereferenceNode: FieldDereferenceNode, parents: List<AstNode>) {}
    final override fun visit(lValuePointerDereferenceNode: LValuePointerDereferenceNode, data: Unit) =
        withParent(lValuePointerDereferenceNode) { visit(lValuePointerDereferenceNode, parents) }

    open fun visit(lValuePointerDereferenceNode: LValuePointerDereferenceNode, parents: List<AstNode>) {}
    final override fun visit(lValueFieldAccessNode: LValueFieldAccessNode, data: Unit) = withParent(lValueFieldAccessNode) { visit(lValueFieldAccessNode, parents) }
    open fun visit(lValueFieldAccessNode: LValueFieldAccessNode, parents: List<AstNode>) {}
    final override fun visit(lValueFieldDereferenceNode: LValueFieldDereferenceNode, data: Unit) =
        withParent(lValueFieldDereferenceNode) { visit(lValueFieldDereferenceNode, parents) }

    open fun visit(lValueFieldDereferenceNode: LValueFieldDereferenceNode, parents: List<AstNode>) {}
    final override fun visit(lValueArrayAccessNode: LValueArrayAccessNode, data: Unit) = withParent(lValueArrayAccessNode) { visit(lValueArrayAccessNode, parents) }
    open fun visit(lValueArrayAccessNode: LValueArrayAccessNode, parents: List<AstNode>) {}
    final override fun visit(structDeclarationNode: StructDeclarationNode, data: Unit) = withParent(structDeclarationNode) { visit(structDeclarationNode, parents) }
    open fun visit(structDeclarationNode: StructDeclarationNode, parents: List<AstNode>) {}
    final override fun visit(structFieldDeclarationNode: StructFieldDeclarationNode, data: Unit) =
        withParent(structFieldDeclarationNode) { visit(structFieldDeclarationNode, parents) }

    open fun visit(structFieldDeclarationNode: StructFieldDeclarationNode, parents: List<AstNode>) {}
    final override fun visit(callAllocNode: CallAllocNode, data: Unit) = withParent(callAllocNode) { visit(callAllocNode, parents) }
    open fun visit(callAllocNode: CallAllocNode, parents: List<AstNode>) {}
    final override fun visit(callAllocArrayNode: CallAllocArrayNode, data: Unit) = withParent(callAllocArrayNode) { visit(callAllocArrayNode, parents) }
    open fun visit(callAllocArrayNode: CallAllocArrayNode, parents: List<AstNode>) {}
    final override fun visit(arrayAccessNode: ArrayAccessNode, data: Unit) = withParent(arrayAccessNode) { visit(arrayAccessNode, parents) }
    open fun visit(arrayAccessNode: ArrayAccessNode, parents: List<AstNode>) {}
}
