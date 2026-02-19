package xyz.block.bittycity.innie.api

import app.cash.quiver.extensions.failure
import app.cash.quiver.extensions.success
import arrow.core.getOrElse
import arrow.core.raise.result
import jakarta.inject.Inject
import java.util.UUID
import org.bitcoinj.base.Address
import org.joda.money.CurrencyUnit
import xyz.block.bittycity.common.client.BitcoinAccountClient
import xyz.block.bittycity.common.client.Eligibility
import xyz.block.bittycity.common.client.ExchangeRateClient
import xyz.block.bittycity.innie.client.DepositEligibilityClient
import xyz.block.bittycity.common.client.IneligibleCustomer
import xyz.block.bittycity.common.models.BalanceId
import xyz.block.bittycity.common.models.Bitcoins
import xyz.block.bittycity.common.models.CustomerId
import xyz.block.bittycity.common.store.Transactor
import xyz.block.bittycity.common.utils.WalletAddressParser
import xyz.block.bittycity.innie.client.WalletClient
import xyz.block.bittycity.innie.controllers.DomainController
import xyz.block.bittycity.innie.controllers.IdempotencyHandler
import xyz.block.bittycity.innie.models.Deposit
import xyz.block.bittycity.innie.models.DepositState
import xyz.block.bittycity.innie.models.CheckingEligibility
import xyz.block.bittycity.innie.models.DepositToken
import xyz.block.bittycity.innie.models.RequirementId
import xyz.block.bittycity.innie.models.AwaitingDepositConfirmation
import xyz.block.bittycity.innie.store.DepositOperations
import xyz.block.bittycity.innie.store.DepositStore
import xyz.block.bittycity.innie.utils.SearchParser
import xyz.block.domainapi.DomainApi
import xyz.block.domainapi.ExecuteResponse
import xyz.block.domainapi.InfoOnly
import xyz.block.domainapi.Input
import xyz.block.domainapi.ProcessInfo
import xyz.block.domainapi.ResultCode.CLEARED
import xyz.block.domainapi.ResultCode.FAILED
import xyz.block.domainapi.ResultCode.SKIPPED
import xyz.block.domainapi.SearchParameter
import xyz.block.domainapi.SearchResult
import xyz.block.domainapi.UpdateResponse
import xyz.block.domainapi.kfsm.v2.util.Operation

typealias DepositDomainController =
  DomainController<DepositToken, DepositState, Deposit, RequirementId>

class OnChainDepositDomainApi @Inject constructor(
  private val transactor: Transactor<DepositOperations>,
  private val domainController: DepositDomainController,
  private val bitcoinAccountClient: BitcoinAccountClient,
  private val eligibilityClient: DepositEligibilityClient,
  private val exchangeRateClient: ExchangeRateClient,
  private val walletClient: WalletClient,
  private val walletAddressParser: WalletAddressParser,
  private val depositStore: DepositStore,
  private val idempotencyHandler: IdempotencyHandler
) : DomainApi<InitialRequest, DepositToken, RequirementId, Unit, Deposit> {
  override fun create(
    id: DepositToken,
    initialRequest: InitialRequest,
    hurdleGroupId: String?
  ): Result<ExecuteResponse<DepositToken, RequirementId>> = result {
    val customerId = walletClient.lookupWallet(initialRequest.targetWalletAddress).bind()
      ?: raise(NoCustomerFoundForWalletAddress(initialRequest.targetWalletAddress))
    when (val eligibility = eligibilityClient.productEligibility(customerId.id).bind()) {
      is Eligibility.Eligible -> doCreate(id, customerId, initialRequest).bind()
      is Eligibility.Ineligible -> raise(IneligibleCustomer(eligibility.violations))
    }
  }

  private fun doCreate(id: DepositToken, customerId: CustomerId, initialRequest: InitialRequest) = result {
    val exchangeRateQuote = exchangeRateClient.quoteExchange(
      initialRequest.amount,
      CurrencyUnit.USD
    ).bind()
    val deposit = transactor.transact("insert deposit $id") {
      val existingDeposit = findByToken(id).bind()
      existingDeposit?.success() ?: insert(
        Deposit(
          id = id,
          state = mapPaymentState(initialRequest.paymentState),
          customerId = customerId,
          amount = initialRequest.amount,
          exchangeRate = exchangeRateQuote.exchangeRate,
          targetWalletAddress = initialRequest.targetWalletAddress,
          blockchainTransactionId = initialRequest.blockchainTransactionId,
          blockchainTransactionOutputIndex = initialRequest.blockchainTransactionOutputIndex,
          paymentToken = initialRequest.paymentToken,
          targetBalanceToken = getSourceBalanceToken(customerId).bind()
        )
      )
    }.bind()
    domainController.execute(deposit, emptyList(), Operation.CREATE).bind()
  }

  override fun execute(
    id: DepositToken,
    hurdleResponses: List<Input.HurdleResponse<RequirementId>>,
    hurdleGroupId: String?
  ): Result<ExecuteResponse<DepositToken, RequirementId>> = result {
    val deposit = transactor.transactReadOnly("get deposit") { getByToken(id) }.bind()
    idempotencyHandler.handle(
      id = id,
      backCounter = 0, // always zero - we can't go back
      hurdleResponses = hurdleResponses
    ).bind().getOrElse { hash ->
      val result = domainController.execute(
        deposit,
        hurdleResponses,
        Operation.EXECUTE,
        null // no hurdles groups for deposits
      )

      idempotencyHandler.updateCachedResponse(hash, id, result).bind()
      result.bind()
    }
  }

  override fun resume(
    id: DepositToken,
    resumeResult: Input.ResumeResult<RequirementId>
  ): Result<Unit> = result {
    val deposit = transactor.transactReadOnly("get deposit") { getByToken(id) }.bind()
    idempotencyHandler.handleResume(
      id,
      resumeResult
    ).bind().getOrElse { hash ->
      val result = domainController.execute(
        deposit,
        listOf(resumeResult),
        Operation.RESUME
      )

      idempotencyHandler.updateCachedResponse(hash, id, result).bind()
      result.bind()
    }
  }

  fun requiresSecureEndpoint(hurdleResponses: List<Input.HurdleResponse<RequirementId>>): Boolean =
    hurdleResponses.any {
      it.id.requiresSecureEndpoint && setOf(CLEARED, FAILED, SKIPPED).contains(it.result)
    }

  override fun update(
    id: DepositToken,
    attributeId: Unit,
    hurdleResponses: List<Input.HurdleResponse<RequirementId>>
  ): Result<UpdateResponse<RequirementId, Unit>> = UnsupportedOperationException().failure()

  override fun get(id: DepositToken): Result<ProcessInfo<Unit, Deposit>> = result {
    val deposit = transactor.transactReadOnly("get deposit by id") {
      getByToken(id)
    }.bind()

    ProcessInfo(
      id = deposit.id.toString(),
      updatableAttributes = emptyList(),
      process = deposit
    )
  }

  fun getMany(
    ids: List<DepositToken>
  ): Result<ProcessInfo<Unit, Map<DepositToken, Deposit?>>> = result {
    val deposits = transactor.transactReadOnly("get deposits by ids") {
      getByTokens(ids)
    }.bind()

    ProcessInfo(
      id = UUID.randomUUID().toString(),
      updatableAttributes = emptyList(),
      process = deposits
    )
  }

  override fun search(
    parameter: SearchParameter,
    limit: Int
  ): Result<SearchResult<Unit, Deposit>> = result {
    val parsed = SearchParser().parseFilters(parameter).bind()
    val targetWalletAddress = parsed.targetWalletAddress
      ?.let { walletAddressParser.parse(it).bind() }

    val results: List<ProcessInfo<Unit, Deposit>> = depositStore.search(
      customerId = parsed.customerId,
      from = parsed.from,
      to = parsed.to,
      minAmount = parsed.minAmount,
      maxAmount = parsed.maxAmount,
      states = parsed.states.toSet(),
      targetWalletAddress = targetWalletAddress,
      paymentToken = parsed.paymentToken
    ).bind().map {
      ProcessInfo<Unit, Deposit>(
        id = it.id.toString(),
        updatableAttributes = emptyList<Unit>(),
        process = it
      )
    }

    SearchResult(
      results = results,
      thisStart = 0,
      nextStart = null,
      prevStart = null,
      limit = limit
    )
  }

  private fun getSourceBalanceToken(
    customerId: CustomerId
  ): Result<BalanceId> = result {
    val customerSourceBalanceTokens = bitcoinAccountClient.getBitcoinAccounts(customerId).bind()
    customerSourceBalanceTokens.firstOrNull()?.balanceId ?: raise(
      IllegalStateException("No default balance token found")
    )
  }

  companion object {
    fun mapPaymentState(state: PaymentState): DepositState = when (state) {
      PaymentState.AWAITING_CONFIRMATION -> AwaitingDepositConfirmation
      PaymentState.COMPLETED_CONFIRMED -> CheckingEligibility
    }
  }
}

data class InitialRequest(
  val id: DepositToken,
  val paymentToken: String,
  val targetWalletAddress: Address,
  val amount: Bitcoins,
  val blockchainTransactionId: String,
  val blockchainTransactionOutputIndex: Int,
  val paymentState: PaymentState
)

enum class PaymentState {
  AWAITING_CONFIRMATION,
  COMPLETED_CONFIRMED
}

sealed class ApiError :
  Exception(),
  InfoOnly

data class NoCustomerFoundForWalletAddress(
  val address: Address,
): ApiError() {
  override val message: String = "No customer was found for wallet $address"
}
