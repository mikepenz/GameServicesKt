package com.mikepenz.gameservices.gamecenter

import com.mikepenz.gameservices.InternalGameServicesApi
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.useContents
import kotlinx.cinterop.convert
import platform.Foundation.NSProcessInfo

@OptIn(ExperimentalForeignApi::class)
@InternalGameServicesApi
public fun gameCenterOsMajor(): Long = NSProcessInfo.processInfo.operatingSystemVersion.useContents { majorVersion.convert<Long>() }
