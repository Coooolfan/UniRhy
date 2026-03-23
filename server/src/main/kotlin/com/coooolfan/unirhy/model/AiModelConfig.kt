package com.coooolfan.unirhy.model

import org.babyfish.jimmer.sql.Serialized

@Serialized
data class AiModelConfig(
    val endpoint: String,
    val model: String,
    val key: String,
    val requestFormat: AiRequestFormat,
)
