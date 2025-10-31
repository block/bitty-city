ALTER TABLE withdrawals
    DROP COLUMN usd_equivalent_cents,
    DROP COLUMN block_target,
    DROP COLUMN fee,
    DROP COLUMN selected_speed,
    DROP COLUMN fee_margin,
    DROP COLUMN fee_flat_rate;
