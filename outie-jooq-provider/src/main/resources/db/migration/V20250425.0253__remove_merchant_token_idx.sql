ALTER TABLE withdrawals
  DROP INDEX merchant_token_idx,
  ADD INDEX merchant_token_idx_nonunique (merchant_token);

