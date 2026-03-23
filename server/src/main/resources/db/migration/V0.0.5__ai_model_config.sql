ALTER TABLE public.system_config
    ADD COLUMN completion_model JSONB;

ALTER TABLE public.system_config
    ADD COLUMN embedding_model JSONB;
