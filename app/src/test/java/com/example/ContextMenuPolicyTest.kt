package com.example

import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * Architecture & Design System Policy Test:
 * Enforces that all context menus and dropdown menus in the application
 * remain strictly text-only without icons (no leadingIcon, no trailingIcon, no Icon inside text slot).
 */
class ContextMenuPolicyTest {

    @Test
    fun testNoIconsInDropdownMenuItems() {
        val rootCandidates = listOf(
            File("src/main/java"),
            File("app/src/main/java"),
            File("../app/src/main/java")
        )
        val sourceDir = rootCandidates.find { it.exists() && it.isDirectory }
        assertTrue("Could not locate source directory for lint test", sourceDir != null)

        val violations = mutableListOf<String>()

        sourceDir!!.walkTopDown()
            .filter { it.isFile && it.extension == "kt" }
            .forEach { file ->
                val text = file.readText()
                val target = "DropdownMenuItem"
                var idx = 0

                while (true) {
                    val pos = text.find(target, idx)
                    if (pos == -1) break

                    val startParen = text.find("(", pos)
                    if (startParen == -1) break

                    var depth = 0
                    var endParen = -1
                    var inString = false
                    var escape = false

                    // Scan balanced parentheses matching the DropdownMenuItem invocation
                    for (i in startParen until text.length) {
                        val c = text[i]
                        if (escape) {
                            escape = false
                            continue
                        }
                        if (c == '\\') {
                            escape = true
                            continue
                        }
                        if (c == '"') {
                            inString = !inString
                            continue
                        }
                        if (!inString) {
                            if (c == '(') depth++
                            else if (c == ')') {
                                depth--
                                if (depth == 0) {
                                    endParen = i
                                    break
                                }
                            }
                        }
                    }

                    if (endParen != -1) {
                        val body = text.substring(startParen + 1, endParen)
                        val lineNo = text.substring(0, pos).count { it == '\n' } + 1

                        if (body.contains("leadingIcon") || body.contains("trailingIcon") || body.contains("Icon(")) {
                            violations.add("${file.name}:$lineNo - Contains icon in DropdownMenuItem: ${body.trim().take(80)}...")
                        }
                        idx = endParen + 1
                    } else {
                        idx = pos + target.length
                    }
                }
            }

        assertTrue(
            "Policy Violation: Found icons inside context/dropdown menus:\n" + violations.joinToString("\n"),
            violations.isEmpty()
        )
    }

    private fun String.find(sub: String, startIndex: Int): Int = indexOf(sub, startIndex)
}
