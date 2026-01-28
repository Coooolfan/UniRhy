package com.coooolfan.unirhy.model

import org.babyfish.jimmer.sql.DissociateAction
import org.babyfish.jimmer.sql.Entity
import org.babyfish.jimmer.sql.GeneratedValue
import org.babyfish.jimmer.sql.GenerationType
import org.babyfish.jimmer.sql.Id
import org.babyfish.jimmer.sql.ManyToMany
import org.babyfish.jimmer.sql.ManyToOne
import org.babyfish.jimmer.sql.OnDissociate
import org.babyfish.jimmer.sql.OneToMany

@Entity
interface Recording {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long

    @ManyToOne
    @OnDissociate(DissociateAction.DELETE)
    val work: Work

    @ManyToMany
    val artists: List<Artist>

    val kind: String

    val label: String?

    val title: String?

    val comment: String

    @ManyToOne
    @OnDissociate(DissociateAction.DELETE)
    val cover: MediaFile?

    @OneToMany(mappedBy = "recording")
    val assets: List<Asset>

    @ManyToMany(mappedBy = "recordings")
    val albums: List<Album>
}
