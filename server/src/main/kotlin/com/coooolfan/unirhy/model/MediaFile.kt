package com.coooolfan.unirhy.model

import com.coooolfan.unirhy.model.storage.FileProviderFileSystem
import com.coooolfan.unirhy.model.storage.FileProviderOss
import com.coooolfan.unirhy.service.MediaFileUrlResolver
import org.babyfish.jimmer.sql.Entity
import org.babyfish.jimmer.sql.GeneratedValue
import org.babyfish.jimmer.sql.GenerationType
import org.babyfish.jimmer.sql.Id
import org.babyfish.jimmer.sql.IdView
import org.babyfish.jimmer.sql.Key
import org.babyfish.jimmer.sql.ManyToOne
import org.babyfish.jimmer.sql.Transient

@Entity
interface MediaFile {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long

    @Key
    val objectKey: String

    val mimeType: String

    // 字节
    val size: Long

    val width: Int?

    val height: Int?

    @ManyToOne
    @Key
    val ossProvider: FileProviderOss?

    @IdView("ossProvider")
    val ossProviderId: Long?

    @ManyToOne
    @Key
    val fsProvider: FileProviderFileSystem?

    @IdView("fsProvider")
    val fsProviderId: Long?

    @Transient(MediaFileUrlResolver::class)
    val url: String
}
