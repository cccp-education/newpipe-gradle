package com.cheroliv.newpipe.music

import com.fasterxml.jackson.annotation.JsonIgnoreProperties

/**
 * Rights under which a music track may be used in a produced video
 * (HF-MUSIC-LIBRARY US-1).
 *
 * Fail-closed: an undeclared or unrecognised licence is [UNKNOWN] and can never
 * be published by a consumer. The wire names match the
 * `video.kernel.music.MusicLicence` contract of the N1 kernel.
 */
enum class MusicLicence {
    OWN,
    CC0,
    CC_BY,
    ROYALTY_FREE,
    LICENSED,
    UNKNOWN,
    ;

    /** Lowercase kebab wire name exposed in the manifest (`cc-by`, `royalty-free`…). */
    val wireName: String
        get() =
            when (this) {
                OWN -> "own"
                CC0 -> "cc0"
                CC_BY -> "cc-by"
                ROYALTY_FREE -> "royalty-free"
                LICENSED -> "licensed"
                UNKNOWN -> "unknown"
            }

    companion object {
        fun parse(raw: String): MusicLicence =
            when (raw.trim().lowercase().replace('_', '-')) {
                "own" -> OWN
                "cc0" -> CC0
                "cc-by" -> CC_BY
                "royalty-free" -> ROYALTY_FREE
                "licensed" -> LICENSED
                else -> UNKNOWN
            }
    }
}

/**
 * A declared music source (one artist folder of the downloads directory) with
 * its **mandatory** licence and the aesthetic hints used by the video engines
 * (HF-MUSIC-LIBRARY US-1).
 */
@JsonIgnoreProperties(ignoreUnknown = true)
data class MusicSource(
    val artist: String,
    val licence: String = "",
    val attribution: String = "",
    val mood: String = "",
    val tags: List<String> = emptyList(),
    val energy: Int = 0,
)

/** Root of `music-sources.yml`. */
@JsonIgnoreProperties(ignoreUnknown = true)
data class MusicSourceConfig(
    val sources: List<MusicSource> = emptyList(),
)

/**
 * One track of the produced `music-library.json` (HF-MUSIC-LIBRARY US-1).
 *
 * The JSON shape mirrors the neutral N1 model consumed by hyperframes/capsule;
 * the audio file stays in the gitignored `downloads/` directory and only this
 * metadata is versioned.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
data class MusicLibraryTrack(
    val id: String,
    val title: String,
    val artist: String,
    val file: String,
    val sha256: String,
    val durationSeconds: Double,
    val licence: String,
    val sourceUrl: String = "",
    val attribution: String = "",
    val tags: List<String> = emptyList(),
    val mood: String = "",
    val energy: Int = 0,
)

/** Root of `music-library.json` (versioned manifest). */
@JsonIgnoreProperties(ignoreUnknown = true)
data class MusicLibraryManifest(
    val schemaVersion: Int = 1,
    val tracks: List<MusicLibraryTrack> = emptyList(),
)
