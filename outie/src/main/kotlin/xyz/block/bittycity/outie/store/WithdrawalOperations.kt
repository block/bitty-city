package xyz.block.bittycity.outie.store

import xyz.block.bittycity.common.store.Operations

interface WithdrawalOperations : WithdrawalEventOperations, WithdrawalEntityOperations, OutboxOperations, Operations
