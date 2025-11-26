package xyz.block.bittycity.outie.store

import app.cash.kfsm.OutboxMessage
import app.cash.kfsm.annotations.ExperimentalLibraryApi

interface OutboxOperations {
  @OptIn(ExperimentalLibraryApi::class)
  fun insertOutboxMessage(message: OutboxMessage<String>): Result<Unit>
  
  @OptIn(ExperimentalLibraryApi::class)
  fun fetchPendingMessages(limit: Int): Result<List<OutboxMessage<String>>>
  
  fun markAsProcessed(id: String): Result<Unit>
  
  fun markAsFailed(id: String, error: String?): Result<Unit>
}
