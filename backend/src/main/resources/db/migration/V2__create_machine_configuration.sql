CREATE TABLE cash_machine.machine_configuration (
    id SMALLINT PRIMARY KEY,
    machine_code VARCHAR(50) NOT NULL UNIQUE,
    display_name VARCHAR(100) NOT NULL,
    location VARCHAR(200) NOT NULL,
    currency VARCHAR(3) NOT NULL,
    time_zone VARCHAR(50) NOT NULL,

    CONSTRAINT chk_single_machine CHECK (id = 1),
    CONSTRAINT chk_machine_currency CHECK (currency = 'EUR')
);

INSERT INTO cash_machine.machine_configuration (
    id,
    machine_code,
    display_name,
    location,
    currency,
    time_zone
) VALUES (
    1,
    'UNIT-SIM-001',
    'Unit Cash Machine',
    'Local development environment',
    'EUR',
    'Europe/Belgrade'
);