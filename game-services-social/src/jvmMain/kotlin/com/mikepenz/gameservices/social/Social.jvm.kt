package com.mikepenz.gameservices.social

import com.mikepenz.gameservices.GameServicesPlatform

public fun createSocialClient(): SocialClient = UnsupportedSocialClient(GameServicesPlatform.JVM)
