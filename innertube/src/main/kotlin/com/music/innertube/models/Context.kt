package com.music.innertube.models

import kotlinx.serialization.Serializable

@Serializable
data class Context(
    val client: Client,
    val thirdParty: ThirdParty? = null,
    val request: Request = Request(),
    val user: User = User()
) {
    @Serializable
    data class Client(
        val clientName: String,
        val clientVersion: String,
        val osName: String? = null,
        val osVersion: String? = null,
        val deviceMake: String? = null,
        val deviceModel: String? = null,
        val androidSdkVersion: String? = null,
        val gl: String,
        val hl: String,
        val visitorData: String?,
        /** "EMBED" for the embedded player clients; null (not serialised, explicitNulls = false) for every other client. */
        val clientScreen: String? = null,
    )

    @Serializable
    data class ThirdParty(
        val embedUrl: String,
    )

    /** This context as an embedded player sends it: a third-party [embedUrl] and the EMBED client screen. */
    fun asEmbedded(embedUrl: String): Context =
        copy(client = client.copy(clientScreen = EMBED_CLIENT_SCREEN), thirdParty = ThirdParty(embedUrl = embedUrl))

    companion object {
        const val EMBED_CLIENT_SCREEN = "EMBED"
    }

    @Serializable
    data class Request(
        val internalExperimentFlags: Array<String> = emptyArray(),
        val useSsl: Boolean = true,
    )

    @Serializable
    data class User(
        val lockedSafetyMode: Boolean = false,
        val onBehalfOfUser: String? = null,
    )
}
