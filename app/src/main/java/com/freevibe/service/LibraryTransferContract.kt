package com.freevibe.service

/**
 * One runtime contract for every library, favorites, and collection transfer limit.
 * `docs/data/export-format.json` points at these keys and the repository gate checks
 * that the public format description and every exporter still use this object.
 */
internal object LibraryTransferContract {
    const val MAX_FAVORITES = 10_000
    const val MAX_COLLECTIONS = 100
    const val MAX_COLLECTION_ITEMS = 500
    const val MAX_SEARCH_HISTORY_ITEMS = 200
    const val MAX_ROTATION_EXCLUSIONS = 10_000
    const val MAX_LOCAL_WALLPAPERS = 10_000
    const val MAX_WALLPAPER_HISTORY_ITEMS = 100

    const val MAX_FAVORITES_DOCUMENT_CHARS = 64_000_000
    const val MAX_COLLECTION_DOCUMENT_BYTES = 4_194_304
    const val MAX_LIBRARY_DOCUMENT_CHARS = 128_000_000
    const val MAX_SOUND_SHUFFLE_DOCUMENT_CHARS = 4_000_000

    const val MAX_TEXT_CHARS = 512
    const val MAX_URL_CHARS = 2_048
    const val MAX_COLLECTION_NAME_CHARS = 80
    const val MIN_SHARE_TOKEN_CHARS = 8
    const val MAX_SHARE_TOKEN_CHARS = 80
    const val MAX_QR_IMAGE_BYTES = 4_194_304L
    const val MAX_QR_IMAGE_DIMENSION = 4_096
    const val MAX_QR_IMAGE_PIXELS = 12_000_000L
}

class LibraryTransferLimitExceededException(
    val section: String,
    val actual: Long,
    val maximum: Long,
) : IllegalStateException("$section contains $actual; maximum is $maximum.")

internal fun requireWithinTransferLimit(section: String, actual: Int, maximum: Int) {
    requireWithinTransferLimit(section, actual.toLong(), maximum.toLong())
}

internal fun requireWithinTransferLimit(section: String, actual: Long, maximum: Long) {
    require(actual >= 0) { "$section count cannot be negative." }
    require(maximum >= 0) { "$section maximum cannot be negative." }
    if (actual > maximum) {
        throw LibraryTransferLimitExceededException(section, actual, maximum)
    }
}
