package com.tint.app.sync

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class PositionTrackerTest {
    private var clock = 1_000L
    private val tracker = PositionTracker { clock }

    @Test
    fun `sin muestras no hay posicion`() {
        assertNull(tracker.current())
    }

    @Test
    fun `si el video avanza extrapola con el tiempo transcurrido y la velocidad`() {
        tracker.update(positionMs = 60_000, speed = 1.5)
        clock += 200

        assertEquals(60_300L, tracker.current())
    }

    @Test
    fun `si no avanza devuelve la posicion publicada`() {
        tracker.update(positionMs = 60_000, speed = 0.0)
        clock += 5_000

        assertEquals(60_000L, tracker.current())
    }

    @Test
    fun `clear olvida la muestra`() {
        tracker.update(10, 1.0)

        tracker.clear()

        assertNull(tracker.current())
    }
}
