ALTER TABLE withdrawals
  ADD COLUMN reason_for_withdrawal VARCHAR(255),
  ADD COLUMN fee_refunded BOOLEAN NOT NULL DEFAULT FALSE;
