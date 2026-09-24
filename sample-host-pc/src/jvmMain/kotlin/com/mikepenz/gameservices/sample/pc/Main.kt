@file:OptIn(com.mikepenz.gameservices.ExperimentalGameServicesApi::class)
package com.mikepenz.gameservices.sample.pc

import com.mikepenz.gameservices.playgames.pc.PlayGamesPcBackend
import kotlinx.coroutines.runBlocking

fun main(args: Array<String>): Unit = runBlocking {
    require(args.size == 1) { "Supply the absolute path to gs_play_pc.dll" }
    val backend = PlayGamesPcBackend(args.single())
    try {
        val initialization = backend.initialize()
        if (initialization.isFailure) {
            System.err.println(initialization.exceptionOrNull()?.message)
            return@runBlocking
        }
        backend.requestRecallAccess().fold(
            onSuccess = { println("Recall session received. Pass it securely to the game's server; do not log it.") },
            onFailure = { System.err.println(it.message) },
        )
    } finally { backend.close() }
}
