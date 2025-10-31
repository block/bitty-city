-- Add a 'user_has_accepted_risk' column to the 'withdrawals' table
ALTER TABLE withdrawals
ADD COLUMN user_has_accepted_risk BOOLEAN DEFAULT NULL;
