package com.metafrazo.app.feature.captions

/**
 * Pure helper describing which language pairs a caption needs, independent of
 * any Android/ML Kit dependency so it can be unit tested.
 */
object TranslationPlan {
    /**
     * Target languages that require translation from [source], given the full
     * [supported] set. The source language is never a translation target.
     */
    fun targetsFor(source: String, supported: List<String>): List<String> =
        supported.filter { it != source }

    /**
     * Merge successful translations with the original. Failed pairs are omitted
     * from [translations] by the caller; the source language always maps to the
     * original text so a caption is never empty.
     */
    fun merge(
        source: String,
        original: String,
        translations: Map<String, String>,
    ): Map<String, String> = buildMap {
        putAll(translations)
        put(source, original)
    }
}
