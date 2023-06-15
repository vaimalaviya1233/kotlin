/*
 * Copyright 2010-2023 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */

import com.intellij.openapi.util.text.StringUtilRt.convertLineSeparators
import org.jetbrains.kotlin.library.abi.internal.*
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.fail

class MockLibraryAbiReaderTest {
    private val fakeLibrary = File(System.getProperty("user.dir")).resolve("fake-library.klib")

    private fun readAbiTextFile(fileName: String): String = this::class.java.getResourceAsStream(fileName)
        ?.bufferedReader()
        ?.use { it.readText() }
        ?.let { convertLineSeparators(it) }
        ?: fail("Can't read ABI text file: $fileName")

    @Test
    fun testDefaultRendererWithSignaturesV1() {
        val expectedAbiText = readAbiTextFile("mock-klib-abi-dump-v1.txt")

        val libraryAbi = LibraryAbiReader.readAbiInfo(fakeLibrary)
        val actualAbiText = libraryAbi.topLevelDeclarations.renderTopLevels(
            AbiRenderingSettings(renderedSignatureVersions = setOf(AbiSignatureVersion.V1))
        )

        assertEquals(expectedAbiText, actualAbiText)
    }

    @Test
    fun testDefaultRendererWithSignaturesV2() {
        val expectedAbiText = readAbiTextFile("mock-klib-abi-dump-v2.txt")

        val libraryAbi = LibraryAbiReader.readAbiInfo(fakeLibrary)
        val actualAbiText = libraryAbi.topLevelDeclarations.renderTopLevels(
            AbiRenderingSettings(renderedSignatureVersions = setOf(AbiSignatureVersion.V2))
        )

        assertEquals(expectedAbiText, actualAbiText)
    }

    @Test
    fun testDefaultRendererWithSignaturesV1V2() {
        val expectedAbiText = readAbiTextFile("mock-klib-abi-dump-v1v2.txt")

        val libraryAbi = LibraryAbiReader.readAbiInfo(fakeLibrary)
        val actualAbiText = libraryAbi.topLevelDeclarations.renderTopLevels(
            AbiRenderingSettings(renderedSignatureVersions = setOf(AbiSignatureVersion.V1, AbiSignatureVersion.V2))
        )

        assertEquals(expectedAbiText, actualAbiText)
    }

    @Test
    fun testManifestInfo() {
        val manifest = LibraryAbiReader.readAbiInfo(fakeLibrary).manifest

        with(manifest) {
            assertEquals("fake-library", uniqueName)
            assertEquals("NATIVE", platform)
            assertEquals(setOf("ios_arm64"), nativeTargets)
            assertEquals("1.9.20", compilerVersion)
            assertEquals("1.8.0", abiVersion)
            assertEquals("0.0.1", libraryVersion)
            assertEquals("fake-ir-provider", irProvider)
        }
    }
}