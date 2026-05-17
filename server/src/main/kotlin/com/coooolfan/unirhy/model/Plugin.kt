package com.coooolfan.unirhy.model

import org.babyfish.jimmer.sql.Entity
import org.babyfish.jimmer.sql.Id
import java.time.Instant

@Entity
interface Plugin {
    @Id
    val id: String

    val name: String?

    val version: String

    val abi: String

    val taskType: String

    val extension: String

    val networkAllow: List<String>

    val formFields: String

    val wasm: ByteArray

    val enabled: Boolean

    val createdAt: Instant
}
