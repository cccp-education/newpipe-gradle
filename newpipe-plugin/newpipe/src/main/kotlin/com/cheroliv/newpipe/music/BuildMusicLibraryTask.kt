package com.cheroliv.newpipe.music

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.SerializationFeature
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory
import com.fasterxml.jackson.module.kotlin.readValue
import com.fasterxml.jackson.module.kotlin.registerKotlinModule
import org.gradle.api.DefaultTask
import org.gradle.api.GradleException
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.provider.Property
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.Internal
import org.gradle.api.tasks.OutputFile
import org.gradle.api.tasks.TaskAction
import org.gradle.work.DisableCachingByDefault
import org.jaudiotagger.audio.AudioFileIO
import org.jaudiotagger.tag.FieldKey
import java.io.File

/**
 * `buildMusicLibrary` — turns the declared music sources and the downloaded
 * audio into the versioned [MusicLibraryManifest] consumed by the video engines
 * (HF-MUSIC-LIBRARY US-1).
 *
 * The audio stays in the gitignored [downloadsDir]; only the metadata manifest
 * is written. Declared sources must carry an allowed licence — the build fails
 * with an actionable message otherwise (never a silent skip).
 *
 * @property sourcesFile `music-sources.yml` (artist + licence + aesthetic hints).
 * @property downloadsDir downloaded audio root (one folder per artist).
 * @property outputFile `music-library.json` (versioned manifest).
 * @property allowUnlicensed personal dogfooding only: include `unknown` licences
 *                           (still marked `unknown`, never published by consumers).
 */
@DisableCachingByDefault(because = "Reads audio tags and hashes the library (I/O bound)")
abstract class BuildMusicLibraryTask : DefaultTask() {
    @get:Input
    abstract val sourcesFile: Property<String>

    @get:Input
    abstract val downloadsDir: Property<String>

    @get:OutputFile
    abstract val outputFile: RegularFileProperty

    @get:Internal
    abstract val projectDir: org.gradle.api.file.DirectoryProperty

    @get:Input
    abstract val allowUnlicensed: Property<Boolean>

    @TaskAction
    fun build() {
        val projectDir = projectDir.asFile.get()
        val sources = File(projectDir, sourcesFile.get())
        val downloads = File(projectDir, downloadsDir.get())

        val declared = readSources(sources)
        if (declared.isEmpty()) {
            logger.lifecycle("buildMusicLibrary: no declared source (${sources.name}) — empty library")
        }

        val result =
            MusicLibraryBuilder.build(
                sources = declared,
                downloadsRoot = downloads,
                readDurationSeconds = ::readDurationSeconds,
                readTags = ::readTags,
                allowUnlicensed = allowUnlicensed.get(),
            )

        val output = outputFile.get().asFile
        output.parentFile?.mkdirs()
        jsonMapper.writeValue(output, result.manifest)

        logger.lifecycle(
            "buildMusicLibrary: ${result.manifest.tracks.size} track(s) -> ${output.absolutePath} " +
                "(${result.ignored} file(s) ignored in undeclared folders)",
        )
        if (result.rejected.isNotEmpty()) {
            val message =
                buildString {
                    append("buildMusicLibrary: ${result.rejected.size} declared source(s) rejected:")
                    result.rejected.forEach { append("\n  - ").append(it) }
                }
            throw GradleException(message)
        }
    }

    private fun readSources(file: File): List<MusicSource> {
        if (!file.exists() || !file.isFile) return emptyList()
        return yamlMapper.readValue<MusicSourceConfig>(file).sources
    }

    private fun readTags(file: File): Pair<String, String>? =
        try {
            val tag = AudioFileIO.read(file).tag
            val title = tag?.getFirst(FieldKey.TITLE).orEmpty()
            val artist = tag?.getFirst(FieldKey.ARTIST).orEmpty()
            if (title.isBlank() && artist.isBlank()) null else title to artist
        } catch (e: Exception) {
            null
        }

    private fun readDurationSeconds(file: File): Double =
        try {
            AudioFileIO.read(file).audioHeader.trackLength.toDouble()
        } catch (e: Exception) {
            0.0
        }

    private companion object {
        val yamlMapper: ObjectMapper = ObjectMapper(YAMLFactory()).registerKotlinModule()
        val jsonMapper: ObjectMapper =
            ObjectMapper().registerKotlinModule().enable(SerializationFeature.INDENT_OUTPUT)
    }
}
