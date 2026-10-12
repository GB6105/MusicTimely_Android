package com.gb6105.musictimely.domain.usecase.source

import com.gb6105.musictimely.domain.model.link.LinkValidation
import com.gb6105.musictimely.domain.model.link.MusicLinkPolicy
import com.gb6105.musictimely.domain.model.source.ExternalSource
import com.gb6105.musictimely.domain.model.source.Provider
import com.gb6105.musictimely.domain.repository.ClockPort
import com.gb6105.musictimely.domain.repository.SourceRepository

sealed interface SourceCandidate {
    data class Link(val provider: Provider, val normalizedUrl: String) : SourceCandidate
    data object OfferAppOnly : SourceCandidate
    data object Rejected : SourceCandidate
}

/** MT-M14: share, paste or app-only. Only allowlisted links are stored (FR-A023·A024·A025, DEC-A09). */
class RegisterSourceUseCase(
    private val repository: SourceRepository,
    private val clock: ClockPort,
    private val newId: () -> String,
) {
    fun inspectSharedText(text: String): SourceCandidate = candidateOf(MusicLinkPolicy.fromSharedText(text))

    fun inspectLink(text: String): SourceCandidate = candidateOf(MusicLinkPolicy.validate(text))

    suspend fun saveLink(candidate: SourceCandidate.Link, label: String, defaultLabel: String = ""): ExternalSource {
        val packageName = requireNotNull(candidate.provider.packageName)
        return store(candidate.provider, packageName, candidate.normalizedUrl, label, defaultLabel)
    }

    suspend fun saveAppOnly(provider: Provider, label: String): ExternalSource {
        val packageName = requireNotNull(provider.packageName) { "provider without a package" }
        return store(provider, packageName, linkUri = null, label = label, defaultLabel = label)
    }

    private suspend fun store(provider: Provider, packageName: String, linkUri: String?, label: String, defaultLabel: String): ExternalSource {
        val source = ExternalSource(
            id = newId(),
            provider = provider,
            packageName = packageName,
            linkUri = linkUri,
            label = label.trim().ifEmpty { defaultLabel.trim() },
            createdAtEpochMs = clock.wallNowEpochMs(),
        )
        repository.save(source)
        return source
    }

    private fun candidateOf(validation: LinkValidation): SourceCandidate = when (validation) {
        is LinkValidation.Accepted -> SourceCandidate.Link(validation.provider, validation.normalizedUrl)
        LinkValidation.Unsupported -> SourceCandidate.OfferAppOnly
        LinkValidation.RejectedScheme, LinkValidation.NoLink -> SourceCandidate.Rejected
    }
}
