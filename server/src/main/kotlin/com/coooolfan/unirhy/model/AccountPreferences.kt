package com.coooolfan.unirhy.model

import org.babyfish.jimmer.sql.Serialized

@Serialized
data class AccountPreferences(
    val playbackPreference: PlaybackPreference = PlaybackPreference.OPUS,
)
