-- The current MediaFile model no longer stores content hashes.
ALTER TABLE public.media_file
    DROP COLUMN IF EXISTS sha256;
