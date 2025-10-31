-- Add a 'note' column to the 'withdrawals' table
ALTER TABLE withdrawals
ADD COLUMN note VARCHAR(255);
