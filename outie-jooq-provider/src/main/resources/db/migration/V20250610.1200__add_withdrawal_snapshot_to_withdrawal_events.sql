-- Add withdrawal_snapshot column to store withdrawal snapshots as JSON  
-- This column stores the complete withdrawal state at the time of the event
ALTER TABLE withdrawal_events
  ADD COLUMN withdrawal_snapshot JSON NULL; 