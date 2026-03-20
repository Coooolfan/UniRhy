package com.coooolfan.unirhy.service

import org.babyfish.jimmer.sql.TransientResolver
import org.springframework.stereotype.Component

@Component
class MediaFileUrlResolver(
    private val urlSigner: MediaUrlSigner,
) : TransientResolver<Long, String> {

    override fun resolve(ids: Collection<Long>): Map<Long, String> {
        return ids.associateWith { urlSigner.generatePresignedPath(it) }
    }
}
