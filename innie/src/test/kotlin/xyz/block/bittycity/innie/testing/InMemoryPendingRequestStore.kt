package xyz.block.bittycity.innie.testing

import app.cash.kfsm.v2.PendingRequestStatus
import app.cash.kfsm.v2.PendingRequestStore
import xyz.block.bittycity.innie.models.Deposit
import xyz.block.bittycity.innie.models.DepositToken
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

class InMemoryPendingRequestStore : PendingRequestStore<DepositToken, Deposit> {

  private data class PendingRequest(
    val valueId: DepositToken,
    val status: PendingRequestStatus<Deposit>
  )

  private val requests = ConcurrentHashMap<String, PendingRequest>()
  private val valueIdToRequestId = ConcurrentHashMap<DepositToken, String>()

  override fun create(valueId: DepositToken): String {
    val requestId = UUID.randomUUID().toString()
    requests[requestId] = PendingRequest(valueId, PendingRequestStatus.Waiting)
    valueIdToRequestId[valueId] = requestId
    return requestId
  }

  override fun getStatus(requestId: String): PendingRequestStatus<Deposit> =
    requests[requestId]?.status ?: PendingRequestStatus.NotFound

  override fun complete(valueId: DepositToken, value: Deposit) {
    val requestId = valueIdToRequestId[valueId] ?: return
    requests[requestId] = PendingRequest(valueId, PendingRequestStatus.Completed(value))
  }

  override fun fail(valueId: DepositToken, error: String) {
    val requestId = valueIdToRequestId[valueId] ?: return
    requests[requestId] = PendingRequest(valueId, PendingRequestStatus.Failed(error))
  }

  override fun markTimedOut(requestId: String) {
    val request = requests[requestId] ?: return
    requests[requestId] = request.copy(status = PendingRequestStatus.Failed("Timed out"))
  }

  override fun delete(requestId: String) {
    val request = requests.remove(requestId)
    request?.let { valueIdToRequestId.remove(it.valueId) }
  }

  fun allRequestIds(): Set<String> = requests.keys.toSet()

  fun size(): Int = requests.size

  fun exists(requestId: String): Boolean = requests.containsKey(requestId)

  fun clear() {
    requests.clear()
    valueIdToRequestId.clear()
  }
}
