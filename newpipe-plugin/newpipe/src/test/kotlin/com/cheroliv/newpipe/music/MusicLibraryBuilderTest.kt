package com.cheroliv.newpipe.music

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File
import java.nio.file.Path

/**
 * TDD — licence gate and manifest mapping of the music library
 * (HF-MUSIC-LIBRARY US-1). Pure: audio probes and hashing are injected.
 */
class MusicLibraryBuilderTest {
    @field:TempDir
    lateinit var tempDir: Path

    private fun audio(
        artist: String,
        name: String,
    ): File {
        val folder = tempDir.resolve(artist).toFile().apply { mkdirs() }
        return File(folder, name).apply { writeText("bytes-of-$artist-$name") }
    }

    private fun build(
        sources: List<MusicSource>,
        allowUnlicensed: Boolean = false,
        duration: Double = 12.5,
    ): MusicLibraryBuilder.Result =
        MusicLibraryBuilder.build(
            sources = sources,
            downloadsRoot = tempDir.toFile(),
            readDurationSeconds = { duration },
            readTags = { file -> file.nameWithoutExtension to "Tagged Artist" },
            sha256 = { file -> file.name.hashCode().toUInt().toString(16) },
            allowUnlicensed = allowUnlicensed,
        )

    @Test
    fun `an allowed licence produces a track with its aesthetic hints`() {
        audio("Mon son 2", "01.mp3")
        val result =
            build(
                listOf(
                    MusicSource(
                        artist = "Mon son 2",
                        licence = "own",
                        attribution = "Me",
                        mood = "calm",
                        tags = listOf("piano"),
                        energy = 20,
                    ),
                ),
            )

        assertThat(result.rejected).isEmpty()
        assertThat(result.manifest.schemaVersion).isEqualTo(1)
        assertThat(result.manifest.tracks).hasSize(1)
        val track = result.manifest.tracks.single()
        assertThat(track.title).isEqualTo("01")
        assertThat(track.artist).isEqualTo("Tagged Artist")
        assertThat(track.licence).isEqualTo("own")
        assertThat(track.mood).isEqualTo("calm")
        assertThat(track.tags).containsExactly("piano")
        assertThat(track.energy).isEqualTo(20)
        assertThat(track.durationSeconds).isEqualTo(12.5)
        assertThat(track.file).isEqualTo("Mon son 2/01.mp3")
        assertThat(track.id).startsWith("mon-son-2-01-")
    }

    @Test
    fun `an unknown licence is rejected and produces no track`() {
        audio("Ripped", "01.mp3")
        val result = build(listOf(MusicSource(artist = "Ripped", licence = "youtube", mood = "calm")))

        assertThat(result.manifest.tracks).isEmpty()
        assertThat(result.rejected).singleElement().asString().contains("not allowed")
    }

    @Test
    fun `allowUnlicensed includes the track but marks it unknown`() {
        audio("Ripped", "01.mp3")
        val result = build(listOf(MusicSource(artist = "Ripped", licence = "youtube", mood = "calm")), allowUnlicensed = true)

        assertThat(result.rejected).isEmpty()
        assertThat(result.manifest.tracks.single().licence).isEqualTo("unknown")
    }

    @Test
    fun `an out of range energy is rejected`() {
        audio("A", "01.mp3")
        val result = build(listOf(MusicSource(artist = "A", licence = "own", energy = 150)))

        assertThat(result.manifest.tracks).isEmpty()
        assertThat(result.rejected).singleElement().asString().contains("energy 150")
    }

    @Test
    fun `an undeclared artist folder is ignored and counted`() {
        audio("Declared", "01.mp3")
        audio("NotDeclared", "02.mp3")
        audio("NotDeclared", "03.mp3")
        val result = build(listOf(MusicSource(artist = "Declared", licence = "own", mood = "calm")))

        assertThat(result.manifest.tracks).hasSize(1)
        assertThat(result.ignored).isEqualTo(2)
    }

    @Test
    fun `two files get distinct ids`() {
        audio("A", "01.mp3")
        audio("A", "02.mp3")
        val result = build(listOf(MusicSource(artist = "A", licence = "own", mood = "calm")))

        assertThat(result.manifest.tracks).hasSize(2)
        assertThat(result.manifest.tracks.map { it.id }).doesNotHaveDuplicates()
    }

    @Test
    fun `a declared but absent folder is rejected`() {
        val result = build(listOf(MusicSource(artist = "Ghost", licence = "own", mood = "calm")))

        assertThat(result.manifest.tracks).isEmpty()
        assertThat(result.rejected).singleElement().asString().contains("no downloaded folder")
    }

    @Test
    fun `slug normalises punctuation and case`() {
        assertThat(MusicLibraryBuilder.slug("Mon Son 2 — Track (Live)!")).isEqualTo("mon-son-2-track-live")
    }
}
