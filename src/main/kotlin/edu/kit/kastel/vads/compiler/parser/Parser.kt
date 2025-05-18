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
        val returnType = expect<Keyword, KeywordType>(KeywordType.INT)
        val identifier = expect<Identifier>()

        if (identifier.value != "main") {
            throw ParseError.FunctionNotNamedMain(identifier.span)
        }

        expect<Separator, SeparatorType>(SeparatorType.PAREN_OPEN)
        expect<Separator, SeparatorType>(SeparatorType.PAREN_CLOSE)
        val body = parseBlock()
        return FunctionNode(TypeNode(IntType, returnType.span), createNameNode(identifier), body)
    }

    private fun parseBlock(): BlockNode {
        val bodyOpen = expect<Separator, SeparatorType>(SeparatorType.BRACE_OPEN)
        val statements = mutableListOf<StatementNode>()

        while (true) {
            when (val token = tokenSource.peek()) {
                is Separator if token.type == SeparatorType.BRACE_CLOSE -> {
                    break
                }

                else -> statements += parseStatement()
            }
        }

        val bodyClose = expect<Separator, SeparatorType>(SeparatorType.BRACE_CLOSE)

        return BlockNode(statements, bodyOpen.span.merge(bodyClose.span))
    }

    private fun parseStatement(): StatementNode {
        val statement = when (val token = tokenSource.peek()) {
            is Keyword if token.type == KeywordType.INT -> parseDeclaration()
            is Keyword if token.type == KeywordType.RETURN -> parseReturn()
            else -> parseSimple()
        }

        expect<Separator, SeparatorType>(SeparatorType.SEMICOLON)
        return statement
    }

    private fun parseDeclaration(): StatementNode {
        val type = expect<Keyword, KeywordType>(KeywordType.INT)
        val identifier = expect<Identifier>()

        val token = tokenSource.peek()
        val expression = if (token is Operator && token.type == OperatorType.ASSIGN) {
            expect<Operator, OperatorType>(OperatorType.ASSIGN)
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
            expect<Separator, SeparatorType>(SeparatorType.PAREN_OPEN)
            val inner = parseLValue()
            expect<Separator, SeparatorType>(SeparatorType.PAREN_CLOSE)
            return inner
        }

        val identifier = expect<Identifier>()
        return LValueIdentifierNode(createNameNode(identifier))
    }

    private fun parseReturn(): StatementNode {
        val returnToken = expect<Keyword, KeywordType>(KeywordType.RETURN)
        val expression = parseExpression()
        return ReturnNode(expression, returnToken.span.start)
    }

    private fun parseExpression(): ExpressionNode {
        var lhs = parseTerm()

        while (true) {
            when (val token = tokenSource.peek()) {
                is Operator if token.type == OperatorType.ADD || token.type == OperatorType.SUB -> {
                    tokenSource.consume()
                    lhs = BinaryOperationNode(lhs, parseTerm(), token.type)
                }

                else -> return lhs
            }
        }
    }

    private fun parseTerm(): ExpressionNode {
        var lhs = parseFactor()

        while (true) {
            when (val token = tokenSource.peek()) {
                is Operator if token.type == OperatorType.MUL || token.type == OperatorType.DIV || token.type == OperatorType.MOD -> {
                    tokenSource.consume()
                    lhs = BinaryOperationNode(lhs, parseFactor(), token.type)
                }

                else -> return lhs
            }
        }
    }

    private fun parseFactor(): ExpressionNode {
        return when (val token = tokenSource.peek()) {
            is Separator if token.type == SeparatorType.PAREN_OPEN -> {
                tokenSource.consume()
                val expression = parseExpression()
                expect<Separator, SeparatorType>(SeparatorType.PAREN_CLOSE)
                expression
            }

            is Operator if token.type == OperatorType.SUB -> {
                val span = tokenSource.consume()!!.span
                NegateNode(parseFactor(), span)
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

    private inline fun <reified T : TokenWithType<Type>, Type> expect(type: Type): T {
        val token = expect<T>()

        if (token.type != type) {
            throw ParseError.UnexpectedToken(token, token.span)
        }

        return token
    }

    private fun createNameNode(identifier: Identifier) =
        NameNode(SymbolName.forIdentifier(identifier), identifier.span)
}
