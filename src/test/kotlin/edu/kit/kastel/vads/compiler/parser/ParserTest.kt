package edu.kit.kastel.vads.compiler.parser

import edu.kit.kastel.vads.compiler.CompilerOptions
import edu.kit.kastel.vads.compiler.lexer.Lexer
import org.junit.jupiter.api.DynamicTest
import org.junit.jupiter.api.TestFactory
import java.nio.file.Files
import java.nio.file.Paths
import kotlin.test.assertTrue
import kotlin.test.fail

class ParserTest {

    companion object {
        private val TESTS_DIR = Paths.get("crow-tests")
        private val PROGRAM_ARG_SECTION = Regex("""## ProgramArgumentFile\s+```(.*?)```""", RegexOption.DOT_MATCHES_ALL)
        private val SHOULD_FAIL_SECTION = Regex("""## ShouldFail\s+```(.*?)```""", RegexOption.DOT_MATCHES_ALL)
        private val LIMITED_TO_CATEGORY_SECTION = Regex("""## Limited to Category\s+```(.*?)```""", RegexOption.DOT_MATCHES_ALL)
    }

    @TestFactory
    fun parserShouldMatchCrowTestExpectations(): List<DynamicTest> {
        if (!Files.exists(TESTS_DIR)) {
            // No tests directory found, skip
            fail("test directory does not exist, expected it here: ${TESTS_DIR.toAbsolutePath()}")
            return emptyList()
        }

        return Files.walk(TESTS_DIR)
            .filter { it.toString().endsWith(".crow-test.md") }
            .toList()
            .mapNotNull { path ->
                val content = Files.readString(path)

                // Skip if "Limited to Category" is true
                val limitedToCategory = LIMITED_TO_CATEGORY_SECTION.find(content)
                    ?.groups?.get(1)?.value
                    ?.trim()
                    ?.equals("true", ignoreCase = true) == true
                if (limitedToCategory) return@mapNotNull null

                val code = PROGRAM_ARG_SECTION.find(content)?.groups?.get(1)?.value?.trim()
                if (code.isNullOrBlank()) return@mapNotNull null

                val shouldFailParsing = SHOULD_FAIL_SECTION.find(content)
                    ?.groups?.get(1)?.value
                    ?.lines()
                    ?.any { it.trim().equals("Parsing", ignoreCase = true) } == true

                DynamicTest.dynamicTest("Parse: ${path.fileName}") {
                    val options = CompilerOptions()
                    val lexer = Lexer(code, options)
                    val tokenSource = TokenSource(lexer.lex())
                    val result = with(options) { parse(tokenSource) }
                    if (shouldFailParsing) {
                        assertTrue(result is ParseResult.Failure, "Expected parser to fail for ${path.fileName}, but it succeeded: $result")
                    } else {
                        assertTrue(result is ParseResult.Success, "Parser failed for ${path.fileName}: $result")
                    }
                }
            }
    }
}
