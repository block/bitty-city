package xyz.block.bittycity.innie.testing

import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import xyz.block.bittycity.innie.models.Deposit

infix fun Any?.shouldBeDeposit(deposit: Deposit) {
  this.shouldNotBeNull()
  this::class.java shouldBe Deposit::class.java

  this as Deposit

  this shouldBe deposit.copy(
    updatedAt = this.updatedAt,
    version = this.version
  )
}
