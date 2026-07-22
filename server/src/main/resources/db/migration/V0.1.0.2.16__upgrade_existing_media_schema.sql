-- Bring databases created from older mutable baselines in line with the current model.

ALTER TABLE public.account
    ADD COLUMN IF NOT EXISTS preferences JSONB;

UPDATE public.account
SET preferences = '{"preferredAssetFormat":"audio/opus"}'::jsonb
WHERE preferences IS NULL;

ALTER TABLE public.account
    ALTER COLUMN preferences SET DEFAULT '{"preferredAssetFormat":"audio/opus"}'::jsonb,
    ALTER COLUMN preferences SET NOT NULL;

DO
$$
BEGIN
    IF NOT EXISTS (
        SELECT 1
        FROM information_schema.columns
        WHERE table_schema = 'public'
          AND table_name = 'album_recording_mapping'
          AND column_name = 'id'
    ) THEN
        ALTER TABLE public.album_recording_mapping
            DROP CONSTRAINT album_recording_mapping_pkey,
            ADD COLUMN id BIGSERIAL;
        ALTER TABLE public.album_recording_mapping
            ADD PRIMARY KEY (id);
    END IF;
END
$$;

ALTER TABLE public.album_recording_mapping
    ADD COLUMN IF NOT EXISTS sort_order INT NOT NULL DEFAULT 0;

DO
$$
BEGIN
    IF NOT EXISTS (
        SELECT 1
        FROM pg_constraint
        WHERE conrelid = 'public.album_recording_mapping'::regclass
          AND conname = 'album_recording_mapping_uniq'
    ) THEN
        ALTER TABLE public.album_recording_mapping
            ADD CONSTRAINT album_recording_mapping_uniq UNIQUE (album_id, recording_id);
    END IF;
END
$$;

DO
$$
BEGIN
    IF NOT EXISTS (
        SELECT 1
        FROM information_schema.columns
        WHERE table_schema = 'public'
          AND table_name = 'playlist_recording_mapping'
          AND column_name = 'id'
    ) THEN
        ALTER TABLE public.playlist_recording_mapping
            DROP CONSTRAINT playlist_recording_mapping_pkey,
            ADD COLUMN id BIGSERIAL;
        ALTER TABLE public.playlist_recording_mapping
            ADD PRIMARY KEY (id);
    END IF;
END
$$;

ALTER TABLE public.playlist_recording_mapping
    ADD COLUMN IF NOT EXISTS sort_order INT NOT NULL DEFAULT 0;

DO
$$
BEGIN
    IF NOT EXISTS (
        SELECT 1
        FROM pg_constraint
        WHERE conrelid = 'public.playlist_recording_mapping'::regclass
          AND conname = 'playlist_recording_mapping_uniq'
    ) THEN
        ALTER TABLE public.playlist_recording_mapping
            ADD CONSTRAINT playlist_recording_mapping_uniq UNIQUE (playlist_id, recording_id);
    END IF;
END
$$;

DO
$$
BEGIN
    IF EXISTS (
        SELECT 1
        FROM information_schema.columns
        WHERE table_schema = 'public'
          AND table_name = 'recording'
          AND column_name = 'label'
          AND data_type = 'text'
    ) THEN
        ALTER TABLE public.recording
            ALTER COLUMN label TYPE TEXT[]
                USING CASE
                    WHEN label IS NULL OR btrim(label) = '' THEN '{}'::TEXT[]
                    ELSE ARRAY[label]
                END;
    END IF;
END
$$;

ALTER TABLE public.recording
    ALTER COLUMN label SET DEFAULT '{}',
    ALTER COLUMN label SET NOT NULL;

ALTER TABLE public.recording
    DROP COLUMN IF EXISTS kind;

ALTER TABLE public.album
    DROP COLUMN IF EXISTS kind;

CREATE INDEX IF NOT EXISTS recording_label_gin_idx
    ON public.recording USING GIN (label);
