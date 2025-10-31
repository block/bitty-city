ALTER TABLE withdrawals
  ADD INDEX ix_withdrawals_state_updated_at (state, updated_at);
