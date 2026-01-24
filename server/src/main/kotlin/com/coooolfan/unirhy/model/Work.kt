package com.coooolfan.unirhy.model

import org.babyfish.jimmer.sql.Entity
import org.babyfish.jimmer.sql.GeneratedValue
import org.babyfish.jimmer.sql.GenerationType
import org.babyfish.jimmer.sql.Id
import org.babyfish.jimmer.sql.OneToMany

@Entity
interface Work {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long

    val title: String

    @OneToMany(mappedBy = "work")
    val recordings: List<Recording>
}