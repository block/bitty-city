ALTER TABLE withdrawals
  ADD COLUMN user_has_confirmed BOOLEAN,
  ADD COLUMN exchange_rate_units BIGINT,
  ADD COLUMN exchange_rate_currency VARCHAR(16);
