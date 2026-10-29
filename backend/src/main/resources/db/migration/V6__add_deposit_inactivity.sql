ALTER TABLE cash_machine.deposit
    ADD COLUMN inactivity_expires_at TIMESTAMP WITH TIME ZONE;

UPDATE cash_machine.deposit
SET inactivity_expires_at = LEAST(
    expires_at,
    started_at + INTERVAL '60 seconds'
);

ALTER TABLE cash_machine.deposit
    ALTER COLUMN inactivity_expires_at SET NOT NULL;

ALTER TABLE cash_machine.deposit
    ADD CONSTRAINT chk_deposit_inactivity_expiration CHECK (
        inactivity_expires_at >= started_at
        AND inactivity_expires_at <= expires_at
    );

CREATE INDEX idx_active_deposit_inactivity
    ON cash_machine.deposit(inactivity_expires_at)
    WHERE status = 'ACTIVE';