package xyz.block.bittycity.innie.store

import org.bitcoinj.base.Address
import java.time.Instant
import xyz.block.bittycity.common.models.Bitcoins
import xyz.block.bittycity.common.models.CustomerId
import xyz.block.bittycity.innie.models.Deposit
import xyz.block.bittycity.innie.models.DepositReversal
import xyz.block.bittycity.innie.models.DepositReversalToken
import xyz.block.bittycity.innie.models.DepositState
import xyz.block.bittycity.innie.models.DepositToken
import xyz.block.domainapi.InfoOnly

interface DepositEntityOperations {

  fun insert(deposit: Deposit): Result<Deposit>

  fun getByToken(token: DepositToken): Result<Deposit>

  fun getByTokens(tokens: List<DepositToken>): Result<Map<DepositToken, Deposit?>>

  fun findByToken(token: DepositToken): Result<Deposit?>

  fun update(deposit: Deposit): Result<Deposit>

  @Suppress("LongParameterList")
  fun searchDeposits(
    customerId: CustomerId?,
    from: Instant? = null,
    to: Instant? = null,
    minAmount: Bitcoins? = null,
    maxAmount: Bitcoins? = null,
    states: Set<DepositState> = setOf(),
    targetWalletAddress: Address? = null,
    paymentToken: String? = null,
    reversalToken: DepositReversalToken? = null
  ): Result<List<Deposit>>

  fun addReversal(id: DepositToken, reversal: DepositReversal): Result<DepositReversal>

  fun getLatestReversal(id: DepositToken): Result<DepositReversal?>
}

sealed class DepositStoreError(message: String) :
  Exception(message),
  InfoOnly

class DepositNotPresent(val depositToken: DepositToken) :
  DepositStoreError("Deposit not present: $depositToken")

class DepositTokensEmpty : DepositStoreError("Deposit tokens not present")

class TooManyDepositTokens(val depositTokenCount: Int, val depositTokenLimit: Int) :
  DepositStoreError(
    "Too many deposit tokens: $depositTokenCount, exceeded limit: $depositTokenLimit"
  )

class DepositVersionMismatch(val deposit: Deposit) :
  DepositStoreError(
    "Deposit not at expected version ${deposit.version}: ${deposit.id}"
  )
