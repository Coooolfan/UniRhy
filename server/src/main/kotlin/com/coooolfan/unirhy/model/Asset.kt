package com.coooolfan.unirhy.model

import org.babyfish.jimmer.sql.DissociateAction
import org.babyfish.jimmer.sql.Entity
import org.babyfish.jimmer.sql.GeneratedValue
import org.babyfish.jimmer.sql.GenerationType
import org.babyfish.jimmer.sql.Id
import org.babyfish.jimmer.sql.ManyToOne
import org.babyfish.jimmer.sql.OnDissociate

@Entity
interface Asset {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long

    @ManyToOne
    @OnDissociate(DissociateAction.DELETE)
    val recording: Recording

    @ManyToOne
    @OnDissociate(DissociateAction.DELETE)
    val mediaFile: MediaFile

    val comment: String
}