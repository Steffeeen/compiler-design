package edu.kit.kastel.vads.compiler.parser

import edu.kit.kastel.vads.compiler.CompilerOptions
import edu.kit.kastel.vads.compiler.CrowTestUtil
import edu.kit.kastel.vads.compiler.lexer.Lexer
import org.junit.jupiter.api.DynamicTest
import org.junit.jupiter.api.TestFactory
import kotlin.test.assertTrue
import kotlin.test.fail

class ParserTest {

    @TestFactory
    fun parserShouldMatchCrowTestExpectations(): List<DynamicTest> {
        val testCases = CrowTestUtil.loadCrowTestCases()
        if (testCases.isEmpty()) {
            fail("test directory does not exist or contains no tests, expected it here: crow-tests")
        }

        return testCases
            .filter { !it.limitedToCategory }
            .map { testCase ->
                DynamicTest.dynamicTest("Parse: ${testCase.path.fileName}") {
                    val options = CompilerOptions()
                    val lexer = Lexer(testCase.code, options)
                    val tokenSource = TokenSource(lexer.lex())
                    val result = with(options) { parse(tokenSource) }
                    if (testCase.shouldFailParsing) {
                        assertTrue(result is ParseResult.Failure, "Expected parser to fail for ${testCase.path.fileName}, but it succeeded: $result")
                    } else {
                        assertTrue(result is ParseResult.Success, "Parser failed for ${testCase.path.fileName}: $result")
                    }
                }
            }
    }
}
