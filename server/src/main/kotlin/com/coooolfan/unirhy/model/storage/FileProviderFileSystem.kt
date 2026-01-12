package com.coooolfan.unirhy.model.storage

import org.babyfish.jimmer.sql.Entity
import org.babyfish.jimmer.sql.GeneratedValue
import org.babyfish.jimmer.sql.GenerationType
import org.babyfish.jimmer.sql.Id

@Entity
interface FileProviderFileSystem {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long

    val parentPath: String

    val mounted: Boolean
}