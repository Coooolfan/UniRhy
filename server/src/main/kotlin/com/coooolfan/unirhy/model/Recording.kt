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

    val durationMs: Long

    val defaultInWork: Boolean

    @ManyToOne
    @OnDissociate(DissociateAction.DELETE)
    val cover: MediaFile?

    @OneToMany(mappedBy = "recording")
    val assets: List<Asset>

    @OneToMany(mappedBy = "recording")
    val albumRecordings: List<AlbumRecording>

    @ManyToManyView(prop = "albumRecordings", deeperProp = "album")
    val albums: List<Album>

    @OneToMany(mappedBy = "recording")
    val playlistRecordings: List<PlaylistRecording>

    @ManyToManyView(prop = "playlistRecordings", deeperProp = "playlist")
    val playlists: List<Playlist>

    val lyrics: String

    @Column(sqlType = "vector(1024)")
    val embedding: Embedding?
}
