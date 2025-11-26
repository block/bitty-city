package xyz.block.bittycity.outie.jooq

import com.squareup.moshi.Moshi
import org.jooq.DSLContext
import xyz.block.bittycity.outie.store.WithdrawalEntityOperations
import xyz.block.bittycity.outie.store.WithdrawalEventOperations
import xyz.block.bittycity.outie.store.WithdrawalOperations
import xyz.block.bittycity.outie.store.OutboxOperations
import xyz.block.bittycity.outie.validation.WalletAddressParser
import java.time.Clock

class JooqWithdrawalOperations(
    private val context: DSLContext,
    val walletAddressParser: WalletAddressParser,
    val clock: Clock,
    val moshi: Moshi,
) : WithdrawalEntityOperations by JooqWithdrawalEntityOperations(context, walletAddressParser, clock),
    WithdrawalEventOperations by JooqWithdrawalEventOperations(context, moshi),
    OutboxOperations by JooqOutboxOperations(context, moshi),
    WithdrawalOperations

