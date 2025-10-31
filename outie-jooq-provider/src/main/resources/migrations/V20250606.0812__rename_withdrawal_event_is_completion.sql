ALTER TABLE withdrawal_events
  CHANGE COLUMN is_completion is_processed TINYINT(1) NULL;
