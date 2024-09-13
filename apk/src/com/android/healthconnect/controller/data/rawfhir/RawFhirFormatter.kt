/*
 * Copyright (C) 2024 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except
 * in compliance with the License. You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the License
 * is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express
 * or implied. See the License for the specific language governing permissions and limitations under
 * the License.
 */
package com.android.healthconnect.controller.data.rawfhir

import android.health.connect.datatypes.FhirResource
import javax.inject.Singleton

/** Formatter for printing raw FHIR data. */
@Singleton
class RawFhirFormatter {
    /**
     * Formats the whole FHIR resource string into a more readable format with proper indentation
     * and line breaks.
     */
    fun format(fhirResource: FhirResource): String {
        val indentFactor = 4
        val indent = " ".repeat(indentFactor)
        val result = StringBuilder()
        var level = 0
        var inQuote = false
        var escape = false
        val fhirString = fhirResource.data

        if (!isValidJson(fhirString)) {
            return fhirString
        }

        for (char in fhirString) {
            when (char) {
                '"' -> {
                    result.append(char)
                    if (!escape) {
                        inQuote = !inQuote
                    }
                }
                '\\' -> {
                    result.append(char)
                    escape = !escape
                    continue
                }
                '{',
                '[' -> {
                    result.append(char)
                    if (!inQuote) {
                        result.append("\n")
                        level++
                        result.append(indent.repeat(level))
                    }
                }
                '}',
                ']' -> {
                    if (!inQuote) {
                        result.append("\n")
                        level--
                        result.append(indent.repeat(level))
                    }
                    result.append(char)
                }
                ',' -> {
                    result.append(char)
                    if (!inQuote) {
                        result.append("\n")
                        result.append(indent.repeat(level))
                    }
                }
                ':' -> {
                    result.append(char)
                    if (!inQuote) {
                        result.append(" ")
                    }
                }
                else -> result.append(char)
            }
            escape = false
        }

        return result.toString()
    }

    private fun isValidJson(input: String): Boolean {
        if (input.isEmpty()) return false

        val trimmedInput = input.trim()

        // Check for validity of start and end characters.
        if (
            !(trimmedInput.startsWith("{") && trimmedInput.endsWith("}")) &&
                !(trimmedInput.startsWith("[") && trimmedInput.endsWith("]"))
        ) {
            return false
        }

        var inQuotes = false
        var escape = false
        var brackets = 0

        for (char in trimmedInput) {
            when {
                escape -> escape = false
                char == '\\' -> escape = true
                char == '"' -> inQuotes = !inQuotes
                !inQuotes -> {
                    when (char) {
                        '{',
                        '[' -> brackets++
                        '}',
                        ']' -> {
                            if (brackets == 0) return false
                            brackets--
                        }
                    }
                }
            }
        }

        return brackets == 0 && !inQuotes
    }
}
