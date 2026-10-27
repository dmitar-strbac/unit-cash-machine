CREATE TABLE cash_machine.deposit (
    id UUID PRIMARY KEY,
    machine_id SMALLINT NOT NULL
        REFERENCES cash_machine.machine_configuration(id),
    user_id UUID NOT NULL
        REFERENCES cash_machine.user_account(id),
    login_id UUID NOT NULL UNIQUE,
    runtime_id UUID NOT NULL,
    status VARCHAR(20) NOT NULL,
    started_at TIMESTAMP WITH TIME ZONE NOT NULL,
    expires_at TIMESTAMP WITH TIME ZONE NOT NULL,
    finished_at TIMESTAMP WITH TIME ZONE,

    CONSTRAINT chk_deposit_status
        CHECK (status IN ('ACTIVE', 'COMPLETED', 'EXPIRED', 'INTERRUPTED')),

    CONSTRAINT chk_deposit_expiration
        CHECK (expires_at > started_at),

    CONSTRAINT chk_deposit_completion
        CHECK (
            (status = 'ACTIVE' AND finished_at IS NULL)
            OR
            (status <> 'ACTIVE' AND finished_at IS NOT NULL)
        ),

    CONSTRAINT chk_deposit_finished_at
        CHECK (finished_at IS NULL OR finished_at >= started_at)
);

CREATE UNIQUE INDEX uq_active_deposit_per_machine
    ON cash_machine.deposit(machine_id)
    WHERE status = 'ACTIVE';

CREATE INDEX idx_deposit_user_started_at
    ON cash_machine.deposit(user_id, started_at);

CREATE TABLE cash_machine.deposit_banknote_operation (
    id UUID PRIMARY KEY,
    deposit_id UUID NOT NULL
        REFERENCES cash_machine.deposit(id),
    currency VARCHAR(3) NOT NULL,
    denomination INTEGER NOT NULL,
    outcome VARCHAR(30) NOT NULL,
    recorded_at TIMESTAMP WITH TIME ZONE NOT NULL,

    CONSTRAINT chk_banknote_currency
        CHECK (currency ~ '^[A-Z]{3}$'),

    CONSTRAINT chk_banknote_denomination
        CHECK (denomination > 0),

    CONSTRAINT chk_banknote_outcome
        CHECK (
            outcome IN (
                'ACCEPTED',
                'CURRENCY_REJECTED',
                'DENOMINATION_REJECTED'
            )
        ),

    CONSTRAINT chk_accepted_banknote
        CHECK (
            outcome <> 'ACCEPTED'
            OR (
                currency = 'EUR'
                AND denomination IN (5, 10, 20, 50, 100, 200, 500)
            )
        )
);

CREATE INDEX idx_banknote_operation_deposit
    ON cash_machine.deposit_banknote_operation(deposit_id);