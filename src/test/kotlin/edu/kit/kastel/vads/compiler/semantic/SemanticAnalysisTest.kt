package edu.kit.kastel.vads.compiler.semantic

import edu.kit.kastel.vads.compiler.CompilerOptions
import edu.kit.kastel.vads.compiler.CrowTestUtil
import edu.kit.kastel.vads.compiler.lexer.Lexer
import edu.kit.kastel.vads.compiler.parser.ParseResult
import edu.kit.kastel.vads.compiler.parser.TokenSource
import edu.kit.kastel.vads.compiler.parser.parse
import org.junit.jupiter.api.DynamicTest
import org.junit.jupiter.api.TestFactory
import kotlin.test.assertTrue
import kotlin.test.fail

class SemanticAnalysisTest {

    @TestFactory
    fun semanticAnalysisShouldMatchCrowTestExpectations(): List<DynamicTest> {
        val testCases = CrowTestUtil.loadCrowTestCases()
        if (testCases.isEmpty()) {
            fail("test directory does not exist or contains no tests, expected it here: crow-tests")
        }

        return testCases
            .filter { !it.limitedToCategory }
            .map { testCase ->
                DynamicTest.dynamicTest("Semantic: ${testCase.path.fileName}") {
                    val options = CompilerOptions()
                    val lexer = Lexer(testCase.code, options)
                    val tokenSource = TokenSource(lexer.lex())
                    val parseResult = with(options) { parse(tokenSource) }
                    if (parseResult !is ParseResult.Success) {
                        // Skip semantic analysis if parsing failed
                        return@dynamicTest
                    }

                    val semanticResult = with(options) { analyzeProgram(parseResult.program) }
                    if (testCase.shouldFailSemanticAnalysis) {
                        assertTrue(semanticResult.isNotEmpty(), "Expected semantic analysis to fail for ${testCase.path.fileName}, but it succeeded")
                    } else {
                        assertTrue(semanticResult.isEmpty(), "Semantic analysis failed for ${testCase.path.fileName}: $semanticResult")
                    }
                }
            }
    }
}
