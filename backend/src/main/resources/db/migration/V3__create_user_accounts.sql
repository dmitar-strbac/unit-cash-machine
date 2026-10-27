CREATE TABLE cash_machine.user_account (
    id UUID PRIMARY KEY,
    display_name VARCHAR(100) NOT NULL,
    role VARCHAR(20) NOT NULL,
    pin_hash VARCHAR(100) NOT NULL,
    active BOOLEAN NOT NULL DEFAULT TRUE,

    CONSTRAINT chk_user_display_name
        CHECK (length(trim(display_name)) > 0),

    CONSTRAINT chk_user_role
        CHECK (role IN ('CASHIER', 'MANAGEMENT', 'MAINTENANCE', 'CIT')),

    CONSTRAINT chk_user_pin_hash
        CHECK (length(trim(pin_hash)) > 0)
);

CREATE INDEX idx_user_account_role_active
    ON cash_machine.user_account (role, active);