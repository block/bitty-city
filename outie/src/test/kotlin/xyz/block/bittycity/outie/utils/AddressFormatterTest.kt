package xyz.block.bittycity.outie.utils

import xyz.block.bittycity.outie.testing.Arbitrary
import xyz.block.bittycity.outie.utils.AddressFormatter.truncate
import io.kotest.matchers.result.shouldBeFailure
import io.kotest.matchers.result.shouldBeSuccess
import io.kotest.matchers.shouldBe
import io.kotest.property.Arb
import io.kotest.property.arbitrary.stringPattern
import io.kotest.property.checkAll
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test

class AddressFormatterTest {

  @Test
  fun `truncates any address`() = runTest {
    checkAll(Arbitrary.walletAddress) { address ->
      address.truncate() shouldBe address.toString().take(4) + " " +
        address.toString().drop(4).take(4) + "..." +
        address.toString().takeLast(2)
    }
  }

  @Test
  fun `truncates any address (as string)`() = runTest {
    checkAll(Arbitrary.walletAddress) { address ->
      truncate(address.toString()) shouldBeSuccess address.truncate()
    }
  }

  @Test
  fun `failure when truncating short strings`() = runTest {
    checkAll(Arb.stringPattern("^.{0,9}$")) { address ->
      truncate(address).shouldBeFailure()
    }
  }
}
