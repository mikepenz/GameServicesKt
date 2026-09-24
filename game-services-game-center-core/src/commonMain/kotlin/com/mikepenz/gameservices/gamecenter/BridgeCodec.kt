@file:OptIn(com.mikepenz.gameservices.InternalGameServicesApi::class)
package com.mikepenz.gameservices.gamecenter

import com.mikepenz.gameservices.*
import kotlinx.serialization.json.*

@InternalGameServicesApi
public fun encodeBridgeRows(rows: List<List<String>>): String = JsonArray(rows.map { row -> JsonArray(row.map(::JsonPrimitive)) }).toString()

@InternalGameServicesApi
public fun decodeBridgeRows(value: String): List<List<String>> = Json.parseToJsonElement(value).jsonArray.map { row ->
    row.jsonArray.map { element ->
        val string = element.jsonPrimitive
        require(string.isString) { "Invalid native bridge string" }
        string.content
    }
}

@InternalGameServicesApi
public fun bridgeError(error: Throwable): String = encodeBridgeRows(listOf(when (error) {
    GameServicesException.AuthenticationRequired -> listOf("authentication")
    GameServicesException.PermissionRequired -> listOf("permission")
    GameServicesException.ConfigurationMissing -> listOf("configuration")
    GameServicesException.UserCancelled -> listOf("cancelled")
    is GameServicesException.UnsupportedOperation -> listOf("unsupported", error.operation.name)
    is GameServicesException.ProviderFailure -> listOf("provider", error.code)
    is IllegalArgumentException -> listOf("argument", error.message.orEmpty())
    else -> listOf("provider", error::class.simpleName.orEmpty())
}))

@InternalGameServicesApi
public fun decodeBridgeError(value: String): Exception {
    val row = decodeBridgeRows(value).single()
    return when (row.first()) {
        "authentication" -> GameServicesException.AuthenticationRequired
        "permission" -> GameServicesException.PermissionRequired
        "configuration" -> GameServicesException.ConfigurationMissing
        "cancelled" -> GameServicesException.UserCancelled
        "unsupported" -> GameServicesException.UnsupportedOperation(GameServicesProvider.GameCenter, GameServicesOperation.valueOf(row[1]))
        "argument" -> IllegalArgumentException(row[1])
        "provider" -> GameServicesException.ProviderFailure(GameServicesProvider.GameCenter, row[1])
        else -> error("Invalid native bridge error")
    }
}
