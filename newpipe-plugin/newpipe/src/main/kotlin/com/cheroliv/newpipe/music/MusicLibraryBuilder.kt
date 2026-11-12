package com.cheroliv.newpipe.music

import java.io.File
import java.security.MessageDigest

/**
 * Builds the versioned music library manifest from declared sources and the
 * downloaded audio files (HF-MUSIC-LIBRARY US-1).
 *
 * Pure with respect to its effectors: duration, ID3 tags and the content hash are
 * injected, so the licence gate and the mapping are unit-tested without audio
 * tooling. The **gate is fail-closed**: a declared source without an allowed
 * licence yields no track (and a rejection message); only `allowUnlicensed`
 * (personal dogfooding) lets an `unknown` licence through, and even then the
 * track is marked `unknown` so consumers will not publish it.
 *
 * Audio files living in an **undeclared** artist folder are ignored and counted
 * — never silently added to the library.
 */
object MusicLibraryBuilder {
    const val SCHEMA_VERSION: Int = 1

    /** Audio extensions considered part of the library. */
    val AUDIO_EXTENSIONS: Set<String> = setOf("mp3", "wav", "m4a", "ogg", "flac")

    /**
     * @property manifest the produced manifest (tracks sorted by id).
     * @property rejected actionable rejection messages (empty on success).
     * @property ignored audio files found in undeclared folders.
     */
    data class Result(
        val manifest: MusicLibraryManifest,
        val rejected: List<String>,
        val ignored: Int,
    )

    fun build(
        sources: List<MusicSource>,
        downloadsRoot: File,
        readDurationSeconds: (File) -> Double = { 0.0 },
        readTags: (File) -> Pair<String, String>? = { null },
        sha256: (File) -> String = ::sha256Of,
        allowUnlicensed: Boolean = false,
    ): Result {
        val tracks = mutableListOf<MusicLibraryTrack>()
        val rejected = mutableListOf<String>()
        val declaredArtists = mutableSetOf<String>()

        sources.forEach { source ->
            val artist = source.artist.trim()
            if (artist.isEmpty()) {
                rejected += "a source has a blank artist name"
                return@forEach
            }
            declaredArtists += artist.lowercase()
            if (source.energy !in 0..100) {
                rejected += "artist '$artist': energy ${source.energy} is out of 0..100"
                return@forEach
            }
            val licence = MusicLicence.parse(source.licence)
            if (licence == MusicLicence.UNKNOWN && !allowUnlicensed) {
                rejected +=
                    "artist '$artist': licence '${source.licence}' is not allowed " +
                        "(own|cc0|cc-by|royalty-free|licensed)"
                return@forEach
            }
            val folder =
                downloadsRoot
                    .listFiles { file -> file.isDirectory && file.name.equals(artist, ignoreCase = true) }
                    ?.firstOrNull()
            if (folder == null) {
                rejected += "artist '$artist': no downloaded folder '${downloadsRoot.name}/$artist'"
                return@forEach
            }
            val audio =
                folder
                    .listFiles { file -> file.isFile && file.extension.lowercase() in AUDIO_EXTENSIONS }
                    ?.sortedBy { it.name }
                    .orEmpty()
            if (audio.isEmpty()) {
                rejected += "artist '$artist': no audio file in '${folder.name}'"
                return@forEach
            }
            audio.forEach { file ->
                val hash = sha256(file)
                val tags = readTags(file)
                val title = tags?.first?.takeIf { it.isNotBlank() } ?: file.nameWithoutExtension
                val trackArtist = tags?.second?.takeIf { it.isNotBlank() } ?: artist
                tracks +=
                    MusicLibraryTrack(
                        id = "${slug("$artist $title").ifBlank { "track" }}-${hash.take(8)}",
                        title = title,
                        artist = trackArtist,
                        file = relativePath(downloadsRoot, file),
                        sha256 = hash,
                        durationSeconds = readDurationSeconds(file),
                        licence = licence.wireName,
                        attribution = source.attribution.trim(),
                        tags = source.tags,
                        mood = source.mood.trim(),
                        energy = source.energy,
                    )
            }
        }

        val ignored =
            downloadsRoot
                .listFiles { file -> file.isDirectory }
                ?.filter { !declaredArtists.contains(it.name.trim().lowercase()) }
                ?.sumOf { folder ->
                    folder.listFiles { file -> file.isFile && file.extension.lowercase() in AUDIO_EXTENSIONS }?.size ?: 0
                } ?: 0

        return Result(
            manifest = MusicLibraryManifest(SCHEMA_VERSION, tracks.sortedBy { it.id }),
            rejected = rejected,
            ignored = ignored,
        )
    }

    /** Lowercase kebab slug used for deterministic ids. */
    fun slug(raw: String): String =
        raw
            .lowercase()
            .replace(Regex("[^a-z0-9]+"), "-")
            .trim('-')

    /** Relative, `/`-separated path of [file] under [root] (portable manifest). */
    fun relativePath(
        root: File,
        file: File,
    ): String = root.toPath().relativize(file.toPath()).toString().replace(File.separatorChar, '/')

    /** SHA-256 (hex) of [file]'s content. */
    fun sha256Of(file: File): String {
        val digest = MessageDigest.getInstance("SHA-256")
        file.inputStream().use { input ->
            val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
            while (true) {
                val read = input.read(buffer)
                if (read < 0) break
                digest.update(buffer, 0, read)
            }
        }
        return digest.digest().joinToString("") { "%02x".format(it) }
    }
}
