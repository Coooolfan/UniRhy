CREATE TABLE public.account_current_queue (
    account_id BIGINT PRIMARY KEY
        REFERENCES public.account(id)
        ON DELETE CASCADE,
    state JSONB NOT NULL,
    version BIGINT NOT NULL,
    updated_at_ms BIGINT NOT NULL
);
