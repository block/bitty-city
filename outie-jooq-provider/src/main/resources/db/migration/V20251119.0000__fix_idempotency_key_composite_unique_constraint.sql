-- Drop the existing unique constraint on idempotency_key alone
-- and replace it with a composite unique constraint on (idempotency_key, withdrawal_token)
-- This ensures that the same idempotency_key can be used for different withdrawal tokens,
-- which properly scopes collision risk to individual withdrawals (UUIDs).

ALTER TABLE withdrawal_responses
DROP INDEX idempotency_key,
ADD UNIQUE INDEX idempotency_composite_idx (idempotency_key, withdrawal_token);
