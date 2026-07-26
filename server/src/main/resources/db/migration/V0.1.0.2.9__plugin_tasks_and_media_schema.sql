-- 插件任务架构与媒体模型对齐。
-- 插件按 (namespace, task_type) 声明任务，async_task 通过 parent_task_id 构成任务树，
-- 规划与执行同构，调度只按 (namespace, task_type) 索引。
-- 破坏性升级：删除全部历史任务与已安装插件，不做数据转换。

-- ---------------------------------------------------------------------------
-- 媒体模型：与当前实体定义对齐
-- ---------------------------------------------------------------------------

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

-- MediaFile 不再存储内容哈希
ALTER TABLE public.media_file
    DROP COLUMN IF EXISTS sha256;

-- ---------------------------------------------------------------------------
-- 插件与任务
-- ---------------------------------------------------------------------------

DROP TABLE public.async_task_log;
DROP TABLE public.plugin;

CREATE TABLE public.plugin
(
    id                TEXT        PRIMARY KEY,
    name              TEXT,
    version           TEXT        NOT NULL,
    abi               TEXT        NOT NULL,
    config_definition JSONB       NOT NULL,
    wasm              BYTEA       NOT NULL,
    enabled           BOOLEAN     NOT NULL DEFAULT FALSE,
    created_at        TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at        TIMESTAMPTZ NOT NULL DEFAULT NOW(),

    CONSTRAINT ck_plugin_id_reserved
        CHECK (id NOT LIKE 'app.unirhy%'),
    CONSTRAINT ck_plugin_config_definition
        CHECK (
            jsonb_typeof(config_definition) = 'object'
            AND config_definition ? 'schema'
            AND jsonb_typeof(config_definition -> 'schema') = 'object'
            AND config_definition ? 'order'
            AND jsonb_typeof(config_definition -> 'order') = 'array'
        ),
    CONSTRAINT ck_plugin_updated_at
        CHECK (updated_at >= created_at)
);

-- 任务身份、并发与表单定义都是"每任务一份"的属性
CREATE TABLE public.plugin_task
(
    plugin_id        TEXT    NOT NULL REFERENCES public.plugin (id) ON DELETE CASCADE,
    task_type        TEXT    NOT NULL,
    concurrency      INTEGER NOT NULL,
    user_submittable BOOLEAN NOT NULL,
    form_definition  JSONB   NOT NULL,

    PRIMARY KEY (plugin_id, task_type),

    CONSTRAINT ck_plugin_task_concurrency_positive
        CHECK (concurrency > 0),
    CONSTRAINT ck_plugin_task_form_definition
        CHECK (
            jsonb_typeof(form_definition) = 'object'
            AND form_definition ? 'schema'
            AND jsonb_typeof(form_definition -> 'schema') = 'object'
            AND form_definition ? 'order'
            AND jsonb_typeof(form_definition -> 'order') = 'array'
        )
);

CREATE TABLE public.plugin_data
(
    plugin_id       TEXT  NOT NULL REFERENCES public.plugin (id) ON DELETE CASCADE,
    key             TEXT  NOT NULL,
    value           JSONB,
    encrypted_value BYTEA,

    PRIMARY KEY (plugin_id, key),

    CONSTRAINT ck_plugin_data_value
        CHECK (num_nonnulls(value, encrypted_value) = 1)
);

CREATE TABLE public.async_task
(
    id               BIGINT GENERATED BY DEFAULT AS IDENTITY PRIMARY KEY,
    parent_task_id   BIGINT REFERENCES public.async_task (id) ON DELETE RESTRICT,
    namespace        TEXT        NOT NULL,
    task_type        TEXT        NOT NULL,
    payload          JSONB       NOT NULL,
    status           TEXT        NOT NULL DEFAULT 'PENDING',
    created_at       TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    started_at       TIMESTAMPTZ,
    completed_at     TIMESTAMPTZ,
    completed_reason TEXT,

    CONSTRAINT ck_async_task_payload_object
        CHECK (jsonb_typeof(payload) = 'object'),
    CONSTRAINT ck_async_task_status
        CHECK (status IN ('PENDING', 'RUNNING', 'COMPLETED', 'FAILED', 'CANCELLED'))
);

CREATE INDEX idx_async_task_discovery
    ON public.async_task (status, namespace, task_type);

CREATE INDEX idx_async_task_claim
    ON public.async_task (namespace, task_type, status, created_at, id);

CREATE INDEX idx_async_task_parent
    ON public.async_task (parent_task_id, created_at, id);

-- 活动任务去重：唯一索引默认 NULLS DISTINCT，
-- 入口任务（parent_task_id IS NULL）天然不参与去重
CREATE UNIQUE INDEX uq_async_task_active_sibling
    ON public.async_task (
        parent_task_id,
        namespace,
        task_type,
        sha256(jsonb_send(payload))
    )
    WHERE status IN ('PENDING', 'RUNNING');
