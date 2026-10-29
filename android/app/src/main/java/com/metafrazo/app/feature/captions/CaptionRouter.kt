package com.metafrazo.app.feature.captions

/**
 * A single caption message to send: the translations map it carries and the
 * recipients it is addressed to. A null [identities] means broadcast to everyone.
 */
data class CaptionDelivery(
    val translations: Map<String, String>,
    /** Target recipient identities, or null to broadcast to the whole room. */
    val identities: List<String>?,
)

/**
 * Pure routing logic for per-recipient caption delivery, kept free of LiveKit types
 * so it can be unit tested. Given the known recipient language preferences, the set
 * of remote participant identities, and the available translations, it produces the
 * minimal set of messages to publish:
 *
 *  - one targeted message per distinct known language, carrying only that language;
 *  - one broadcast-style fallback carrying every translation for recipients whose
 *    preference is unknown, so captions never disappear.
 */
object CaptionRouter {
    fun plan(
        sourceLang: String,
        original: String,
        translations: Map<String, String>,
        remoteIdentities: Collection<String>,
        prefs: Map<String, String>,
    ): List<CaptionDelivery> {
        fun textFor(lang: String): String =
            if (lang == sourceLang) original else translations[lang] ?: original

        val knownIds = remoteIdentities.filter { prefs.containsKey(it) }
        val unknownIds = remoteIdentities.filter { !prefs.containsKey(it) }

        val deliveries = mutableListOf<CaptionDelivery>()

        // Targeted per-language messages for recipients whose preference we know.
        knownIds.groupBy { prefs.getValue(it) }.forEach { (lang, ids) ->
            deliveries += CaptionDelivery(
                translations = mapOf(lang to textFor(lang)),
                identities = ids,
            )
        }

        // Fallback: anyone unknown, or an empty room, gets the full set.
        if (unknownIds.isNotEmpty() || remoteIdentities.isEmpty()) {
            val withOriginal = translations.toMutableMap().apply {
                putIfAbsent(sourceLang, original)
            }
            deliveries += CaptionDelivery(
                translations = withOriginal,
                // Narrow to the unknown set only when some recipients are already known.
                identities = if (knownIds.isEmpty()) null else unknownIds,
            )
        }

        return deliveries
    }
}
