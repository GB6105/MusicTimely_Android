package com.gb6105.musictimely.domain.model.link

import com.gb6105.musictimely.domain.model.source.Provider
import java.net.URI
import java.net.URISyntaxException

sealed interface LinkValidation {
    data class Accepted(val provider: Provider, val normalizedUrl: String) : LinkValidation

    /** https but outside the allowlist → offer app-only (FR-A024). */
    data object Unsupported : LinkValidation

    /** Anything but https: javascript:, file:, intent:, custom schemes (FR-A029). */
    data object RejectedScheme : LinkValidation

    data object NoLink : LinkValidation
}

/** Link allowlist and normalisation (SRS Android §7.5, DEC-A09). Redirects are never followed here. */
object MusicLinkPolicy {
    private val HOSTS = mapOf(
        "music.youtube.com" to Provider.YOUTUBE_MUSIC,
        "open.spotify.com" to Provider.SPOTIFY,
        "spotify.link" to Provider.SPOTIFY,
        "music.apple.com" to Provider.APPLE_MUSIC,
    )
    private val TRACKING = setOf("si", "feature", "at", "ct", "ls", "itscg", "itsct")
    private val URL_START = Regex("""^(?:[a-zA-Z][a-zA-Z0-9+.\-]*://|(?:javascript|data|intent|file|content):)""", RegexOption.IGNORE_CASE)
    private const val LEADING = "(<[{\"'"
    private const val TRAILING = ")>]}\"'.,!?;:"

    fun extractFirstUrl(text: String): String? =
        text.split(Regex("""\s+"""))
            .map { it.trimStart { c -> c in LEADING }.trimEnd { c -> c in TRAILING } }
            .firstOrNull { URL_START.containsMatchIn(it) }

    fun fromSharedText(text: String): LinkValidation =
        extractFirstUrl(text)?.let(::validate) ?: LinkValidation.NoLink

    fun validate(raw: String): LinkValidation {
        val trimmed = raw.trim()
        if (!trimmed.startsWith("https://", ignoreCase = true)) return LinkValidation.RejectedScheme
        val uri = try {
            URI(trimmed)
        } catch (_: URISyntaxException) {
            return LinkValidation.Unsupported
        }
        val host = uri.host?.lowercase() ?: return LinkValidation.Unsupported
        if (uri.rawUserInfo != null || uri.port !in setOf(-1, 443)) return LinkValidation.Unsupported
        val provider = HOSTS[host] ?: return LinkValidation.Unsupported
        return LinkValidation.Accepted(provider, normalize(host, uri))
    }

    private fun normalize(host: String, uri: URI): String {
        val kept = uri.rawQuery.orEmpty().split('&')
            .filter { it.isNotEmpty() }
            .filterNot { param -> param.substringBefore('=').lowercase().let { it in TRACKING || it.startsWith("utm_") } }
        val query = if (kept.isEmpty()) "" else kept.joinToString("&", prefix = "?")
        return "https://$host${uri.rawPath.orEmpty()}$query"
    }
}
