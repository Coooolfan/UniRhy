ALTER TABLE public.account
    ADD COLUMN preferences JSONB NOT NULL DEFAULT '{"playbackPreference":"OPUS"}'::jsonb;
