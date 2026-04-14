CREATE TABLE public.account_playback_resume_state (
    account_id BIGINT PRIMARY KEY REFERENCES public.account(id) ON DELETE CASCADE,
    status VARCHAR NOT NULL,
    recording_id BIGINT NULL,
    position_seconds DOUBLE PRECISION NOT NULL,
    server_time_to_execute_ms BIGINT NOT NULL,
    version BIGINT NOT NULL,
    updated_at_ms BIGINT NOT NULL
);
