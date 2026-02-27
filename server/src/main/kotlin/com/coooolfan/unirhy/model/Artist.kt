package com.coooolfan.unirhy.model

import org.babyfish.jimmer.sql.*

@Entity
interface Artist {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long

    @Key
    val displayName: String

    val alias: List<String>

    val comment: String

    @ManyToOne
    val avatar: MediaFile?
}
