package com.coooolfan.unirhy.sync.service

import org.springframework.stereotype.Component

fun interface PlaybackSyncTimeProvider {
    fun nowMs(): Long
}

@Component
class SystemPlaybackSyncTimeProvider : PlaybackSyncTimeProvider {
    override fun nowMs(): Long = System.currentTimeMillis()
}
