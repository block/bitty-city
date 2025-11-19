package xyz.block.bittycity.outie.api

import app.cash.quiver.extensions.failure
import app.cash.quiver.extensions.success
import arrow.core.flatMap
import arrow.core.getOrElse
import arrow.core.raise.result
import xyz.block.bittycity.outie.client.BitcoinAccountClient
import xyz.block.bittycity.outie.client.Eligibility
import xyz.block.bittycity.outie.client.EligibilityClient
import xyz.block.bittycity.common.client.ExchangeRateClient
import xyz.block.bittycity.outie.client.IneligibleCustomer
import xyz.block.bittycity.outie.controllers.AdminFailController
import xyz.block.bittycity.outie.controllers.DomainController
import xyz.block.bittycity.outie.models.BalanceId
import xyz.block.bittycity.common.models.Bitcoins
import xyz.block.bittycity.outie.models.CollectingInfo
import xyz.block.bittycity.common.models.CustomerId
import xyz.block.bittycity.outie.models.RequirementId
import xyz.block.bittycity.outie.models.Withdrawal
import xyz.block.bittycity.outie.models.WithdrawalState
import xyz.block.bittycity.outie.models.WithdrawalToken
import xyz.block.bittycity.outie.controllers.IdempotencyHandler
import xyz.block.bittycity.common.store.Transactor
import xyz.block.bittycity.outie.store.WithdrawalOperations
import xyz.block.bittycity.outie.store.WithdrawalStore
import xyz.block.bittycity.outie.utils.SearchParser
import xyz.block.bittycity.outie.validation.InvalidSourceBalanceToken
import xyz.block.bittycity.outie.validation.ValidationService
import jakarta.inject.Inject
import jakarta.inject.Singleton
import org.bitcoinj.base.Address
import org.joda.money.CurrencyUnit
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
import xyz.block.domainapi.WarnOnly
import xyz.block.domainapi.util.Operation
import java.util.UUID

typealias WithdrawalDomainController =
  DomainController<WithdrawalToken, WithdrawalState, Withdrawal, RequirementId>

@Suppress("LongParameterList", "TooManyFunctions")
@Singleton
class OnChainWithdrawalDomainApi @Inject constructor(
  private val transactor: Transactor<WithdrawalOperations>,
  private val withdrawalStore: WithdrawalStore,
  private val domainController: WithdrawalDomainController,
  private val bitcoinAccountClient: BitcoinAccountClient,
  private val eligibilityClient: EligibilityClient,
  private val exchangeRateClient: ExchangeRateClient,
  private val validationService: ValidationService,
  private val idempotencyHandler: IdempotencyHandler,
  private val adminFailController: AdminFailController
) : DomainApi<InitialRequest, WithdrawalToken, RequirementId, AttributeId, Withdrawal> {

  override fun create(
    id: WithdrawalToken,
    initialRequest: InitialRequest,
    hurdleGroupId: String?
  ): Result<ExecuteResponse<WithdrawalToken, RequirementId>> = result {
    val eligibility = eligibilityClient.productEligibility(
      initialRequest.customerId.id
    ).bind()

    when (eligibility) {
      is Eligibility.Eligible -> doCreate(id, initialRequest).bind()
      is Eligibility.Ineligible -> raise(IneligibleCustomer(eligibility.violations))
    }
  }

  private fun doCreate(id: WithdrawalToken, initialRequest: InitialRequest) = result {
    val sourceBalanceToken = validateSourceBalanceToken(
      initialRequest.customerId,
      initialRequest.sourceBalanceToken
    ).bind()
    val validatedInitialRequest = validateInitialRequest(initialRequest, sourceBalanceToken).bind()
    val amount = validatedInitialRequest.amount
    val exchangeRateQuote = exchangeRateClient.quoteExchange(
      amount ?: Bitcoins(1),
      CurrencyUnit.USD
    ).bind()
    val withdrawal = transactor.transact("Insert withdrawal $id") {
      findByToken(id).flatMap { existingWithdrawal ->
        existingWithdrawal?.let { withdrawal ->
          if (withdrawal.customerId != validatedInitialRequest.customerId) {
            raise(
              WithdrawalTokenConflictError(
                withdrawalToken = id,
                existingCustomerId = withdrawal.customerId,
                requestedCustomerId = validatedInitialRequest.customerId
              )
            )
          }
          withdrawal.success()
        } ?: insertWithdrawal(
          withdrawalToken = id,
          customerId = validatedInitialRequest.customerId,
          state = CollectingInfo,
          sourceBalanceToken = sourceBalanceToken,
          source = Withdrawal.DEFAULT_SOURCE,
          amount = amount,
          exchangeRate = exchangeRateQuote.exchangeRate,
          targetWalletAddress = validatedInitialRequest.targetWalletAddress,
          note = validatedInitialRequest.note,
        )
      }
    }.bind()
    domainController.execute(withdrawal, emptyList(), Operation.CREATE).bind()
  }

  override fun execute(
    id: WithdrawalToken,
    hurdleResponses: List<Input.HurdleResponse<RequirementId>>,
    hurdleGroupId: String?
  ): Result<ExecuteResponse<WithdrawalToken, RequirementId>> = result {
    val withdrawal = transactor.transactReadOnly("Get withdrawal") { getByToken(id) }.bind()
    idempotencyHandler.handle(
      id,
      withdrawal.backCounter,
      hurdleResponses
    ).bind().getOrElse { hash ->
      val result = domainController.execute(
        withdrawal,
        hurdleResponses,
        Operation.EXECUTE,
        hurdleGroupId
      )

      // Errors at the API level are never retryable for withdrawals so we can save error responses
      idempotencyHandler.updateCachedResponse(hash, id, result).bind()
      result.bind()
    }
  }

  override fun get(id: WithdrawalToken): Result<ProcessInfo<AttributeId, Withdrawal>> = result {
    val withdrawal = transactor.transactReadOnly("get withdrawal by id") {
      getByToken(id)
    }.bind()

    ProcessInfo(
      id = withdrawal.id.toString(),
      updatableAttributes = AttributeId.entries,
      process = withdrawal
    )
  }

  fun getMany(
    ids: List<WithdrawalToken>
  ): Result<ProcessInfo<AttributeId, Map<WithdrawalToken, Withdrawal?>>> = result {
    val withdrawals = transactor.transactReadOnly("get withdrawal by id") {
      getByTokens(ids)
    }.bind()

    ProcessInfo(
      id = UUID.randomUUID().toString(),
      updatableAttributes = AttributeId.entries,
      process = withdrawals
    )
  }

  override fun search(
    parameter: SearchParameter,
    limit: Int
  ): Result<SearchResult<AttributeId, Withdrawal>> = result {
    val parsed = SearchParser().parseFilters(parameter).bind()

    val res = withdrawalStore.search(
      customerId = parsed.customerId,
      from = parsed.from,
      to = parsed.to,
      minAmount = parsed.minAmount,
      maxAmount = parsed.maxAmount,
      states = parsed.states.toSet(),
      destinationAddress = parsed.destinationAddress
    ).bind().map {
      ProcessInfo(
        id = it.id.toString(),
        updatableAttributes = AttributeId.entries,
        process = it
      )
    }

    SearchResult(
      results = res,
      // For now our search results are not paginated.
      thisStart = 0,
      nextStart = null,
      prevStart = null,
      limit = limit
    )
  }

  override fun update(
    id: WithdrawalToken,
    attributeId: AttributeId,
    hurdleResponses: List<Input.HurdleResponse<RequirementId>>
  ): Result<UpdateResponse<RequirementId, AttributeId>> = UnsupportedOperationException().failure()

  override fun resume(
    id: WithdrawalToken,
    resumeResult: Input.ResumeResult<RequirementId>
  ): Result<Unit> = result {
    val withdrawal = transactor.transactReadOnly("Get withdrawal") {
      getByToken(id)
    }.bind()
    idempotencyHandler.handleResume(
      id,
      resumeResult
    ).bind().getOrElse { hash ->
      val result = domainController.execute(
        withdrawal,
        listOf(resumeResult),
        Operation.RESUME
      )

      // Errors at the API level are never retryable for withdrawals so we can save error responses
      idempotencyHandler.updateCachedResponse(hash, id, result).bind()
      result.bind()
    }
  }

  fun requiresSecureEndpoint(hurdleResponses: List<Input.HurdleResponse<RequirementId>>): Boolean =
    hurdleResponses.any {
      it.id.requiresSecureEndpoint && setOf(CLEARED, FAILED, SKIPPED).contains(it.result)
    }

  /**
   * Fails a withdrawal. This can be used as an admin action to fail withdrawals that are stuck.
   *
   * @param id The withdrawal token.
   */
  fun failWithdrawal(id: WithdrawalToken): Result<ExecuteResponse<WithdrawalToken, RequirementId>> =
    result {
      val withdrawal = transactor.transactReadOnly("Get withdrawal") { getByToken(id) }.bind()
      adminFailController.processInputs(withdrawal, emptyList(), Operation.EXECUTE).bind()
      ExecuteResponse(id = withdrawal.id, interactions = emptyList(), nextEndpoint = null)
    }

  /**
   * Validates the initial request, except the source balance token, which is validated separately.
   * Only validates values that are present because some of this might be collected later through
   * hurdles.
   *
   * @param initialRequest The initial request to withdraw Bitcoin.
   * @param balanceId The id of the balance to use for the withdrawal.
   *
   * @return A validated initial request.
   */
  private fun validateInitialRequest(
    initialRequest: InitialRequest,
    balanceId: BalanceId
  ): Result<InitialRequest> = result {
    val customerId = initialRequest.customerId
    initialRequest.apply {
      amount?.let { validationService.validateAmount(customerId, balanceId, it).bind() }
      note?.let { validationService.validateNote(customerId, it).bind() }
    }
  }

  /**
   * The initial request can include a source balance token if the system supports more than one
   * Bitcoin account and the customer wants to specify which account to use to withdraw from. If one
   * is provided the system must validate that it exists and that it belongs to the customer.
   *
   * If no source balance token is provided, the system will use the default one.
   *
   * @param customerId The customer id.
   * @param sourceBalanceToken The source balance token.
   *
   * @return The validated source balance token.
   */
  private fun validateSourceBalanceToken(
    customerId: CustomerId,
    sourceBalanceToken: BalanceId?
  ): Result<BalanceId> = result {
    val customerSourceBalanceTokens = bitcoinAccountClient.getBitcoinAccounts(customerId).bind()
    sourceBalanceToken?.let { providedSourceBalanceToken ->
      customerSourceBalanceTokens.find { it.balanceId == providedSourceBalanceToken }?.balanceId
        ?: raise(InvalidSourceBalanceToken(customerId, providedSourceBalanceToken))
    } ?: customerSourceBalanceTokens.firstOrNull()?.balanceId ?: raise(
      InvalidSourceBalanceToken(customerId, BalanceId("No default balance token found"))
    )
  }
}

enum class AttributeId {
  NOTE
}

data class InitialRequest(
  val customerId: CustomerId,
  val targetWalletAddress: Address? = null,
  val amount: Bitcoins? = null,
  val note: String? = null,
  val sourceBalanceToken: BalanceId? = null,
)

sealed class ApiError :
  Exception(),
    InfoOnly

data class WithdrawalTokenConflictError(
  val withdrawalToken: WithdrawalToken,
  val existingCustomerId: CustomerId,
  val requestedCustomerId: CustomerId
) : ApiError() {
  override val message: String = "Withdrawal token $withdrawalToken" +
    " already exists but belongs to a different customer"
}

data class ClientError(val code: Int, override val message: String) :
  Throwable(),
  WarnOnly
