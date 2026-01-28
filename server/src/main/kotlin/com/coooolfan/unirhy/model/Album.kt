package com.coooolfan.unirhy.model

import org.babyfish.jimmer.sql.Entity
import org.babyfish.jimmer.sql.GeneratedValue
import org.babyfish.jimmer.sql.GenerationType
import org.babyfish.jimmer.sql.Id
import org.babyfish.jimmer.sql.Key
import org.babyfish.jimmer.sql.ManyToMany
import org.babyfish.jimmer.sql.ManyToOne
import java.time.LocalDate

@Entity
interface Album {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long

    @Key
    val title: String

    @ManyToMany
    val recordings: List<Recording>

    val kind: String

    val releaseDate: LocalDate?

    val comment: String

    @ManyToOne
    val cover: MediaFile?
}
