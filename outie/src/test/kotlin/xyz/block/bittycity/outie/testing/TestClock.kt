package xyz.block.bittycity.outie.testing

import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.time.ZoneId

/**
 * A test implementation of java.time.Clock that allows manual control of time.
 * Replaces misk-testing's FakeClock with a simple mutable clock.
 */
class TestClock(private var currentInstant: Instant = Instant.now()) : Clock() {

  override fun getZone(): ZoneId = ZoneId.systemDefault()

  override fun withZone(zone: ZoneId): Clock = this

  override fun instant(): Instant = currentInstant

  /**
   * Set the clock to a specific instant.
   */
  fun setInstant(instant: Instant) {
    currentInstant = instant
  }

  /**
   * Advance the clock by a given duration.
   */
  fun advance(duration: Duration) {
    currentInstant = currentInstant.plus(duration)
  }

  /**
   * Advance the clock by a given number of milliseconds.
   */
  fun advanceMillis(millis: Long) {
    currentInstant = currentInstant.plusMillis(millis)
  }

  /**
   * Reset the clock to the current system time.
   */
  fun reset() {
    currentInstant = Instant.now()
  }
}
