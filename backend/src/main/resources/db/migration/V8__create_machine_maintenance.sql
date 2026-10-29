CREATE TABLE cash_machine.maintenance_command (
    id UUID PRIMARY KEY,
    action VARCHAR(30) NOT NULL,
    requested_by UUID
        REFERENCES cash_machine.user_account(id),
    status VARCHAR(20) NOT NULL,
    requested_at TIMESTAMP WITH TIME ZONE NOT NULL,
    completes_at TIMESTAMP WITH TIME ZONE NOT NULL,
    finished_at TIMESTAMP WITH TIME ZONE,

    CONSTRAINT chk_maintenance_action CHECK (
        action IN (
            'RESET',
            'REBOOT',
            'SHUT_DOWN',
            'EXIT_APPLICATION',
            'POWER_ON',
            'START_APPLICATION'
        )
    ),

    CONSTRAINT chk_maintenance_status CHECK (
        status IN ('PENDING', 'COMPLETED')
    ),

    CONSTRAINT chk_maintenance_completion CHECK (
        (status = 'PENDING' AND finished_at IS NULL)
        OR
        (status = 'COMPLETED' AND finished_at IS NOT NULL)
    ),

    CONSTRAINT chk_maintenance_dates CHECK (
        completes_at >= requested_at
        AND (finished_at IS NULL OR finished_at >= requested_at)
    ),

    CONSTRAINT chk_maintenance_actor CHECK (
        (
            action IN ('POWER_ON', 'START_APPLICATION')
            AND requested_by IS NULL
        )
        OR
        (
            action IN ('RESET', 'REBOOT', 'SHUT_DOWN', 'EXIT_APPLICATION')
            AND requested_by IS NOT NULL
        )
    )
);

CREATE UNIQUE INDEX uq_pending_maintenance_command
    ON cash_machine.maintenance_command((1))
    WHERE status = 'PENDING';

CREATE TABLE cash_machine.machine_runtime (
    id SMALLINT PRIMARY KEY
        REFERENCES cash_machine.machine_configuration(id),
    state VARCHAR(30) NOT NULL,
    generation BIGINT NOT NULL DEFAULT 0,
    command_id UUID
        REFERENCES cash_machine.maintenance_command(id),
    completes_at TIMESTAMP WITH TIME ZONE,
    notice_until TIMESTAMP WITH TIME ZONE,

    CONSTRAINT chk_single_machine_runtime CHECK (id = 1),

    CONSTRAINT chk_machine_run_state CHECK (
        state IN (
            'RUNNING',
            'RESETTING',
            'REBOOTING',
            'SHUT_DOWN',
            'APPLICATION_STOPPED'
        )
    ),

    CONSTRAINT chk_machine_generation CHECK (generation >= 0),

    CONSTRAINT chk_machine_transition CHECK (
        (
            state IN ('RESETTING', 'REBOOTING')
            AND command_id IS NOT NULL
            AND completes_at IS NOT NULL
        )
        OR
        (
            state NOT IN ('RESETTING', 'REBOOTING')
            AND completes_at IS NULL
        )
    )
);

INSERT INTO cash_machine.machine_runtime (
    id, state, generation
)
VALUES (1, 'RUNNING', 0);

CREATE TABLE cash_machine.component_runtime (
    component_id VARCHAR(30) PRIMARY KEY,
    reset_count BIGINT NOT NULL DEFAULT 0,
    last_reset_at TIMESTAMP WITH TIME ZONE,

    CONSTRAINT chk_component_runtime_id CHECK (
        component_id IN (
            'ACCEPTOR',
            'CASHBOX',
            'DOOR',
            'LOCK',
            'EXTENSION_BOARD',
            'PRINTER'
        )
    ),

    CONSTRAINT chk_component_reset_count CHECK (reset_count >= 0)
);

INSERT INTO cash_machine.component_runtime(component_id)
VALUES
    ('ACCEPTOR'),
    ('CASHBOX'),
    ('DOOR'),
    ('LOCK'),
    ('EXTENSION_BOARD'),
    ('PRINTER');