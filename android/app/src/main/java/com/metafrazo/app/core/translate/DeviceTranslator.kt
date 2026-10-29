package com.metafrazo.app.core.translate

import com.google.mlkit.common.model.DownloadConditions
import com.google.mlkit.nl.translate.TranslateLanguage
import com.google.mlkit.nl.translate.Translation
import com.google.mlkit.nl.translate.Translator
import com.google.mlkit.nl.translate.TranslatorOptions
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume

/**
 * On-device translation via ML Kit. Works without the Python AI service; models are
 * downloaded once per language pair and then run offline.
 */
/** Outcome of translating a single string. */
sealed interface TranslateResult {
    /** Text was translated to the requested target. */
    data class Translated(val text: String) : TranslateResult
    /** Source and target languages are the same; [text] is the original. */
    data class SameLanguage(val text: String) : TranslateResult
    /** The language pair is not supported; [text] is the original. */
    data class Unsupported(val text: String) : TranslateResult
    /** Translation failed at runtime; [text] is the original, [reason] explains why. */
    data class Failed(val text: String, val reason: String) : TranslateResult
}

object DeviceTranslator {
    val supported = listOf("en", "ta", "hi")

    private val translators = mutableMapOf<String, Translator>()
    private val lock = Any()

    private fun mlLang(code: String): String? = when (code) {
        "en" -> TranslateLanguage.ENGLISH
        "ta" -> TranslateLanguage.TAMIL
        "hi" -> TranslateLanguage.HINDI
        else -> null
    }

    private fun translator(source: String, target: String): Translator? {
        val s = mlLang(source) ?: return null
        val t = mlLang(target) ?: return null
        val key = "$s>$t"
        return synchronized(lock) {
            translators.getOrPut(key) {
                Translation.getClient(TranslatorOptions.Builder().setSourceLanguage(s).setTargetLanguage(t).build())
            }
        }
    }

    /** Downloads the model for a pair if needed. Returns null on success, else an error message. */
    suspend fun prepare(source: String, target: String): String? {
        if (source == target) return null
        val tr = translator(source, target) ?: return "Unsupported language pair $source>$target"
        return suspendCancellableCoroutine { cont ->
            tr.downloadModelIfNeeded(DownloadConditions.Builder().build())
                .addOnSuccessListener { cont.resume(null) }
                .addOnFailureListener { cont.resume("Model download failed: ${it.message}") }
        }
    }

    /** Translates text, returning the original on any failure so captions never disappear. */
    suspend fun translate(text: String, source: String, target: String): String =
        when (val r = translateResult(text, source, target)) {
            is TranslateResult.Translated -> r.text
            is TranslateResult.SameLanguage -> r.text
            is TranslateResult.Unsupported -> r.text
            is TranslateResult.Failed -> r.text
        }

    /** Translates text and reports the outcome so callers can distinguish real translations from fallbacks. */
    suspend fun translateResult(text: String, source: String, target: String): TranslateResult {
        if (text.isBlank() || source == target) return TranslateResult.SameLanguage(text)
        val tr = translator(source, target) ?: return TranslateResult.Unsupported(text)
        return suspendCancellableCoroutine { cont ->
            tr.translate(text)
                .addOnSuccessListener { cont.resume(TranslateResult.Translated(it)) }
                .addOnFailureListener { cont.resume(TranslateResult.Failed(text, it.message ?: "translation failed")) }
        }
    }

    fun close() {
        synchronized(lock) {
            translators.values.forEach { it.close() }
            translators.clear()
        }
    }
}
