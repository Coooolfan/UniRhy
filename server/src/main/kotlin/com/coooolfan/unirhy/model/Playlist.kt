package com.coooolfan.unirhy.model

import org.babyfish.jimmer.sql.Entity
import org.babyfish.jimmer.sql.GeneratedValue
import org.babyfish.jimmer.sql.GenerationType
import org.babyfish.jimmer.sql.Id
import org.babyfish.jimmer.sql.ManyToManyView
import org.babyfish.jimmer.sql.ManyToOne
import org.babyfish.jimmer.sql.OneToMany
import org.babyfish.jimmer.sql.OrderedProp

@Entity
interface Playlist {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long

    @ManyToOne
    val owner: Account

    @OneToMany(
        mappedBy = "playlist",
        orderedProps = [OrderedProp("sortOrder")]
    )
    val playlistRecordings: List<PlaylistRecording>

    @ManyToManyView(prop = "playlistRecordings", deeperProp = "recording")
    val recordings: List<Recording>

    val name: String

    val comment: String
}
