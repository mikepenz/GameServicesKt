package com.mikepenz.gameservices.recall

import com.mikepenz.gameservices.ExperimentalGameServicesApi

/** Sensitive opaque token for the game's server; never a player ID or login proof. */
@ExperimentalGameServicesApi
public class RecallSession public constructor(public val sessionId: String) {
    init { require(sessionId.isNotBlank()) }
    override fun toString(): String = "RecallSession(<redacted>)"
}

@ExperimentalGameServicesApi
public interface RecallClient {
    public suspend fun requestRecallAccess(): Result<RecallSession>
}
