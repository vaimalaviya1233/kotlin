/*
 * Copyright 2010-2015 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.jetbrains.kotlin.utils

// Needed for Java interop: otherwise you need to specify all the optional parameters to join, i.e. prefix, postfix, limit, truncated
fun join(collection: Iterable<Any>, separator: String) = collection.joinToString(separator)

// Needed for Java interop - the same reasoning as above
fun split(string: String, separator: String) = string.split(separator)

// Below are functions copied with conversion and minor modifications from com.intellij.openapi.util.textStringUtil
// they are used in the Kotlin codebase when there are no good alternative in the stdlib
fun escapeStringCharacters(s: String): String {
    val buffer = java.lang.StringBuilder(s.length)
    escapeStringCharacters(s.length, s, "\"", buffer)
    return buffer.toString()
}

fun unescapeStringCharacters(s: String): String {
    val buffer = java.lang.StringBuilder(s.length)
    unescapeStringCharacters(s.length, s, buffer)
    return buffer.toString()
}

fun escapeStringCharacters(
    length: Int,
    str: String,
    additionalChars: String?,
    buffer: java.lang.StringBuilder
): java.lang.StringBuilder {
    return escapeStringCharacters(length, str, additionalChars, true, buffer)
}

fun escapeStringCharacters(
    length: Int,
    str: String,
    additionalChars: String?,
    escapeSlash: Boolean,
    buffer: java.lang.StringBuilder
): java.lang.StringBuilder {
    return escapeStringCharacters(length, str, additionalChars, escapeSlash, true, buffer)
}

fun escapeStringCharacters(
    length: Int,
    str: String,
    additionalChars: String?,
    escapeSlash: Boolean,
    escapeUnicode: Boolean,
    buffer: java.lang.StringBuilder
): java.lang.StringBuilder {
    var prev = 0.toChar()
    for (idx in 0 until length) {
        val ch = str[idx]
        when (ch) {
            '\b' -> buffer.append("\\b")
            '\t' -> buffer.append("\\t")
            '\n' -> buffer.append("\\n")
            '\u000c' -> buffer.append("\\u000c")
            '\r' -> buffer.append("\\r")
            else -> if (escapeSlash && ch == '\\') {
                buffer.append("\\\\")
            } else if (additionalChars != null && additionalChars.indexOf(ch) > -1 && (escapeSlash || prev != '\\')) {
                buffer.append("\\").append(ch)
            } else if (escapeUnicode && !isPrintableUnicode(ch)) {
                val hexCode: CharSequence = Integer.toHexString(ch.code).uppercase()
                buffer.append("\\u")
                var paddingCount = 4 - hexCode.length
                while (paddingCount-- > 0) {
                    buffer.append(0)
                }
                buffer.append(hexCode)
            } else {
                buffer.append(ch)
            }
        }
        prev = ch
    }
    return buffer
}

private fun isPrintableUnicode(c: Char): Boolean {
    val t = Character.getType(c)
    return t != Character.UNASSIGNED.toInt() && t != Character.LINE_SEPARATOR.toInt() && t != Character.PARAGRAPH_SEPARATOR.toInt() &&
            t != Character.CONTROL.toInt() && t != Character.FORMAT.toInt() && t != Character.PRIVATE_USE.toInt() &&
            t != Character.SURROGATE.toInt()
}


private fun unescapeStringCharacters(length: Int, s: String, buffer: StringBuilder) {

    fun isOctalDigit(c: Char): Boolean = '0' <= c && c <= '7'

    var escaped = false
    var idx = 0
    while (idx < length) {
        val ch = s[idx]
        if (!escaped) {
            if (ch == '\\') {
                escaped = true
            } else {
                buffer.append(ch)
            }
        } else {
            var octalEscapeMaxLength = 2
            when (ch) {
                'n' -> buffer.append('\n')
                'r' -> buffer.append('\r')
                'b' -> buffer.append('\b')
                't' -> buffer.append('\t')
                'f' -> buffer.append('\u000c')
                '\'' -> buffer.append('\'')
                '\"' -> buffer.append('\"')
                '\\' -> buffer.append('\\')
                'u' -> if (idx + 4 < length) {
                    try {
                        val code = s.substring(idx + 1, idx + 5).toInt(16)
                        idx += 4
                        buffer.append(code.toChar())
                    } catch (e: NumberFormatException) {
                        buffer.append("\\u")
                    }
                } else {
                    buffer.append("\\u")
                }
                '0', '1', '2', '3' -> {
                    octalEscapeMaxLength = 3
                    var escapeEnd = idx + 1
                    while (escapeEnd < length && escapeEnd < idx + octalEscapeMaxLength && isOctalDigit(s[escapeEnd])) 
                        escapeEnd++
                    try {
                        buffer.append(s.substring(idx, escapeEnd).toInt(8).toChar())
                    } catch (e: NumberFormatException) {
                        throw RuntimeException("Couldn't parse " + s.substring(idx, escapeEnd), e) // shouldn't happen
                    }
                    idx = escapeEnd - 1
                }
                '4', '5', '6', '7' -> {
                    var escapeEnd = idx + 1
                    while (escapeEnd < length && escapeEnd < idx + octalEscapeMaxLength && isOctalDigit(s[escapeEnd])) 
                        escapeEnd++
                    try {
                        buffer.append(s.substring(idx, escapeEnd).toInt(8).toChar())
                    } catch (e: NumberFormatException) {
                        throw RuntimeException("Couldn't parse " + s.substring(idx, escapeEnd), e)
                    }
                    idx = escapeEnd - 1
                }
                else -> buffer.append(ch)
            }
            escaped = false
        }
        idx++
    }
    if (escaped) buffer.append('\\')
}

// Copied with conversion from the com.intellij.openapi.util.text.StringUtil.replace
// There seems no comparable utility in the kotlin stdlib or other codebase, that can be used insted of this code
fun String.replaceAll(from: List<String>, to: List<String>): String {
    assert(from.size == to.size)
    var result: java.lang.StringBuilder? = null
    var i = 0
    replace@ while (i < length) {
        var j = 0
        while (j < from.size) {
            val toReplace = from[j]
            val replaceWith = to[j]
            val len = toReplace.length
            if (len == 0) {
                j += 1
                continue
            }
            if (regionMatches(i, toReplace, 0, len)) {
                if (result == null) {
                    result = java.lang.StringBuilder(length)
                    result.append(this, 0, i)
                }
                result.append(replaceWith)
                i += len - 1
                i++
                continue@replace
            }
            j += 1
        }
        result?.append(this[i])
        i++
    }
    return result?.toString() ?: this
}


// from StringUtil.first
fun String.trimTextToSize(maxLength: Int, appendEllipsis: Boolean): String {
    return if (length > maxLength) substring(0, maxLength) + (if (appendEllipsis) "..." else "") else this
}

fun String.shortenText(maxLength: Int, suffixLength: Int, ellipsisSymbol: String = "\u2026"): String {
    val textLength = length
    return if (textLength > maxLength) {
        val prefixLength = maxLength - suffixLength - ellipsisSymbol.length
        assert(prefixLength >= 0)
        substring(0, prefixLength) + ellipsisSymbol + substring(textLength - suffixLength)
    } else {
        this
    }
}
