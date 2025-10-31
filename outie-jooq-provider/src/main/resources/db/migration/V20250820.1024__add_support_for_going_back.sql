ALTER TABLE withdrawals
  ADD COLUMN previous_target_wallet VARCHAR(128),
  ADD COLUMN previous_satoshis BIGINT UNSIGNED,
  ADD COLUMN previous_note VARCHAR(255),
  ADD COLUMN back_counter INT NOT NULL DEFAULT 0;
