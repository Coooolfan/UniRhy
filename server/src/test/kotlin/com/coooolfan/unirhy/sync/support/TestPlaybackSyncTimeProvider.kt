package com.coooolfan.unirhy.sync.support

import com.coooolfan.unirhy.sync.service.PlaybackSyncTimeProvider

class TestPlaybackSyncTimeProvider(
    private var currentNowMs: Long,
) : PlaybackSyncTimeProvider {
    override fun nowMs(): Long = currentNowMs

    fun setNowMs(nowMs: Long) {
        currentNowMs = nowMs
    }

    fun advanceBy(deltaMs: Long) {
        currentNowMs += deltaMs
    }
}
