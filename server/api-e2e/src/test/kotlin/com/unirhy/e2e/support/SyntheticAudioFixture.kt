package com.unirhy.e2e.support

import org.jaudiotagger.audio.AudioFileIO
import org.jaudiotagger.tag.FieldKey
import java.io.ByteArrayInputStream
import java.nio.file.Files
import java.nio.file.Path
import javax.sound.sampled.AudioFileFormat
import javax.sound.sampled.AudioFormat
import javax.sound.sampled.AudioInputStream
import javax.sound.sampled.AudioSystem

data class AudioFixtureMetadata(
    val title: String? = null,
    val artist: String? = null,
    val album: String? = null,
    val comment: String? = null,
)

data class GeneratedFixtureFile(
    val path: Path,
    val metadata: AudioFixtureMetadata,
)

data class FixtureBatch(
    val fixtureRoot: Path,
    val files: List<GeneratedFixtureFile>,
    val metadata: AudioFixtureMetadata,
)

object SyntheticAudioFixture {

    private val ffmpegAvailable: Boolean by lazy {
        runCatching {
            val process = ProcessBuilder("ffmpeg", "-version")
                .redirectErrorStream(true)
                .start()
            process.inputStream.readAllBytes()
            process.waitFor() == 0
        }.getOrDefault(false)
    }

    private fun requireFfmpeg() {
        check(ffmpegAvailable) {
            "ffmpeg is not available on PATH. Install ffmpeg to generate non-WAV audio fixtures."
        }
    }

    fun generateBatch(
        scanWorkspace: Path,
        dirName: String,
        count: Int,
        extension: String = "mp3",
        metadata: AudioFixtureMetadata,
    ): FixtureBatch {
        val fixtureRoot = scanWorkspace.resolve(dirName)
        Files.createDirectories(fixtureRoot)
        val files = (0 until count).map { index ->
            val fileName = "fixture-${index.toString().padStart(4, '0')}.$extension"
            generateOne(fixtureRoot, fileName, metadata)
        }
        return FixtureBatch(
            fixtureRoot = fixtureRoot,
            files = files,
            metadata = metadata,
        )
    }

    fun generateOne(
        outputDir: Path,
        fileName: String,
        metadata: AudioFixtureMetadata,
    ): GeneratedFixtureFile {
        val outputPath = outputDir.resolve(fileName)
        val extension = fileName.substringAfterLast('.', "").lowercase()
        if (extension == "wav") {
            generateWav(outputPath)
        } else {
            generateWithFfmpeg(outputPath, extension)
        }
        if (metadata.title != null || metadata.artist != null || metadata.album != null || metadata.comment != null) {
            writeTags(outputPath, metadata)
        }
        return GeneratedFixtureFile(path = outputPath, metadata = metadata)
    }

    private fun generateWav(output: Path) {
        val sampleRate = 44100f
        val sampleSizeInBits = 16
        val channels = 1
        val frameSize = channels * (sampleSizeInBits / 8)
        val sampleCount = 100
        val format = AudioFormat(sampleRate, sampleSizeInBits, channels, true, false)
        val pcmData = ByteArray(sampleCount * frameSize)
        val audioInputStream = AudioInputStream(
            ByteArrayInputStream(pcmData),
            format,
            sampleCount.toLong(),
        )
        AudioSystem.write(audioInputStream, AudioFileFormat.Type.WAVE, output.toFile())
    }

    private fun generateWithFfmpeg(output: Path, extension: String) {
        requireFfmpeg()
        val codec = when (extension) {
            "m4a" -> "aac"
            "wma" -> "wmav2"
            else -> null
        }
        val command = mutableListOf(
            "ffmpeg", "-y",
            "-f", "lavfi", "-i", "anullsrc=r=44100:cl=mono",
            "-t", "0.05",
        )
        if (codec != null) {
            command += listOf("-c:a", codec)
        }
        command += output.toAbsolutePath().toString()

        val process = ProcessBuilder(command)
            .redirectErrorStream(true)
            .start()
        val exitCode = process.waitFor()
        check(exitCode == 0) {
            "ffmpeg failed to generate ${output.fileName}: ${process.inputStream.bufferedReader().readText()}"
        }
    }

    private fun writeTags(file: Path, metadata: AudioFixtureMetadata) {
        val audioFile = AudioFileIO.read(file.toFile())
        val tag = audioFile.tagOrCreateDefault
        metadata.title?.let { tag.setField(FieldKey.TITLE, it) }
        metadata.artist?.let { tag.setField(FieldKey.ARTIST, it) }
        metadata.album?.let { tag.setField(FieldKey.ALBUM, it) }
        metadata.comment?.let { tag.setField(FieldKey.COMMENT, it) }
        audioFile.commit()
    }
}
