-- Add a 'step_up_authenticated' column to the 'withdrawals' table
ALTER TABLE withdrawals
ADD COLUMN step_up_authenticated BOOLEAN DEFAULT NULL;
