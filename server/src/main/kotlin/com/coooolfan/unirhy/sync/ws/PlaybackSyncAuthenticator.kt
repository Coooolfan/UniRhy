package com.coooolfan.unirhy.sync.ws

import cn.dev33.satoken.stp.StpUtil
import org.springframework.stereotype.Component

interface PlaybackSyncAuthenticator {
    fun authenticate(tokenValue: String): Long?
}

@Component
class SaTokenPlaybackSyncAuthenticator : PlaybackSyncAuthenticator {
    override fun authenticate(tokenValue: String): Long? {
        val loginId = runCatching { StpUtil.getLoginIdByToken(tokenValue) }.getOrNull() ?: return null
        return when (loginId) {
            is Long -> loginId
            is Int -> loginId.toLong()
            is Number -> loginId.toLong()
            is String -> loginId.toLongOrNull()
            else -> loginId.toString().toLongOrNull()
        }
    }
}
