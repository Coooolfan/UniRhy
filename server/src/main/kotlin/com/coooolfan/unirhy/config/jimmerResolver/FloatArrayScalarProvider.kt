package com.coooolfan.unirhy.config.jimmerResolver

import com.coooolfan.unirhy.model.Embedding
import com.coooolfan.unirhy.model.Recording
import com.pgvector.PGvector
import org.babyfish.jimmer.meta.ImmutableProp
import org.babyfish.jimmer.meta.ImmutableType
import org.babyfish.jimmer.sql.runtime.ScalarProvider
import org.postgresql.util.PGobject
import org.springframework.stereotype.Component

@Component
class EmbeddingScalarProvider : ScalarProvider<Embedding, PGobject> {

    override fun getHandledProps(): Collection<ImmutableProp> {
        return listOf(
            ImmutableType.get(Recording::class.java).getProp("embedding")
        )
    }

    override fun toScalar(sqlValue: PGobject): Embedding {
        return Embedding(PGvector(sqlValue.value).toArray())
    }

    override fun toSql(scalarValue: Embedding): PGobject {
        return PGvector(scalarValue.values)
    }
}
