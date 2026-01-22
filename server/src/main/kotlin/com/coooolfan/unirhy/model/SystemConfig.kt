package com.coooolfan.unirhy.model

import com.coooolfan.unirhy.model.storage.FileProviderFileSystem
import com.coooolfan.unirhy.model.storage.FileProviderOss
import org.babyfish.jimmer.sql.Entity
import org.babyfish.jimmer.sql.Id
import org.babyfish.jimmer.sql.IdView
import org.babyfish.jimmer.sql.ManyToOne

@Entity
interface SystemConfig {
    @Id
    val id: Long

    @ManyToOne
    val ossProvider: FileProviderOss?

    @IdView("ossProvider")
    val ossProviderId: Long?

    @ManyToOne
    val fsProvider: FileProviderFileSystem?

    @IdView("fsProvider")
    val fsProviderId: Long?
}
