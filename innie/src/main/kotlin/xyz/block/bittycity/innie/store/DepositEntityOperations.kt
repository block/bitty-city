package xyz.block.bittycity.innie.store

import xyz.block.bittycity.innie.models.Deposit
import xyz.block.bittycity.innie.models.DepositReversal
import xyz.block.bittycity.innie.models.DepositToken

interface DepositEntityOperations {

  fun insertDeposit(deposit: Deposit): Result<Deposit>

  fun getByToken(token: DepositToken): Result<Deposit>

  fun findByToken(token: DepositToken): Result<Deposit?>

  fun updateDeposit(deposit: Deposit): Result<Deposit>

  fun addReversal(id: DepositToken, reversal: DepositReversal): Result<DepositReversal>

  fun getLatestReversal(id: DepositToken): Result<DepositReversal?>
}
