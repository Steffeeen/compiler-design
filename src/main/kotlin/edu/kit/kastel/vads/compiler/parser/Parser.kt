package edu.kit.kastel.vads.compiler.parser

import edu.kit.kastel.vads.compiler.CompilerOptions
import edu.kit.kastel.vads.compiler.Span
import edu.kit.kastel.vads.compiler.lexer.Token
import edu.kit.kastel.vads.compiler.lexer.Token.*
import edu.kit.kastel.vads.compiler.parser.AstNode.*
import edu.kit.kastel.vads.compiler.typechecker.Type

sealed class ParseError(open val span: Span) : Exception() {
    data class UnexpectedToken(val token: Token, override val span: Span) : ParseError(span)
    data class UnexpectedEndOfFile(override val span: Span) : ParseError(span)
    data class InvalidType(override val span: Span) : ParseError(span)
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
        val functions = mutableListOf<FunctionNode>()

        while (tokenSource.hasMoreTokens()) {
            functions += parseFunction()
        }

        return ProgramNode(functions)
    }

    private fun parseFunction(): FunctionNode {
        val returnType = expectType<KeywordType.TypeKeywordType>()
        val identifier = expect<Identifier>()

        expectType<SeparatorType>(SeparatorType.PAREN_OPEN)
        val parameters = parseCommaSeparatedList(SeparatorType.PAREN_CLOSE) {
            val type = parseType()
            val identifier = expect<Identifier>()
            ParameterNode(type, createNameNode(identifier))
        }
        expectType<SeparatorType>(SeparatorType.PAREN_CLOSE)
        val body = parseBlock()
        return FunctionNode(TypeNode(Type.IntType, returnType.span), parameters, createNameNode(identifier), body)
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

    private fun parseType(): TypeNode {
        return when (val token = tokenSource.peek()) {
            is Keyword if token.type == KeywordType.INT -> {
                tokenSource.consume()
                TypeNode(Type.IntType, token.span)
            }

            is Keyword if token.type == KeywordType.BOOL -> {
                tokenSource.consume()
                TypeNode(Type.BoolType, token.span)
            }

            else -> throw ParseError.InvalidType(token?.span ?: tokenSource.createEOFSpan())
        }
    }

    private fun parseDeclaration(): DeclarationNode {
        val type = parseType()
        val identifier = expect<Identifier>()

        val token = tokenSource.peek()
        val expression = if (token is Operator && token.type == OperatorType.ASSIGN) {
            expectType<OperatorType>(OperatorType.ASSIGN)
            parseExpression()
        } else {
            null
        }

        return DeclarationNode(type, createNameNode(identifier), expression)
    }

    private fun parseStatement(): StatementNode {
        val statement = when (val token = tokenSource.peek()) {
            is Separator if token.type == SeparatorType.BRACE_OPEN -> parseBlock()
            is Keyword if token.type in setOf(KeywordType.IF, KeywordType.WHILE, KeywordType.FOR, KeywordType.CONTINUE, KeywordType.BREAK, KeywordType.RETURN) -> parseControl()
            else -> {
                val statement = parseSimple()
                expectType<SeparatorType>(SeparatorType.SEMICOLON)
                statement
            }
        }

        return statement
    }

    private fun parseSimple(): SimpleNode {
        fun parseAssignment(): AssignmentNode {
            val lValue = parseLValue()
            val assignmentOperatorType = parseAssignmentOperator()
            val expression = parseExpression()
            return AssignmentNode(lValue, assignmentOperatorType, expression)
        }

        return when (val token = tokenSource.peek()) {
            is Keyword if token.type == KeywordType.INT || token.type == KeywordType.BOOL -> parseDeclaration()
            is Keyword if token.type is KeywordType.BuiltinFunctionType -> parseBuiltinCall()
            is Identifier -> {
                val nextToken = tokenSource.peekNext()
                if (nextToken is Separator && nextToken.type == SeparatorType.PAREN_OPEN) {
                    parseCall()
                } else {
                    parseAssignment()
                }
            }

            else -> parseAssignment()
        }
    }

    private fun parseSimpleOptional(): SimpleNode? {
        return when (val token = tokenSource.peek()) {
            is Keyword if token.type == KeywordType.INT || token.type == KeywordType.BOOL -> parseSimple()
            is Separator if token.type == SeparatorType.PAREN_OPEN -> parseSimple()
            is Identifier -> parseSimple()
            else -> null
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

    private fun parseElseOptional(): StatementNode? {
        when (val token = tokenSource.peek()) {
            is Keyword if token.type == KeywordType.ELSE -> {
                tokenSource.consume()
                return parseStatement()
            }

            else -> return null
        }
    }

    private fun parseControl(): StatementNode {
        val token = tokenSource.peek()

        if (token !is Keyword) {
            unexpectedToken(token)
        }

        return when (token.type) {
            KeywordType.IF -> parseIfStatement()
            KeywordType.WHILE -> parseWhileStatement()
            KeywordType.FOR -> parseForStatement()
            KeywordType.CONTINUE -> parseContinueStatement()
            KeywordType.BREAK -> parseBreakStatement()
            KeywordType.RETURN -> parseReturnStatement()
            else -> unexpectedToken(token)
        }
    }

    private fun parseIfStatement(): StatementNode {
        val ifToken = expectType<KeywordType>(KeywordType.IF)
        expectType<SeparatorType>(SeparatorType.PAREN_OPEN)
        val condition = parseExpression()
        expectType<SeparatorType>(SeparatorType.PAREN_CLOSE)
        val body = parseStatement()
        val elseStatement = parseElseOptional()
        return IfNode(condition, body, elseStatement, ifToken.span.merge(elseStatement?.span ?: body.span))
    }

    private fun parseWhileStatement(): StatementNode {
        val whileToken = expectType<KeywordType>(KeywordType.WHILE)
        expectType<SeparatorType>(SeparatorType.PAREN_OPEN)
        val condition = parseExpression()
        expectType<SeparatorType>(SeparatorType.PAREN_CLOSE)
        val body = parseStatement()
        return WhileNode(condition, body, whileToken.span.merge(body.span))
    }

    private fun parseForStatement(): StatementNode {
        val forToken = expectType<KeywordType>(KeywordType.FOR)
        expectType<SeparatorType>(SeparatorType.PAREN_OPEN)

        val initializer = parseSimpleOptional()
        expectType(SeparatorType.SEMICOLON)
        val condition = parseExpression()
        expectType(SeparatorType.SEMICOLON)
        val increment = parseSimpleOptional()

        expectType<SeparatorType>(SeparatorType.PAREN_CLOSE)
        val body = parseStatement()

        return ForNode(initializer, condition, increment, body, forToken.span.merge(body.span))
    }

    private fun parseContinueStatement(): StatementNode {
        val continueToken = expectType<KeywordType>(KeywordType.CONTINUE)
        expectType(SeparatorType.SEMICOLON)
        return ContinueNode(continueToken.span)
    }

    private fun parseBreakStatement(): StatementNode {
        val breakToken = expectType<KeywordType>(KeywordType.BREAK)
        expectType(SeparatorType.SEMICOLON)
        return BreakNode(breakToken.span)
    }

    private fun parseReturnStatement(): StatementNode {
        val returnToken = expectType<KeywordType>(KeywordType.RETURN)
        val expression = parseExpression()
        expectType(SeparatorType.SEMICOLON)
        return ReturnNode(expression, returnToken.span.start)
    }

    private fun parseAssignmentOperator(): OperatorType.AssignOperatorType {
        val token = tokenSource.peek()

        if (token == null) {
            throw ParseError.UnexpectedEndOfFile(tokenSource.createEOFSpan())
        }

        if (token !is Operator) {
            throw ParseError.UnexpectedToken(token, token.span)
        }

        if (token.type !is OperatorType.AssignOperatorType) {
            throw ParseError.UnexpectedToken(token, token.span)
        }

        tokenSource.consume()
        return token.type
    }

    private fun parseExpression(): ExpressionNode {
        return computeExpression(1)
    }

    // Precedence climbing
    private fun computeExpression(minPrecedence: Int): ExpressionNode {
        var result = computeAtom()

        while (true) {
            val token = tokenSource.peek() ?: break

            if (token is Operator && token.type == OperatorType.TERNARY && OperatorType.TERNARY.ternaryPrecedence >= minPrecedence) {
                tokenSource.consume()
                val trueBranch = computeExpression(OperatorType.TERNARY.ternaryPrecedence)
                expectType(SeparatorType.COLON)
                val falseBranch = computeExpression(OperatorType.TERNARY.ternaryPrecedence)
                result = TernaryOperationNode(result, trueBranch, falseBranch)
                continue
            }

            if (token !is Operator || token.type is OperatorType.AssignOperatorType || token.type !is OperatorType.BinaryOperatorType || token.type.binaryPrecedence < minPrecedence) {
                break
            }

            val precedence = token.type.binaryPrecedence
            val associativity = token.type.binaryAssociativity

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

            is Operator if token.type is OperatorType.UnaryOperatorType -> {
                tokenSource.consume()
                val atom = computeAtom()
                UnaryOperationNode(atom, token.type, token.span.merge(atom.span))
            }

            is Keyword if token.type is KeywordType.BuiltinFunctionType -> parseBuiltinCall()

            is Identifier -> {
                val nextToken = tokenSource.peekNext()
                if (nextToken is Separator && nextToken.type == SeparatorType.PAREN_OPEN) {
                    parseCall()
                } else {
                    tokenSource.consume()
                    IdentifierExpressionNode(createNameNode(token))
                }
            }

            is Keyword if token.type == KeywordType.TRUE || token.type == KeywordType.FALSE -> {
                tokenSource.consume()
                val value = token.type == KeywordType.TRUE
                BooleanLiteralNode(value, token.span)
            }

            is NumberLiteral -> {
                tokenSource.consume()
                IntLiteralNode(token.value, token.base, token.span)
            }

            else -> unexpectedToken(token)
        }
    }

    private fun parseCall(): CallNormalNode {
        val identifier = expect<Identifier>()
        expectType<SeparatorType>(SeparatorType.PAREN_OPEN)
        val arguments = parseCommaSeparatedList(SeparatorType.PAREN_CLOSE, ::parseExpression)
        val parenClose = expectType<SeparatorType>(SeparatorType.PAREN_CLOSE)

        return CallNormalNode(createNameNode(identifier), arguments, identifier.span.merge(parenClose.span))
    }

    private fun parseBuiltinCall(): CallBuiltinNode {
        val token = expect<Keyword>()
        require(token.type is KeywordType.BuiltinFunctionType)

        expectType<SeparatorType>(SeparatorType.PAREN_OPEN)
        val arguments = parseCommaSeparatedList(SeparatorType.PAREN_CLOSE, ::parseExpression)
        val parenClose = expectType<SeparatorType>(SeparatorType.PAREN_CLOSE)

        return CallBuiltinNode(token.type, arguments, token.span.merge(parenClose.span))
    }

    private fun <T : AstNode> parseCommaSeparatedList(endSeparator: SeparatorType, parseElement: () -> T): List<T> {
        val elements = mutableListOf<T>()

        val token = tokenSource.peek()
        if (token is Separator && token.type == endSeparator) {
            return elements
        }

        elements += parseElement()

        while (true) {
            when (val token = tokenSource.peek()) {
                is Separator if token.type == endSeparator -> break
                else -> {
                    expectType(SeparatorType.COMMA)
                    elements += parseElement()
                }
            }
        }

        return elements
    }

    private fun unexpectedToken(token: Token?): Nothing {
        if (token == null) {
            throw ParseError.UnexpectedEndOfFile(tokenSource.createEOFSpan())
        }
        throw ParseError.UnexpectedToken(token, token.span)
    }

    private inline fun <reified T : Token> expect(): T {
        val token = tokenSource.peek()

        if (token !is T) {
            unexpectedToken(token)
        }

        tokenSource.consume()
        return token
    }

    private inline fun <reified T : TokenType> expectType(): TokenWithType<T> {
        val token = expect<TokenWithType<T>>()
        @Suppress("USELESS_IS_CHECK") // this seems like an IntelliJ bug, if the check is removed it does not work
        if (token.type !is T) {
            throw ParseError.UnexpectedToken(token, token.span)
        }
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
