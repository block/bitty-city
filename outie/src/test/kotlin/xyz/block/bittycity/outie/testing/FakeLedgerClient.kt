package xyz.block.bittycity.outie.testing

import xyz.block.bittycity.common.testing.TestFake

import app.cash.quiver.extensions.failure
import app.cash.quiver.extensions.success
import xyz.block.bittycity.outie.client.LedgerClient
import xyz.block.bittycity.common.models.BalanceId
import xyz.block.bittycity.common.models.Bitcoins
import xyz.block.bittycity.common.models.CustomerId
import xyz.block.bittycity.common.models.LedgerTransactionId
import xyz.block.bittycity.outie.models.Withdrawal

class FakeLedgerClient :
  TestFake(),
  LedgerClient {

  var nextBalance: Result<Bitcoins> by resettable { Bitcoins(11_000_000L).success() }
  var nextTransactionId: Result<LedgerTransactionId> by resettable {
    LedgerTransactionId("testTransactionId").success()
  }
  var nextConfirmResult: Result<Unit> by resettable { Unit.success() }
  var nextVoidResult: Result<Unit> by resettable { Unit.success() }
  var voidCalls: MutableList<LedgerTransactionId> by resettable { mutableListOf() }
  var refundFeeCalls: MutableList<LedgerTransactionId> by resettable { mutableListOf() }
  var freezeCalls: MutableList<LedgerTransactionId> by resettable { mutableListOf() }

  override fun getBalance(customerId: CustomerId, balanceId: BalanceId): Result<Bitcoins> =
    nextBalance

  override fun createTransaction(withdrawal: Withdrawal): Result<LedgerTransactionId> =
    nextTransactionId

  override fun confirmTransaction(
    customerId: CustomerId,
    balanceId: BalanceId,
    ledgerTransactionId: LedgerTransactionId
  ): Result<Unit> = nextConfirmResult

  override fun voidTransaction(
    customerId: CustomerId,
    balanceId: BalanceId,
    ledgerTransactionId: LedgerTransactionId
  ): Result<Unit> = nextVoidResult.also {
    voidCalls.add(ledgerTransactionId)
  }

  override fun refundFee(withdrawal: Withdrawal): Result<LedgerTransactionId> =
    nextTransactionId.also {
      refundFeeCalls.add(withdrawal.ledgerTransactionId!!)
    }

  override fun freezeFunds(withdrawal: Withdrawal): Result<Unit> = Unit.success().also {
    freezeCalls.add(withdrawal.ledgerTransactionId!!)
  }

  fun failNextVoid(): Exception =
    Exception("\uD83D\uDC1F Something fishy (expected exception during testing)")
      .also { nextVoidResult = it.failure() }
}
