package edu.kit.kastel.vads.compiler.backend

import edu.kit.kastel.vads.compiler.CompilerOptions
import edu.kit.kastel.vads.compiler.CrashType
import edu.kit.kastel.vads.compiler.CrowTestUtil
import edu.kit.kastel.vads.compiler.ir.buildIr
import edu.kit.kastel.vads.compiler.lexer.Lexer
import edu.kit.kastel.vads.compiler.parser.ParseResult
import edu.kit.kastel.vads.compiler.parser.TokenSource
import edu.kit.kastel.vads.compiler.parser.parse
import org.junit.jupiter.api.DynamicTest
import org.junit.jupiter.api.TestFactory
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.test.fail

class LowererTest {

    @TestFactory
    fun lowererShouldProduceCorrectCode(): List<DynamicTest> {
        val testCases = CrowTestUtil.loadCrowTestCases()
        if (testCases.isEmpty()) {
            fail("test directory does not exist or contains no tests, expected it here: crow-tests")
        }

        return testCases
            .filter { !it.limitedToCategory }
            .filter { !it.shouldFailParsing && !it.shouldFailSemanticAnalysis }
            .map { testCase ->
                DynamicTest.dynamicTest("Lower: ${testCase.path.fileName}") {
                    val options = CompilerOptions()
                    val lexer = Lexer(testCase.code, options)
                    val tokenSource = TokenSource(lexer.lex())
                    with(options) {
                        val result = parse(tokenSource)
                        require(result is ParseResult.Success)
                        val ir = buildIr(result.program)
                        val asmIr = lowerIrToAsmIr(ir)
                        val evaluationResult = asmIr.evaluate(testCase.programInput)
                        when (evaluationResult) {
                            is EvaluationResult.DivByZero -> assertTrue(testCase.shouldCrash == CrashType.FLOATING_POINT)
                            is EvaluationResult.Success -> {
                                if (testCase.exitCode != null) {
                                    assertEquals(testCase.exitCode, evaluationResult.value)
                                }
                            }
                        }
                    }
                }
            }
    }
}