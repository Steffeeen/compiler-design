package edu.kit.kastel.vads.compiler

import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import kotlin.test.DefaultAsserter.fail

data class CrowTestCase(
    val path: Path,
    val code: String,
    val exitCode: Int?,
    val shouldSucceed: Boolean,
    val programInput: List<Char>,
    val shouldCrash: CrashType?,
    val shouldFailParsing: Boolean,
    val shouldFailSemanticAnalysis: Boolean,
    val limitedToCategory: Boolean
)

enum class CrashType {
    FLOATING_POINT
}

private fun createSectionRegex(sectionName: String): Regex {
    return Regex("""## $sectionName\s+```(.*?)```""", RegexOption.DOT_MATCHES_ALL)
}

object CrowTestUtil {
    private val TESTS_DIR = Paths.get("crow-tests")
    private val PROGRAM_ARG_SECTION = createSectionRegex("ProgramArgumentFile")
    private val SHOULD_FAIL_SECTION = createSectionRegex("ShouldFail")
    private val LIMITED_TO_CATEGORY_SECTION = createSectionRegex("Limited to Category")
    private val EXIT_CODE_SECTION = createSectionRegex("ExitCode")
    private val PROGRAM_INPUT_SECTION = createSectionRegex("ProgramInput")
    private val SHOULD_CRASH_SECTION = createSectionRegex("ShouldCrash")

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

                val exitCode = EXIT_CODE_SECTION.find(content)
                    ?.groups?.get(1)?.value
                    ?.trim()
                    ?.toIntOrNull()

                val shouldFailSection = SHOULD_FAIL_SECTION.find(content)
                    ?.groups?.get(1)?.value
                    ?.lines()
                    ?.map { it.trim() }
                    ?: emptyList()

                val programInput = PROGRAM_INPUT_SECTION.find(content)
                    ?.groups?.get(1)?.value
                    ?.trim()
                    ?.toList()
                    ?: listOf()

                val crashType = SHOULD_CRASH_SECTION.find(content)
                    ?.groups?.get(1)?.value
                    ?.trim()
                    ?.let { crashTypeString ->
                        when (crashTypeString) {
                            "FloatingPointException" -> CrashType.FLOATING_POINT
                            else -> null
                        }
                    }

                val limitedContent = content.substringAfter("# Executing your compiler").substringBefore("# Executing the compiled binary")
                val shouldSucceed = limitedContent.contains("## ShouldSucceed", ignoreCase = true)

                val shouldFailParsing = shouldFailSection.any { it.equals("Parsing", ignoreCase = true) }
                val shouldFailSemanticAnalysis = shouldFailSection.any { it.equals("SemanticAnalysis", ignoreCase = true) }

                CrowTestCase(
                    path = path,
                    code = code,
                    exitCode = exitCode,
                    shouldSucceed = shouldSucceed,
                    programInput = programInput,
                    shouldCrash = crashType,
                    shouldFailParsing = shouldFailParsing,
                    shouldFailSemanticAnalysis = shouldFailSemanticAnalysis,
                    limitedToCategory = limitedToCategory
                )
            }
    }
}
