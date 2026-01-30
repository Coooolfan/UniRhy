package com.coooolfan.unirhy.service.task.audio

import org.jaudiotagger.audio.AudioFileIO
import org.jaudiotagger.tag.FieldKey
import java.io.File

data class ArtworkData(
    val binaryData: ByteArray?,
    val mimeType: String?,
    val width: Int?,
    val height: Int?
)

data class AudioMetadata(
    val title: String,
    val artist: String,
    val album: String,
    val comment: String,
    val artwork: ArtworkData?
)

fun readAudioMetadata(file: File): AudioMetadata {
    val audioTag = AudioFileIO.read(file)
    val tag = audioTag.tag
    val artwork = tag?.firstArtwork?.let {
        ArtworkData(
            binaryData = it.binaryData,
            mimeType = it.mimeType,
            width = it.width.takeIf { width -> width > 0 },
            height = it.height.takeIf { height -> height > 0 }
        )
    }

    return AudioMetadata(
        title = tag?.getFirst(FieldKey.TITLE).orEmpty(),
        artist = tag?.getFirst(FieldKey.ARTIST).orEmpty(),
        album = tag?.getFirst(FieldKey.ALBUM).orEmpty(),
        comment = tag?.getFirst(FieldKey.COMMENT).orEmpty(),
        artwork = artwork
    )
}
