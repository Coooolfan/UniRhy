package com.coooolfan.unirhy.model

import org.babyfish.jimmer.sql.Entity
import org.babyfish.jimmer.sql.GeneratedValue
import org.babyfish.jimmer.sql.GenerationType
import org.babyfish.jimmer.sql.Id
import org.babyfish.jimmer.sql.ManyToOne

@Entity
interface Account {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long

    val name: String

    val password: String

    val email: String

    val admin: Boolean

    @ManyToOne
    val avatar: MediaFile?
}