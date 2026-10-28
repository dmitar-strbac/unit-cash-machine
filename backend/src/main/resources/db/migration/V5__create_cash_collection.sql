CREATE TABLE cash_machine.simulated_hardware (
    id SMALLINT PRIMARY KEY
        REFERENCES cash_machine.machine_configuration(id),
    acceptor VARCHAR(20) NOT NULL,
    cashbox VARCHAR(20) NOT NULL,
    door VARCHAR(20) NOT NULL,
    lock_state VARCHAR(20) NOT NULL,
    extension_board VARCHAR(20) NOT NULL,
    printer VARCHAR(20) NOT NULL,

    CONSTRAINT chk_single_simulated_hardware CHECK (id = 1)
);

INSERT INTO cash_machine.simulated_hardware (
    id,
    acceptor,
    cashbox,
    door,
    lock_state,
    extension_board,
    printer
) VALUES (
    1,
    'READY',
    'PRESENT',
    'CLOSED',
    'LOCKED',
    'ONLINE',
    'READY'
);

CREATE TABLE cash_machine.cash_cycle (
    id UUID PRIMARY KEY,
    machine_id SMALLINT NOT NULL
        REFERENCES cash_machine.machine_configuration(id),
    opened_at TIMESTAMP WITH TIME ZONE NOT NULL,
    closed_at TIMESTAMP WITH TIME ZONE,

    CONSTRAINT chk_cash_cycle_dates
        CHECK (closed_at IS NULL OR closed_at >= opened_at)
);

CREATE UNIQUE INDEX uq_open_cash_cycle
    ON cash_machine.cash_cycle(machine_id)
    WHERE closed_at IS NULL;

INSERT INTO cash_machine.cash_cycle (
    id,
    machine_id,
    opened_at
)
VALUES (
    '00000000-0000-0000-0000-000000000100',
    1,
    COALESCE(
        (SELECT MIN(started_at) FROM cash_machine.deposit),
        CURRENT_TIMESTAMP
    )
);

ALTER TABLE cash_machine.deposit
    ADD COLUMN cash_cycle_id UUID;

UPDATE cash_machine.deposit
SET cash_cycle_id = '00000000-0000-0000-0000-000000000100';

ALTER TABLE cash_machine.deposit
    ALTER COLUMN cash_cycle_id SET NOT NULL;

ALTER TABLE cash_machine.deposit
    ADD CONSTRAINT fk_deposit_cash_cycle
        FOREIGN KEY (cash_cycle_id)
        REFERENCES cash_machine.cash_cycle(id);

CREATE INDEX idx_deposit_cash_cycle
    ON cash_machine.deposit(cash_cycle_id);

CREATE TABLE cash_machine.cash_collection (
    id UUID PRIMARY KEY,
    cash_cycle_id UUID NOT NULL UNIQUE
        REFERENCES cash_machine.cash_cycle(id),
    started_by UUID NOT NULL
        REFERENCES cash_machine.user_account(id),
    operator_id UUID NOT NULL
        REFERENCES cash_machine.user_account(id),
    login_id UUID NOT NULL UNIQUE,
    runtime_id UUID NOT NULL,
    lease_expires_at TIMESTAMP WITH TIME ZONE NOT NULL,
    step VARCHAR(30) NOT NULL,
    resume_step VARCHAR(30),
    started_at TIMESTAMP WITH TIME ZONE NOT NULL,
    removal_confirmed_at TIMESTAMP WITH TIME ZONE,
    insertion_confirmed_at TIMESTAMP WITH TIME ZONE,
    securing_deadline TIMESTAMP WITH TIME ZONE,
    finished_at TIMESTAMP WITH TIME ZONE,
    collected_total_cents BIGINT,
    error_code VARCHAR(60),

    CONSTRAINT chk_collection_step CHECK (
        step IN (
            'OPEN_DOOR',
            'REMOVE_CASSETTE',
            'INSERT_CASSETTE',
            'CLOSE_DOOR',
            'SECURING',
            'INCOMPLETE',
            'COMPLETED'
        )
    ),

    CONSTRAINT chk_collection_resume_step CHECK (
        resume_step IS NULL
        OR resume_step IN (
            'OPEN_DOOR',
            'REMOVE_CASSETTE',
            'INSERT_CASSETTE',
            'CLOSE_DOOR'
        )
    ),

    CONSTRAINT chk_collection_completion CHECK (
        (
            step = 'COMPLETED'
            AND finished_at IS NOT NULL
            AND collected_total_cents IS NOT NULL
        )
        OR
        (
            step <> 'COMPLETED'
            AND finished_at IS NULL
            AND collected_total_cents IS NULL
        )
    ),

    CONSTRAINT chk_collection_total CHECK (
        collected_total_cents IS NULL
        OR collected_total_cents >= 0
    ),

    CONSTRAINT chk_collection_confirmations CHECK (
        insertion_confirmed_at IS NULL
        OR removal_confirmed_at IS NOT NULL
    )
);

CREATE UNIQUE INDEX uq_unfinished_cash_collection
    ON cash_machine.cash_collection((1))
    WHERE step <> 'COMPLETED';

CREATE TABLE cash_machine.cash_collection_event (
    id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    collection_id UUID NOT NULL
        REFERENCES cash_machine.cash_collection(id),
    actor_id UUID
        REFERENCES cash_machine.user_account(id),
    event_type VARCHAR(60) NOT NULL,
    recorded_at TIMESTAMP WITH TIME ZONE NOT NULL,
    door_state VARCHAR(20) NOT NULL,
    lock_state VARCHAR(20) NOT NULL,
    cashbox_state VARCHAR(20) NOT NULL
);

CREATE INDEX idx_collection_event_collection
    ON cash_machine.cash_collection_event(collection_id, id);