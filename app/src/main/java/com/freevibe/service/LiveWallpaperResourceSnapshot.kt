package com.freevibe.service

/**
 * What a live-wallpaper engine is holding at this instant.
 *
 * Values are read straight off engine fields rather than accumulated by
 * acquire/release counters: a miscounted release would hide exactly the leak
 * this is meant to catch, so the report is derived from reality instead of from
 * a parallel tally that can drift.
 *
 * This matters more here than anywhere else in the app. A wallpaper process
 * outlives every other component - it survives launcher restarts and stays
 * resident for days - so an engine that keeps one extra player, posted frame
 * callback, sensor listener, segmenter, or decoded bitmap per surface cycle
 * accumulates them until the platform kills the process.
 */
data class LiveWallpaperResourceSnapshot(
    val engine: String,
    /** Video players and decoded animations retained for playback. */
    val players: Int = 0,
    /** Render, telemetry, watchdog, and rebuild Runnables currently posted. */
    val frameCallbacks: Int = 0,
    /** Sensor listeners currently registered with SensorManager. */
    val sensorListeners: Int = 0,
    /** BroadcastReceivers currently registered with the platform. */
    val broadcastReceivers: Int = 0,
    /** Decoded bitmaps retained for drawing. */
    val imageBuffers: Int = 0,
    /** ML Kit clients, which hold native resources until closed. */
    val segmenters: Int = 0,
    /** Media decode threads still running. */
    val loaderThreads: Int = 0,
) {
    val total: Int
        get() = players + frameCallbacks + sensorListeners + broadcastReceivers +
            imageBuffers + segmenters + loaderThreads

    /** True when the engine holds nothing, which is the contract after `onDestroy`. */
    val isDrained: Boolean get() = total == 0

    /** Per-kind view, used by the soak harness to report which kind grew. */
    fun asMap(): Map<String, Int> = mapOf(
        "players" to players,
        "frameCallbacks" to frameCallbacks,
        "sensorListeners" to sensorListeners,
        "broadcastReceivers" to broadcastReceivers,
        "imageBuffers" to imageBuffers,
        "segmenters" to segmenters,
        "loaderThreads" to loaderThreads,
    )

    override fun toString(): String =
        engine + " " + asMap().filterValues { it != 0 }.toString()
}

/**
 * Implemented by every live-wallpaper `Engine` so one harness can soak all of
 * them through the same lifecycle script instead of hand-writing a separate
 * leak test per engine.
 */
interface LiveWallpaperResourceReporter {
    fun resourceSnapshot(): LiveWallpaperResourceSnapshot
}
