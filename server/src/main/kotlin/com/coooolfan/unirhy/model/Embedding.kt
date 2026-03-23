package com.coooolfan.unirhy.model

import org.babyfish.jimmer.sql.Serialized

/**
 * pgvector 向量的包装类型，用于绕过 Jimmer 对数组类型的限制
 */
@Serialized
class Embedding(val values: FloatArray) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is Embedding) return false
        return values.contentEquals(other.values)
    }

    override fun hashCode(): Int = values.contentHashCode()
}
