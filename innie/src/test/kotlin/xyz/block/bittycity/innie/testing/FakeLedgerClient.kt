package xyz.block.bittycity.innie.testing

import app.cash.quiver.extensions.success
import org.bitcoinj.base.Address
import org.joda.money.Money
import xyz.block.bittycity.common.models.BalanceId
import xyz.block.bittycity.common.models.Bitcoins
import xyz.block.bittycity.common.models.CustomerId
import xyz.block.bittycity.common.models.LedgerTransactionId
import xyz.block.bittycity.common.testing.TestFake
import xyz.block.bittycity.innie.client.DepositLedgerClient
import xyz.block.bittycity.innie.models.DepositReversalToken
import xyz.block.bittycity.innie.models.DepositToken
import java.time.Instant
import java.util.UUID

class FakeLedgerClient : TestFake(), DepositLedgerClient {

  override fun confirmDepositTransaction(
    customerId: CustomerId,
    balanceId: BalanceId,
    ledgerTransactionId: LedgerTransactionId
  ): Result<Unit> = Unit.success()

  override fun confirmReversalTransaction(
    customerId: CustomerId,
    balanceId: BalanceId,
    ledgerTransactionId: LedgerTransactionId
  ): Result<Unit> = Unit.success()

  override fun createDepositTransaction(
    depositId: DepositToken,
    customerId: CustomerId,
    balanceId: BalanceId,
    createdAt: Instant,
    amount: Bitcoins,
    fiatEquivalent: Money
  ): Result<LedgerTransactionId> = LedgerTransactionId("ledger-txn-${UUID.randomUUID()}").success()

  override fun voidDepositTransaction(
    customerId: CustomerId,
    balanceId: BalanceId,
    ledgerTransactionId: LedgerTransactionId
  ): Result<Unit> = Unit.success()

  override fun freezeFunds(
    depositReversalId: DepositReversalToken,
    customerId: CustomerId,
    balanceId: BalanceId,
    createdAt: Instant,
    amount: Bitcoins,
    fiatEquivalent: Money,
    targetWalletAddress: Address,
    ledgerTransactionId: LedgerTransactionId
  ): Result<Unit> = Unit.success()
}
