ALTER TABLE withdrawal_speed_options
  ADD UNIQUE KEY uq_withdrawal_token_speed (withdrawal_token, speed);
