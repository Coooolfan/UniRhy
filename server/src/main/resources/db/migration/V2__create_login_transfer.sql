CREATE TABLE login_transfer
(
    id               UUID        PRIMARY KEY,
    account_id       BIGINT      NOT NULL REFERENCES account (id) ON DELETE CASCADE,
    qr_secret_hash   BYTEA       NOT NULL,
    claim_token_hash BYTEA,
    device_name      TEXT,
    platform         VARCHAR,
    client_version   TEXT,
    status           VARCHAR     NOT NULL DEFAULT 'WAITING',
    created_at       TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    expires_at       TIMESTAMPTZ NOT NULL,
    claimed_at       TIMESTAMPTZ,
    authorized_at    TIMESTAMPTZ,
    closed_at        TIMESTAMPTZ,

    CHECK (
        status IN (
            'WAITING',
            'CLAIMED',
            'AUTHORIZED',
            'COMPLETED',
            'REJECTED',
            'CANCELLED',
            'EXPIRED'
        )
    ),
    CHECK (expires_at > created_at)
);

CREATE UNIQUE INDEX login_transfer_account_active_uniq
    ON login_transfer (account_id)
    WHERE status IN ('WAITING', 'CLAIMED', 'AUTHORIZED');

CREATE INDEX login_transfer_expiry_idx
    ON login_transfer (expires_at)
    WHERE status IN ('WAITING', 'CLAIMED', 'AUTHORIZED');

CREATE INDEX login_transfer_terminal_closed_idx
    ON login_transfer (closed_at)
    WHERE status NOT IN ('WAITING', 'CLAIMED', 'AUTHORIZED');
