package xyz.block.bittycity.innie.store

import xyz.block.bittycity.innie.models.Deposit
import xyz.block.bittycity.innie.models.DepositReversal
import xyz.block.bittycity.innie.models.DepositToken
import xyz.block.domainapi.InfoOnly

interface DepositEntityOperations {

  fun insert(deposit: Deposit): Result<Deposit>

  fun getByToken(token: DepositToken): Result<Deposit>

  fun findByToken(token: DepositToken): Result<Deposit?>

  fun update(deposit: Deposit): Result<Deposit>

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
