package com.metafrazo.app.core.screen

import com.metafrazo.app.ui.components.OverlayRegion

import java.util.concurrent.atomic.AtomicLong

/**
 * Tracks generation of screen-translation passes; stale results (older generation) are dropped.
 */
class ScreenTranslateManager {
    private val generation = AtomicLong(0)
    fun nextGeneration(): Long = generation.incrementAndGet()
    fun current(): Long = generation.get()
    fun isStale(resultGeneration: Long): Boolean = resultGeneration < generation.get()

    private val regionCache = mutableMapOf<String, OverlayRegion>()
    fun cacheKey(region: OverlayRegion): String = "${region.x},${region.y},${region.w},${region.h}"
}