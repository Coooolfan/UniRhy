-- ==========================================================
-- 为 album_recording_mapping / playlist_recording_mapping
-- 引入代理主键 id 与排序列 sort_order
-- 配合 Jimmer ManyToManyView 模式
-- ==========================================================

-- album_recording_mapping
ALTER TABLE public.album_recording_mapping
    DROP CONSTRAINT album_recording_mapping_pkey;

ALTER TABLE public.album_recording_mapping
    ADD COLUMN id BIGSERIAL PRIMARY KEY;

ALTER TABLE public.album_recording_mapping
    ADD CONSTRAINT album_recording_mapping_uniq UNIQUE (album_id, recording_id);

ALTER TABLE public.album_recording_mapping
    ADD COLUMN sort_order INT NOT NULL DEFAULT 0;

UPDATE public.album_recording_mapping AS m
SET sort_order = sub.rn
FROM (
    SELECT id,
           (ROW_NUMBER() OVER (PARTITION BY album_id ORDER BY id) - 1)::INT AS rn
    FROM public.album_recording_mapping
) AS sub
WHERE m.id = sub.id;

-- playlist_recording_mapping
ALTER TABLE public.playlist_recording_mapping
    DROP CONSTRAINT playlist_recording_mapping_pkey;

ALTER TABLE public.playlist_recording_mapping
    ADD COLUMN id BIGSERIAL PRIMARY KEY;

ALTER TABLE public.playlist_recording_mapping
    ADD CONSTRAINT playlist_recording_mapping_uniq UNIQUE (playlist_id, recording_id);

ALTER TABLE public.playlist_recording_mapping
    ADD COLUMN sort_order INT NOT NULL DEFAULT 0;

UPDATE public.playlist_recording_mapping AS m
SET sort_order = sub.rn
FROM (
    SELECT id,
           (ROW_NUMBER() OVER (PARTITION BY playlist_id ORDER BY id) - 1)::INT AS rn
    FROM public.playlist_recording_mapping
) AS sub
WHERE m.id = sub.id;
