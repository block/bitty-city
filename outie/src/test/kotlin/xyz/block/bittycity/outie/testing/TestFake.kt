package xyz.block.bittycity.outie.testing

import kotlin.properties.ReadWriteProperty
import kotlin.reflect.KProperty

/**
 * Base class for test fake implementations that need resettable state.
 * Replaces misk-testing's FakeFixture with a simple property delegate pattern.
 */
abstract class TestFake {
  private val resettables = mutableListOf<ResettableProperty<*>>()

  /**
   * Creates a resettable property that can be reset to its initial value.
   * Usage: `var myProperty by resettable { defaultValue }`
   */
  protected fun <T> resettable(initializer: () -> T): ResettableProperty<T> {
    return ResettableProperty(initializer).also { resettables.add(it) }
  }

  /**
   * Resets all resettable properties to their initial values.
   * Should be called between tests to ensure clean state.
   */
  fun reset() {
    resettables.forEach { it.reset() }
  }

  /**
   * Property delegate that stores a value and can reset it to the initial value.
   */
  class ResettableProperty<T>(private val initializer: () -> T) : ReadWriteProperty<Any?, T> {
    private var value: T = initializer()

    override fun getValue(thisRef: Any?, property: KProperty<*>): T = value

    override fun setValue(thisRef: Any?, property: KProperty<*>, value: T) {
      this.value = value
    }

    internal fun reset() {
      value = initializer()
    }
  }
}
