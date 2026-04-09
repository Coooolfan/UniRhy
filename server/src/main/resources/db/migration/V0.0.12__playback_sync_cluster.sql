CREATE TABLE public.playback_connection (
    session_id VARCHAR PRIMARY KEY,
    account_id BIGINT NOT NULL
        REFERENCES public.account(id)
        ON DELETE CASCADE,
    device_id VARCHAR NULL,
    node_id VARCHAR NOT NULL,
    client_version VARCHAR NULL,
    hello_completed BOOLEAN NOT NULL DEFAULT FALSE,
    created_at_ms BIGINT NOT NULL,
    updated_at_ms BIGINT NOT NULL
);

CREATE INDEX idx_playback_connection_account_node
    ON public.playback_connection (account_id, node_id, hello_completed, updated_at_ms);

CREATE UNIQUE INDEX uq_playback_connection_device_active
    ON public.playback_connection (account_id, device_id)
    WHERE hello_completed = TRUE
      AND device_id IS NOT NULL;

CREATE TABLE public.playback_device_runtime (
    account_id BIGINT NOT NULL
        REFERENCES public.account(id)
        ON DELETE CASCADE,
    device_id VARCHAR NOT NULL,
    session_id VARCHAR NOT NULL
        REFERENCES public.playback_connection(session_id)
        ON DELETE CASCADE,
    node_id VARCHAR NOT NULL,
    rtt_ema_ms DOUBLE PRECISION NOT NULL DEFAULT 0,
    last_ntp_response_at_ms BIGINT NOT NULL DEFAULT 0,
    last_seen_at_ms BIGINT NOT NULL,
    PRIMARY KEY (account_id, device_id)
);

CREATE INDEX idx_playback_device_runtime_account
    ON public.playback_device_runtime (account_id, last_seen_at_ms);

CREATE TABLE public.playback_pending_play (
    account_id BIGINT PRIMARY KEY
        REFERENCES public.account(id)
        ON DELETE CASCADE,
    command_id VARCHAR NOT NULL,
    initiator_device_id VARCHAR NULL,
    recording_id BIGINT NOT NULL
        REFERENCES public.recording(id)
        ON DELETE CASCADE,
    position_seconds DOUBLE PRECISION NOT NULL,
    created_at_ms BIGINT NOT NULL,
    timeout_at_ms BIGINT NOT NULL
);

CREATE TABLE public.playback_pending_play_loaded_device (
    account_id BIGINT NOT NULL
        REFERENCES public.playback_pending_play(account_id)
        ON DELETE CASCADE,
    command_id VARCHAR NOT NULL,
    device_id VARCHAR NOT NULL,
    created_at_ms BIGINT NOT NULL,
    PRIMARY KEY (account_id, command_id, device_id)
);

CREATE INDEX idx_playback_pending_loaded_lookup
    ON public.playback_pending_play_loaded_device (account_id, command_id);

CREATE TABLE public.playback_sync_event (
    id BIGSERIAL PRIMARY KEY,
    account_id BIGINT NOT NULL
        REFERENCES public.account(id)
        ON DELETE CASCADE,
    event_type VARCHAR NOT NULL,
    dedupe_key VARCHAR NOT NULL,
    payload JSONB NOT NULL,
    created_at_ms BIGINT NOT NULL
);

CREATE UNIQUE INDEX uq_playback_sync_event_dedupe_key
    ON public.playback_sync_event (dedupe_key);

CREATE INDEX idx_playback_sync_event_created
    ON public.playback_sync_event (id, created_at_ms);

CREATE TABLE public.playback_sync_job (
    id BIGSERIAL PRIMARY KEY,
    job_type VARCHAR NOT NULL,
    account_id BIGINT NOT NULL
        REFERENCES public.account(id)
        ON DELETE CASCADE,
    dedupe_key VARCHAR NOT NULL,
    payload JSONB NOT NULL,
    execute_at_ms BIGINT NOT NULL,
    status VARCHAR NOT NULL,
    retry_count INTEGER NOT NULL DEFAULT 0,
    created_at_ms BIGINT NOT NULL,
    updated_at_ms BIGINT NOT NULL
);

CREATE UNIQUE INDEX uq_playback_sync_job_dedupe_key
    ON public.playback_sync_job (dedupe_key);

CREATE INDEX idx_playback_sync_job_claim
    ON public.playback_sync_job (status, execute_at_ms, id);
