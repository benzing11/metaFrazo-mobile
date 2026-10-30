package com.metafrazo.app.feature.captions

/**
 * Pure helper for appending or updating captions in-place without duplicates.
 * Enables low-latency progressive delivery: a quick initial caption can be
 * broadcast immediately, followed by updated translations when ready.
 */
object CaptionUpsert {
    fun upsert(
        existing: List<CaptionItem>,
        incoming: CaptionItem,
        maxItems: Int = 200,
    ): List<CaptionItem> {
        val index = existing.indexOfFirst {
            it.speaker == incoming.speaker && it.ts == incoming.ts
        }
        val list = if (index >= 0) {
            existing.toMutableList().apply {
                // Merge translations if the incoming item has new ones
                val mergedTranslations = existing[index].translations + incoming.translations
                this[index] = incoming.copy(translations = mergedTranslations)
            }
        } else {
            existing + incoming
        }
        return list.takeLast(maxItems)
    }
}
