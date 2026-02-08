package com.coooolfan.unirhy.model

import org.babyfish.jimmer.sql.*

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

    val defaultInWork: Boolean

    @ManyToOne
    @OnDissociate(DissociateAction.DELETE)
    val cover: MediaFile?

    @OneToMany(mappedBy = "recording")
    val assets: List<Asset>

    @ManyToMany(mappedBy = "recordings")
    val albums: List<Album>
}
