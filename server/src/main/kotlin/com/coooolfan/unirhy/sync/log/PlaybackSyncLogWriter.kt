package com.coooolfan.unirhy.sync.log

import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component

@Component
class PlaybackSyncLogWriter {
    private val logger = LoggerFactory.getLogger(PlaybackSyncLogWriter::class.java)

    fun info(
        event: String,
        vararg fields: Pair<String, Any?>,
    ) {
        logger.info(buildMessage(event, fields.asList()))
    }

    fun warn(
        event: String,
        vararg fields: Pair<String, Any?>,
    ) {
        logger.warn(buildMessage(event, fields.asList()))
    }

    private fun buildMessage(
        event: String,
        fields: List<Pair<String, Any?>>,
    ): String {
        val builder = StringBuilder()
        builder.append(PlaybackSyncLogFields.EVENT)
            .append('=')
            .append(event)

        fields.forEach { (key, value) ->
            if (value == null) {
                return@forEach
            }
            builder.append(' ')
                .append(key)
                .append('=')
                .append(sanitize(value))
        }
        return builder.toString()
    }

    private fun sanitize(value: Any): String = value.toString().replace(WHITESPACE_REGEX, "_")

    private companion object {
        private val WHITESPACE_REGEX = "\\s+".toRegex()
    }
}
