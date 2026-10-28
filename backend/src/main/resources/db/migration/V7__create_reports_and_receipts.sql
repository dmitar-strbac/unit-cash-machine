ALTER TABLE cash_machine.deposit
    ADD COLUMN transaction_number BIGINT GENERATED ALWAYS AS IDENTITY;

ALTER TABLE cash_machine.deposit
    ADD CONSTRAINT uq_deposit_transaction_number UNIQUE (transaction_number);

ALTER TABLE cash_machine.deposit
    ADD COLUMN receipt_required BOOLEAN NOT NULL DEFAULT FALSE;

UPDATE cash_machine.deposit
SET receipt_required = TRUE
WHERE status = 'ACTIVE';

ALTER TABLE cash_machine.deposit
    ALTER COLUMN receipt_required SET DEFAULT TRUE;

ALTER TABLE cash_machine.deposit
    ADD COLUMN receipt_access_hash VARCHAR(64),
    ADD COLUMN receipt_access_expires_at TIMESTAMP WITH TIME ZONE;

CREATE UNIQUE INDEX uq_deposit_receipt_access
    ON cash_machine.deposit(receipt_access_hash)
    WHERE receipt_access_hash IS NOT NULL;

CREATE INDEX idx_accepted_banknote_recorded_at
    ON cash_machine.deposit_banknote_operation(recorded_at, deposit_id)
    WHERE outcome = 'ACCEPTED';

CREATE TABLE cash_machine.receipt (
    id UUID PRIMARY KEY,
    kind VARCHAR(30) NOT NULL,
    deposit_id UUID UNIQUE
        REFERENCES cash_machine.deposit(id),
    requested_by UUID NOT NULL
        REFERENCES cash_machine.user_account(id),
    request_id UUID UNIQUE,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    total_cents BIGINT NOT NULL,
    content TEXT NOT NULL,

    CONSTRAINT chk_receipt_kind CHECK (
        kind IN ('DEPOSIT', 'DEVICE_TOTAL', 'DAY_TOTAL', 'DAY_TOTAL_USER')
    ),

    CONSTRAINT chk_receipt_total CHECK (total_cents >= 0),

    CONSTRAINT chk_receipt_source CHECK (
        (
            kind = 'DEPOSIT'
            AND deposit_id IS NOT NULL
            AND request_id IS NULL
        )
        OR
        (
            kind <> 'DEPOSIT'
            AND deposit_id IS NULL
            AND request_id IS NOT NULL
        )
    )
);

CREATE INDEX idx_receipt_created
    ON cash_machine.receipt(created_at DESC, id DESC);

CREATE TABLE cash_machine.print_attempt (
    id UUID PRIMARY KEY,
    receipt_id UUID NOT NULL
        REFERENCES cash_machine.receipt(id),
    requested_at TIMESTAMP WITH TIME ZONE NOT NULL,
    status VARCHAR(20) NOT NULL,
    error_code VARCHAR(40),

    CONSTRAINT chk_print_attempt_status CHECK (
        status IN ('PRINTED', 'FAILED')
    ),

    CONSTRAINT chk_print_attempt_error CHECK (
        (status = 'PRINTED' AND error_code IS NULL)
        OR
        (status = 'FAILED' AND error_code IS NOT NULL)
    )
);

CREATE INDEX idx_print_attempt_receipt
    ON cash_machine.print_attempt(receipt_id, requested_at DESC, id DESC);