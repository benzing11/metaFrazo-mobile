package com.metafrazo.app.core.focus

import java.util.regex.Pattern

/**
 * Lightweight heuristic focus assistant: pulls likely action items, decisions, and mentions from text.
 */
object FocusAssistant {
    data class Item(val type: String, val text: String)

    private val actionPatterns = listOf(
        "need to", "should", "must", "action item", "todo", "will do", "assign", "deadline", "by tomorrow", "by next"
    )

    fun analyze(text: String): List<Item> {
        val items = mutableListOf<Item>()
        val sentences = text.split(Regex("(?<=[.!?])\\s+"))
        for (s in sentences) {
            val lower = s.lowercase()
            when {
                actionPatterns.any { lower.contains(it) } -> items.add(Item("action", s.trim()))
                lower.startsWith("decide") || lower.contains("decided") -> items.add(Item("decision", s.trim()))
            }
        }
        if (items.isEmpty() && text.isNotBlank()) {
            // fallback: top 3 non-empty sentences
            sentences.filter { it.isNotBlank() }.take(3).forEach { items.add(Item("note", it.trim())) }
        }
        return items
    }
}