package xyz.block.bittycity.outie.testing

import xyz.block.bittycity.outie.models.Withdrawal
import xyz.block.bittycity.outie.models.WithdrawalHurdle
import xyz.block.bittycity.outie.models.WithdrawalSpeedOption
import io.kotest.inspectors.forOne
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe

infix fun Any?.shouldBeWithdrawal(withdrawal: Withdrawal) {
  this.shouldNotBeNull()
  this::class.java shouldBe Withdrawal::class.java

  this as Withdrawal

  this shouldBe withdrawal.copy(
    updatedAt = this.updatedAt,
    version = this.version,
  )
}

infix fun Any.shouldBeSpeedHurdle(speedHurdle: WithdrawalHurdle.SpeedHurdle) {
  this::class.java shouldBe WithdrawalHurdle.SpeedHurdle::class.java

  this as WithdrawalHurdle.SpeedHurdle

  this.withdrawalSpeedOptions.forEachIndexed { index, option ->
    speedHurdle.withdrawalSpeedOptions[index] shouldBeWithdrawalSpeedOption option
  }
}

infix fun Any.shouldBeWithdrawalSpeedOption(withdrawalSpeedOption: WithdrawalSpeedOption) {
  this::class.java shouldBe WithdrawalSpeedOption::class.java

  this as WithdrawalSpeedOption

  this shouldBe withdrawalSpeedOption.copy(
    id = this.id,
    approximateWaitTime = this.approximateWaitTime,
    totalFeeFiatEquivalent = this.totalFeeFiatEquivalent
  )
}

infix fun Collection<Any>.shouldHaveWithdrawalsInAnyOrder(withdrawals: List<Withdrawal>) {
  this.shouldHaveSize(withdrawals.size)

  withdrawals.forEach {
    this.forOne { w -> w shouldBeWithdrawal it }
  }
}

infix fun <T> Collection<T>.singleElementShould(block: (T) -> Unit) =
  block(this.shouldHaveSize(1).single())
