package com.metafrazo.app.core.auth

import android.content.Context
import android.content.SharedPreferences
import com.metafrazo.app.core.network.Api
import java.util.UUID

/**
 * Auth groundwork: stores a (Firebase) ID token when available; otherwise runs in guest/dev mode.
 * Real Firebase/Credential Manager integration lands alongside google-services config.
 */
object AuthRepository {
    private const val PREFS = "metafrazo_auth"
    private const val KEY_TOKEN = "id_token"
    private const val KEY_DISPLAY = "display_name"
    private const val KEY_DEVICE_ID = "dev_device_id"

    private fun prefs(ctx: Context): SharedPreferences =
        ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun init(ctx: Context) {
        Api.authToken = prefs(ctx).getString(KEY_TOKEN, null)
        Api.devUserId = deviceId(ctx)
        Api.devUserName = meetingName(ctx)
    }

    /** Stable per-install id used as the dev identity so two devices never share a LiveKit identity. */
    fun deviceId(ctx: Context): String {
        val p = prefs(ctx)
        p.getString(KEY_DEVICE_ID, null)?.let { return it }
        val id = "dev-" + UUID.randomUUID().toString().replace("-", "").take(12)
        p.edit().putString(KEY_DEVICE_ID, id).apply()
        return id
    }

    fun isSignedIn(ctx: Context): Boolean =
        !prefs(ctx).getString(KEY_TOKEN, null).isNullOrBlank()

    fun displayName(ctx: Context): String =
        prefs(ctx).getString(KEY_DISPLAY, if (isSignedIn(ctx)) "Signed in" else "Guest (dev)").orEmpty()

    /** Name shown to other participants; empty when the user hasn't set one yet. */
    fun meetingName(ctx: Context): String =
        prefs(ctx).getString(KEY_DISPLAY, null)?.takeUnless { it == "Guest (dev)" || it == "Signed in" }.orEmpty()

    fun setMeetingName(ctx: Context, name: String) {
        val clean = name.trim().take(40)
        if (clean.isEmpty()) return
        prefs(ctx).edit().putString(KEY_DISPLAY, clean).apply()
        Api.devUserName = clean
    }

    fun setToken(ctx: Context, token: String?, displayName: String? = null) {
        prefs(ctx).edit().apply {
            if (token.isNullOrBlank()) remove(KEY_TOKEN) else putString(KEY_TOKEN, token)
            displayName?.let { putString(KEY_DISPLAY, it) }
        }.apply()
        Api.authToken = token
    }

    fun signOut(ctx: Context) = setToken(ctx, null, "Guest (dev)")
}
