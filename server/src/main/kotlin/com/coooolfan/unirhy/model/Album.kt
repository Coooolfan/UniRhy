package com.coooolfan.unirhy.model

import org.babyfish.jimmer.sql.*
import java.time.LocalDate

@Entity
interface Album {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long

    @Key
    val title: String

    @OneToMany(
        mappedBy = "album",
        orderedProps = [OrderedProp("sortOrder")]
    )
    val albumRecordings: List<AlbumRecording>

    @ManyToManyView(prop = "albumRecordings", deeperProp = "recording")
    val recordings: List<Recording>

    val kind: String

    val releaseDate: LocalDate?

    val comment: String

    @ManyToOne
    val cover: MediaFile?
}
