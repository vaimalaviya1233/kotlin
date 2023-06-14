/*
 * Copyright 2010-2023 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */

package org.jetbrains.kotlin.test.frontend.fir.differences

import org.jetbrains.kotlin.codeMetaInfo.CodeMetaInfoParser
import org.jetbrains.kotlin.codeMetaInfo.clearTextFromDiagnosticMarkup
import org.jetbrains.kotlin.codeMetaInfo.model.ParsedCodeMetaInfo
import org.jetbrains.kotlin.config.LanguageFeature
import org.jetbrains.kotlin.config.LanguageVersion
import org.jetbrains.kotlin.test.util.LANGUAGE_FEATURE_PATTERN
import java.io.File
import java.io.Writer

val equivalentDiagnostics = listOf(
    listOf("PARCELABLE_CANT_BE_LOCAL_CLASS", "PARCELABLE_SHOULD_BE_CLASS"),
    listOf(
        "TYPE_MISMATCH",
        "ARGUMENT_TYPE_MISMATCH",
        "RETURN_TYPE_MISMATCH",
        "ASSIGNMENT_TYPE_MISMATCH",
        "INITIALIZER_TYPE_MISMATCH",
        "CONSTANT_EXPECTED_TYPE_MISMATCH",
        "HAS_NEXT_FUNCTION_TYPE_MISMATCH",
        "CONDITION_TYPE_MISMATCH",
        "UPPER_BOUND_VIOLATED_IN_TYPEALIAS_EXPANSION",
        "UPPER_BOUND_VIOLATED_BASED_ON_JAVA_ANNOTATIONS",
        "UPPER_BOUND_VIOLATED",
        "NEW_INFERENCE_ERROR",
        "NO_VALUE_FOR_PARAMETER",
        "NO_SET_METHOD",
//    ),
//    listOf(
        "UNRESOLVED_REFERENCE_WRONG_RECEIVER",
        "UNRESOLVED_REFERENCE",
        "UNRESOLVED_MEMBER",
        "UNRESOLVED_IMPORT",
        "CALLABLE_REFERENCE_RESOLUTION_AMBIGUITY",
        "DEPRECATION_ERROR",
        "OVERLOAD_RESOLUTION_AMBIGUITY",
        "NO_COMPANION_OBJECT",
        "NESTED_CLASS_ACCESSED_VIA_INSTANCE_REFERENCE",
        "RESOLUTION_TO_CLASSIFIER",
        "FUNCTION_EXPECTED",
        "RECURSIVE_TYPEALIAS_EXPANSION",
        "MISSING_DEPENDENCY_CLASS",
//    ),
//    listOf(
        "INVISIBLE_MEMBER",
        "INVISIBLE_REFERENCE",
//    ),
//    listOf(
        "WRONG_NUMBER_OF_TYPE_ARGUMENTS",
        "NO_TYPE_ARGUMENTS_ON_RHS",
        "CANNOT_CHECK_FOR_ERASED",
        "TYPE_ARGUMENTS_FOR_OUTER_CLASS_WHEN_NESTED_REFERENCED",
        "INAPPLICABLE_CANDIDATE",
//    ),
//    listOf(
        "VAL_REASSIGNMENT",
        "NONE_APPLICABLE",
    ),
    listOf("DELEGATE_SPECIAL_FUNCTION_MISSING", "DELEGATE_SPECIAL_FUNCTION_NONE_APPLICABLE"),
    listOf(
        "TYPECHECKER_HAS_RUN_INTO_RECURSIVE_PROBLEM_IN_AUGMENTED_ASSIGNMENT_ERROR",
        "TYPECHECKER_HAS_RUN_INTO_RECURSIVE_PROBLEM",
    ),
    listOf(
        "ASSIGNMENT_IN_EXPRESSION_CONTEXT",
        "EXPRESSION_EXPECTED",
    ),
    listOf(
        "SMARTCAST_IMPOSSIBLE",
        "UNSAFE_CALL",
    ),
)

val equivalentDiagnosticsLookup = buildMap {
    for (klass in equivalentDiagnostics) {
        for (diagnostic in klass) {
            if (diagnostic in this) {
                error("$diagnostic is present both in ${this[diagnostic]} and in $klass")
            }

            put(diagnostic, klass)
        }
    }
}

val ParsedCodeMetaInfo.equivalenceClass: Any get() = equivalentDiagnosticsLookup[tag] ?: tag

fun ParsedCodeMetaInfo.isOnSamePositionAs(another: ParsedCodeMetaInfo) =
    start == another.start && end == another.end

fun ParsedCodeMetaInfo.isEquivalentTo(another: ParsedCodeMetaInfo): Boolean {
    return when (another) {
        this -> true
        else -> equivalenceClass == another.equivalenceClass && isOnSamePositionAs(another)
    }
}

fun extractCommonMetaInfosSlow(
    allK1MetaInfos: Collection<ParsedCodeMetaInfo>,
    allK2MetaInfos: Collection<ParsedCodeMetaInfo>,
): Triple<Collection<ParsedCodeMetaInfo>, Collection<ParsedCodeMetaInfo>, Collection<ParsedCodeMetaInfo>> {
    val (commonMetaInfos, k1MetaInfos) = allK1MetaInfos.partition { tagInK1 -> allK2MetaInfos.any { it.isEquivalentTo(tagInK1) } }
    val k2MetaInfos = allK2MetaInfos.filter { tagInK2 -> commonMetaInfos.none { it.isEquivalentTo(tagInK2) } }
    return Triple(k1MetaInfos, k2MetaInfos, commonMetaInfos)
}

fun extractSignificantMetaInfosSlow(
    erroneousK1MetaInfos: Collection<ParsedCodeMetaInfo>,
    erroneousK2MetaInfos: Collection<ParsedCodeMetaInfo>,
): Pair<Collection<ParsedCodeMetaInfo>, Collection<ParsedCodeMetaInfo>> {
    val (k1MetaInfos, k2MetaInfos, commonMetaInfos) = extractCommonMetaInfosSlow(erroneousK1MetaInfos, erroneousK2MetaInfos)

    val significantK1MetaInfo = k1MetaInfos.filter { tagInK1 ->
        commonMetaInfos.none { it.isOnSamePositionAs(tagInK1) }
    }
    val significantK2MetaInfo = k2MetaInfos.filter { tagInK2 ->
        commonMetaInfos.none { it.isOnSamePositionAs(tagInK2) }
    }

    return significantK1MetaInfo to significantK2MetaInfo
}

fun extractCommonMetaInfos(
    allK1MetaInfos: Collection<ParsedCodeMetaInfo>,
    allK2MetaInfos: Collection<ParsedCodeMetaInfo>,
): Triple<MetaInfoTreeSet, MetaInfoTreeSet, MetaInfoTreeSet> {
    val k1MetaInfos = MetaInfoTreeSet().also {
        it.addAll(allK1MetaInfos)
    }

    val commonMetaInfos = MetaInfoTreeSet()
    val k2MetaInfos = MetaInfoTreeSet()

    for (metaInfo in allK2MetaInfos) {
        if (metaInfo in k1MetaInfos) {
            commonMetaInfos.add(metaInfo)
            k1MetaInfos.remove(metaInfo)
        } else {
            k2MetaInfos.add(metaInfo)
        }
    }

    return Triple(k1MetaInfos, k2MetaInfos, commonMetaInfos)
}

fun extractSignificantMetaInfos(
    erroneousK1MetaInfos: Collection<ParsedCodeMetaInfo>,
    erroneousK2MetaInfos: Collection<ParsedCodeMetaInfo>,
): Pair<Collection<ParsedCodeMetaInfo>, Collection<ParsedCodeMetaInfo>> {
    val (k1MetaInfos, k2MetaInfos, commonMetaInfos) = extractCommonMetaInfos(erroneousK1MetaInfos, erroneousK2MetaInfos)

    val significantK1MetaInfo = k1MetaInfos.filterNot { tagInK1 ->
        commonMetaInfos.hasDiagnosticsAt(tagInK1.start, tagInK1.end)
    }
    val significantK2MetaInfo = k2MetaInfos.filterNot { tagInK2 ->
        commonMetaInfos.hasDiagnosticsAt(tagInK2.start, tagInK2.end)
    }

    return significantK1MetaInfo to significantK2MetaInfo
}

val MAGIC_DIAGNOSTICS_THRESHOLD = 80

val k1DefinitelyNonErrors = collectAllK1NonErrors()
val k2DefinitelyNonErrors = collectAllK2NonErrors()

val ParsedCodeMetaInfo.isProbablyK1Error get() = !tag.startsWith("DEBUG_INFO_") && tag !in k1DefinitelyNonErrors
val ParsedCodeMetaInfo.isProbablyK2Error get() = !tag.startsWith("DEBUG_INFO_") && tag !in k2DefinitelyNonErrors

fun IntArray.getLineNumberForOffset(offset: Int): Int {
    return indexOfLast { it <= offset } + 1
}

fun IntArray.getLineNumberForOffsetBinary(offset: Int): Int {
    val index = binarySearch(offset)
    return when (val isInsertionPoint = index < 0) {
        isInsertionPoint -> -index - 1
        else -> index + 1
    }
}

fun ParsedCodeMetaInfo.replaceOffsetsWithLineNumbersWithin(lineStartOffsets: IntArray): ParsedCodeMetaInfo {
    val lineIndex = lineStartOffsets.getLineNumberForOffset(start)
    return ParsedCodeMetaInfo(lineIndex + 1, lineIndex + 1, attributes, tag, description)
}

val String.lineStartOffsets: IntArray
    get() {
        val buffer = mutableListOf<Int>()
        var currentOffset = 0

        split("\n").forEach { line ->
            buffer.add(currentOffset)
            currentOffset += line.length + 1
        }

        buffer.add(currentOffset)
        return buffer.toIntArray()
    }

fun hasNonIdenticalButEquivalentResults(
    alongsideNonIdenticalTest: File,
    differencesReportWriter: Writer,
    k1Text: String,
    allK1MetaInfos: List<ParsedCodeMetaInfo>,
    allK2MetaInfos: List<ParsedCodeMetaInfo>,
): Boolean {
    val (significantK1MetaInfo, significantK2MetaInfo) = when {
        allK1MetaInfos.size + allK2MetaInfos.size <= MAGIC_DIAGNOSTICS_THRESHOLD -> {
            extractSignificantMetaInfosSlow(allK1MetaInfos, allK2MetaInfos)
        }
        else -> {
            extractSignificantMetaInfos(allK1MetaInfos, allK2MetaInfos)
        }
    }

    val areEquivalent = significantK1MetaInfo.isEmpty() && significantK2MetaInfo.isEmpty()

    if (!areEquivalent) {
        val projectDirectory = System.getProperty("user.dir")
        val relativeTestPath = alongsideNonIdenticalTest.path.removePrefix(projectDirectory)
        val obsoleteFeatures = collectObsoleteDisabledLanguageFeatures(k1Text, relativeTestPath)

        if (obsoleteFeatures.isNotEmpty()) {
            differencesReportWriter.write("The `${relativeTestPath}` tests are not really equivalent, but they disable the `$obsoleteFeatures` features which K2 is not expected to support anyway, because they become stable before 2.0.\n\n")
            return true
        }

        differencesReportWriter.write("The `${relativeTestPath}` test:\n\n")

        for (it in significantK1MetaInfo) {
            differencesReportWriter.write("- `#potential-feature`: `${it.tag}` was in K1 at `(${it.start}..${it.end})`, but disappeared\n")
        }

        for (it in significantK2MetaInfo) {
            differencesReportWriter.write("- `#potential-breaking-change`: `${it.tag}` was introduced in K2 at `(${it.start}..${it.end})`\n")
        }

        differencesReportWriter.write("\n")
    }

    return areEquivalent
}

fun collectObsoleteDisabledLanguageFeatures(
    text: String,
    filePath: String,
): List<String> {
    val languageString = """// ?!?LANGUAGE:\s*(.*)""".toRegex().find(text)?.groupValues
        ?: return emptyList()

    return languageString[1].split("""\s+""".toRegex()).filter { featureString ->
        val matcher = LANGUAGE_FEATURE_PATTERN.matcher(featureString)
        if (!matcher.find()) {
            error("Invalid language feature pattern: $featureString (of ${languageString.first()} in $filePath)")
        }
        if (matcher.group(1) != "-") {
            return@filter false
        }
        val name = matcher.group(2)
        val feature = LanguageFeature.fromString(name) ?: error("No such language feature found: $name")
        feature.sinceVersion?.let { it <= LanguageVersion.KOTLIN_2_0 } == true
    }
}

val specTestCommentPattern = """/\*\n \* .*SPEC TEST""".toRegex()
val testCaseCommentPattern = """TESTCASE NUMBER""".toRegex()

fun fixMissingTestSpecComments(
    alongsideNonIdenticalTests: List<String>,
) {
    val status = StatusPrinter()

    val missingCommentsCount = alongsideNonIdenticalTests.count {
        status.loading("Checking SPEC TEST comments in $it", probability = 0.001)
        val k1Text = File(it).readText()
        val indexOfSpecTestInK1 = specTestCommentPattern.find(k1Text)?.range?.first ?: return@count false

        val k2File = File(it).analogousK2File
        val k2Text = k2File.readText()

        if ("SPEC TEST" in k2Text) {
            return@count false
        }

        status.loading("Writing SPEC TEST comments in $it")

        val indexOfTestCaseInK1 = testCaseCommentPattern.find(k1Text)?.range?.first
            ?: error("Couldn't find the TESTCASE comment in $it")
        val indexOfTestCaseInK2 = testCaseCommentPattern.find(k2Text)?.range?.first
            ?: error("Couldn't find the TESTCASE comment in ${k2File.path}")

        k2File.writeText(
            k2Text.substring(0, indexOfSpecTestInK1) +
                    k1Text.subSequence(indexOfSpecTestInK1, indexOfTestCaseInK1) +
                    k2Text.substring(indexOfTestCaseInK2)
        )

        true
    }

    status.done("$missingCommentsCount fir files had missing TEST SPEC comments")
}

fun main() {
    val projectDirectory = File(System.getProperty("user.dir"))
    val build = projectDirectory.child("compiler").child("k2-differences").child("build")

    val tests = deserializeOrGenerate(build.child("testsStats.json")) {
        collectTestsStats(projectDirectory)
    }

    fixMissingTestSpecComments(tests.alongsideNonIdenticalTests)
    val status = StatusPrinter()

    var alongsideNonEquivalentTestsCount = 0
    var alongsideNonSimilarTestsCount = 0

    build.child("similarity-report.md").bufferedWriter().use { similarity ->
        build.child("equivalence-report.md").bufferedWriter().use { equivalence ->
            for (it in tests.alongsideNonIdenticalTests) {
                status.loading("Checking $it", probability = 0.01)
                val test = File(it)

                val k1Text = test.readText()
                val k2Text = test.analogousK2File.readText()

                val allK1MetaInfos = CodeMetaInfoParser.getCodeMetaInfoFromText(k1Text).filter { it.isProbablyK1Error }
                val allK2MetaInfos = CodeMetaInfoParser.getCodeMetaInfoFromText(k2Text).filter { it.isProbablyK2Error }

                if (!hasNonIdenticalButEquivalentResults(test, equivalence, k1Text, allK1MetaInfos, allK2MetaInfos)) {
                    alongsideNonEquivalentTestsCount++
                }

                val k1LineStartOffsets = clearTextFromDiagnosticMarkup(k1Text).lineStartOffsets
                val k2LineStartOffsets = clearTextFromDiagnosticMarkup(k2Text).lineStartOffsets

                val allK1LineMetaInfos = allK1MetaInfos.map { it.replaceOffsetsWithLineNumbersWithin(k1LineStartOffsets) }
                val allK2LineMetaInfos = allK2MetaInfos.map { it.replaceOffsetsWithLineNumbersWithin(k2LineStartOffsets) }

                if (!hasNonIdenticalButEquivalentResults(test, similarity, k1Text, allK1LineMetaInfos, allK2LineMetaInfos)) {
                    alongsideNonSimilarTestsCount++
                }
            }
        }
    }

    status.done("Found $alongsideNonEquivalentTestsCount non-equivalences among alongside tests")
    status.done("Found $alongsideNonSimilarTestsCount non-similarities among alongside tests")
    print("")
}
