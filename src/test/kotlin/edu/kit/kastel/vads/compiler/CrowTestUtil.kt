package edu.kit.kastel.vads.compiler

import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import kotlin.test.DefaultAsserter.fail

data class CrowTestCase(
    val path: Path,
    val code: String,
    val shouldFailParsing: Boolean,
    val shouldFailSemanticAnalysis: Boolean,
    val limitedToCategory: Boolean
)

object CrowTestUtil {
    private val TESTS_DIR = Paths.get("crow-tests")
    private val PROGRAM_ARG_SECTION = Regex("""## ProgramArgumentFile\s+```(.*?)```""", RegexOption.DOT_MATCHES_ALL)
    private val SHOULD_FAIL_SECTION = Regex("""## ShouldFail\s+```(.*?)```""", RegexOption.DOT_MATCHES_ALL)
    private val LIMITED_TO_CATEGORY_SECTION = Regex("""## Limited to Category\s+```(.*?)```""", RegexOption.DOT_MATCHES_ALL)

    fun loadCrowTestCases(): List<CrowTestCase> {
        if (!Files.exists(TESTS_DIR)) {
            fail("Test directory does not exist: $TESTS_DIR. Expected it to be here: ${TESTS_DIR.toAbsolutePath()}")
        }

        return Files.walk(TESTS_DIR)
            .filter { it.toString().endsWith(".crow-test.md") }
            .toList()
            .mapNotNull { path ->
                val content = Files.readString(path)

                val limitedToCategory = LIMITED_TO_CATEGORY_SECTION.find(content)
                    ?.groups?.get(1)?.value
                    ?.trim()
                    ?.equals("true", ignoreCase = true) == true

                val code = PROGRAM_ARG_SECTION.find(content)?.groups?.get(1)?.value?.trim()
                if (code.isNullOrBlank()) return@mapNotNull null

                val shouldFailSection = SHOULD_FAIL_SECTION.find(content)
                    ?.groups?.get(1)?.value
                    ?.lines()
                    ?.map { it.trim() }
                    ?: emptyList()

                val shouldFailParsing = shouldFailSection.any { it.equals("Parsing", ignoreCase = true) }
                val shouldFailSemanticAnalysis = shouldFailSection.any { it.equals("SemanticAnalysis", ignoreCase = true) }

                CrowTestCase(
                    path = path,
                    code = code,
                    shouldFailParsing = shouldFailParsing,
                    shouldFailSemanticAnalysis = shouldFailSemanticAnalysis,
                    limitedToCategory = limitedToCategory
                )
            }
    }
}
