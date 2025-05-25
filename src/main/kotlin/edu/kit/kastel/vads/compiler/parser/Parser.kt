package edu.kit.kastel.vads.compiler.parser

import edu.kit.kastel.vads.compiler.CompilerOptions
import edu.kit.kastel.vads.compiler.Span
import edu.kit.kastel.vads.compiler.lexer.Token
import edu.kit.kastel.vads.compiler.lexer.Token.*
import edu.kit.kastel.vads.compiler.parser.AstNode.*
import edu.kit.kastel.vads.compiler.parser.Type.IntType

sealed class ParseError(open val span: Span) : Exception() {
    data class UnexpectedToken(val token: Token, override val span: Span) : ParseError(span)
    data class UnexpectedEndOfFile(override val span: Span) : ParseError(span)
    data class ExpectedEndOfFile(override val span: Span) : ParseError(span)
    data class FunctionNotNamedMain(override val span: Span) : ParseError(span)
}

sealed interface ParseResult {
    data class Success(val program: ProgramNode) : ParseResult
    data class Failure(val errors: List<ParseError>) : ParseResult
}

context(options: CompilerOptions)
fun parse(tokenSource: TokenSource): ParseResult {
    val parser = Parser(tokenSource, options)

    try {
        val program = parser.parseProgram()
        return ParseResult.Success(program)
    } catch (e: ParseError) {
        return ParseResult.Failure(listOf(e))
    }
}

private class Parser(private val tokenSource: TokenSource, private val options: CompilerOptions) {
    fun parseProgram(): ProgramNode {
        val program = ProgramNode(listOf(parseFunction()))

        if (!tokenSource.hasNoMoreTokens()) {
            throw ParseError.ExpectedEndOfFile(tokenSource.peek()!!.span)
        }

        return program
    }

    private fun parseFunction(): FunctionNode {
        val returnType = expectType<KeywordType>(KeywordType.INT)
        val identifier = expect<Identifier>()

        if (identifier.value != "main") {
            throw ParseError.FunctionNotNamedMain(identifier.span)
        }

        expectType<SeparatorType>(SeparatorType.PAREN_OPEN)
        expectType<SeparatorType>(SeparatorType.PAREN_CLOSE)
        val body = parseBlock()
        return FunctionNode(TypeNode(IntType, returnType.span), createNameNode(identifier), body)
    }

    private fun parseBlock(): BlockNode {
        val bodyOpen = expectType<SeparatorType>(SeparatorType.BRACE_OPEN)
        val statements = mutableListOf<StatementNode>()

        while (true) {
            when (val token = tokenSource.peek()) {
                is Separator if token.type == SeparatorType.BRACE_CLOSE -> {
                    break
                }

                else -> statements += parseStatement()
            }
        }

        val bodyClose = expectType<SeparatorType>(SeparatorType.BRACE_CLOSE)

        return BlockNode(statements, bodyOpen.span.merge(bodyClose.span))
    }

    private fun parseStatement(): StatementNode {
        val statement = when (val token = tokenSource.peek()) {
            is Keyword if token.type == KeywordType.INT -> parseDeclaration()
            is Keyword if token.type == KeywordType.RETURN -> parseReturn()
            else -> parseSimple()
        }

        expectType<SeparatorType>(SeparatorType.SEMICOLON)
        return statement
    }

    private fun parseDeclaration(): StatementNode {
        val type = expectType<KeywordType>(KeywordType.INT)
        val identifier = expect<Identifier>()

        val token = tokenSource.peek()
        val expression = if (token is Operator && token.type == OperatorType.ASSIGN) {
            expectType<OperatorType>(OperatorType.ASSIGN)
            parseExpression()
        } else {
            null
        }

        return DeclarationNode(TypeNode(IntType, type.span), createNameNode(identifier), expression)
    }

    private fun parseSimple(): StatementNode {
        val lValue = parseLValue()
        val assignmentOperator = parseAssignmentOperator()
        val expression = parseExpression()
        return AssignmentNode(lValue, assignmentOperator, expression)
    }

    private fun parseAssignmentOperator(): Operator {
        val token = tokenSource.peek()

        if (token == null) {
            throw ParseError.UnexpectedEndOfFile(tokenSource.createEOFSpan())
        }

        if (token !is Operator) {
            throw ParseError.UnexpectedToken(token, token.span)
        }

        return when (token.type) {
            OperatorType.ASSIGN,
            OperatorType.ASSIGN_DIV,
            OperatorType.ASSIGN_SUB,
            OperatorType.ASSIGN_MOD,
            OperatorType.ASSIGN_MUL,
            OperatorType.ASSIGN_ADD -> {
                tokenSource.consume()
                token
            }

            else -> throw ParseError.UnexpectedToken(token, token.span)
        }
    }

    private fun parseLValue(): LValueNode {
        val token = tokenSource.peek()

        if (token is Separator && token.type == SeparatorType.PAREN_OPEN) {
            expectType<SeparatorType>(SeparatorType.PAREN_OPEN)
            val inner = parseLValue()
            expectType<SeparatorType>(SeparatorType.PAREN_CLOSE)
            return inner
        }

        val identifier = expect<Identifier>()
        return LValueIdentifierNode(createNameNode(identifier))
    }

    private fun parseReturn(): StatementNode {
        val returnToken = expectType<KeywordType>(KeywordType.RETURN)
        val expression = parseExpression()
        return ReturnNode(expression, returnToken.span.start)
    }

    private fun parseExpression(): ExpressionNode {
        return computeExpression(1)
    }

    // Precedence climbing
    private fun computeExpression(minPrecedence: Int): ExpressionNode {
        var result = computeAtom()

        while (true) {
            val token = tokenSource.peek() ?: break

            if (token !is Operator || token.type.precedence < minPrecedence || !token.type.canBeBinary) {
                break
            }

            val precedence = token.type.precedence
            val associativity = token.type.associativity

            val nextMinPrecedence = if (associativity == Associativity.LEFT) precedence + 1 else precedence

            tokenSource.consume()
            val rhs = computeExpression(nextMinPrecedence)
            result = BinaryOperationNode(result, rhs, token.type)
        }

        return result
    }

    private fun computeAtom(): ExpressionNode {
        return when (val token = tokenSource.peek()) {
            is Separator if token.type == SeparatorType.PAREN_OPEN -> {
                tokenSource.consume()
                val expression = computeExpression(1)
                expectType<SeparatorType>(SeparatorType.PAREN_CLOSE)
                expression
            }

            is Operator if token.type.canBeUnary -> {
                tokenSource.consume()
                UnaryOperationNode(computeAtom(), token)
            }

            is Identifier -> {
                tokenSource.consume()
                IdentifierExpressionNode(createNameNode(token))
            }

            is NumberLiteral -> {
                tokenSource.consume()
                LiteralNode(token.value, token.base, token.span)
            }

            null -> throw ParseError.UnexpectedEndOfFile(tokenSource.createEOFSpan())
            else -> throw ParseError.UnexpectedToken(token, token.span)
        }
    }

    private inline fun <reified T : Token> expect(): T {
        val token = tokenSource.peek()

        if (token == null) {
            throw ParseError.UnexpectedEndOfFile(tokenSource.createEOFSpan())
        }

        if (token !is T) {
            throw ParseError.UnexpectedToken(token, token.span)
        }

        tokenSource.consume()
        return token
    }

    private inline fun <reified T : TokenType> expectType(type: T): TokenWithType<T> {
        val token = expect<TokenWithType<T>>()

        if (token.type != type) {
            throw ParseError.UnexpectedToken(token, token.span)
        }

        return token
    }

    private fun createNameNode(identifier: Identifier) =
        NameNode(SymbolName.forIdentifier(identifier), identifier.span)
}
