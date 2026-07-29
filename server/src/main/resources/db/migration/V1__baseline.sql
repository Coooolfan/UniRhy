CREATE TABLE file_provider_oss
(
    id          BIGSERIAL PRIMARY KEY,
    name        TEXT    NOT NULL,
    host        TEXT    NOT NULL,
    bucket      TEXT    NOT NULL,
    access_key  TEXT    NOT NULL,
    secret_key  TEXT    NOT NULL,
    parent_path TEXT,
    readonly    BOOLEAN NOT NULL DEFAULT FALSE
);

CREATE TABLE file_provider_file_system
(
    id          BIGSERIAL PRIMARY KEY,
    name        TEXT    NOT NULL,
    parent_path TEXT    NOT NULL,
    readonly    BOOLEAN NOT NULL DEFAULT FALSE
);

CREATE TABLE media_file
(
    id              BIGSERIAL PRIMARY KEY,
    object_key      TEXT   NOT NULL,
    mime_type       TEXT   NOT NULL,
    size            BIGINT NOT NULL,
    width           INTEGER,
    height          INTEGER,

    oss_provider_id BIGINT REFERENCES file_provider_oss (id) ON DELETE RESTRICT,
    fs_provider_id  BIGINT REFERENCES file_provider_file_system (id) ON DELETE RESTRICT,

    CONSTRAINT ck_media_file_provider_xor
        CHECK ( (oss_provider_id IS NOT NULL) <> (fs_provider_id IS NOT NULL) )
);

CREATE UNIQUE INDEX media_file_oss_key_uniq
    ON media_file (oss_provider_id, object_key) WHERE oss_provider_id IS NOT NULL;

CREATE UNIQUE INDEX media_file_fs_key_uniq
    ON media_file (fs_provider_id, object_key) WHERE fs_provider_id IS NOT NULL;

CREATE TABLE work
(
    id    BIGSERIAL PRIMARY KEY,
    title TEXT NOT NULL
);

CREATE UNIQUE INDEX work_title_uniq ON work (title);

CREATE TABLE recording
(
    id              BIGSERIAL PRIMARY KEY,
    work_id         BIGINT  NOT NULL REFERENCES work (id) ON DELETE RESTRICT,
    label           TEXT[]  NOT NULL DEFAULT '{}',
    title           TEXT,
    comment         TEXT    NOT NULL DEFAULT '',
    cover_id        BIGINT  REFERENCES media_file (id) ON DELETE SET NULL,
    duration_ms     BIGINT  NOT NULL,
    default_in_work BOOLEAN NOT NULL DEFAULT FALSE
);

CREATE UNIQUE INDEX recording_default_in_work_uniq
    ON recording (work_id) WHERE default_in_work = TRUE;

CREATE INDEX recording_label_gin_idx
    ON recording USING GIN (label);

CREATE TABLE artist
(
    id           BIGSERIAL PRIMARY KEY,
    display_name TEXT   NOT NULL,
    alias        TEXT[] NOT NULL,
    comment      TEXT   NOT NULL DEFAULT '',
    avatar_id    BIGINT REFERENCES media_file (id) ON DELETE SET NULL
);

CREATE TABLE work_artist_mapping
(
    work_id   BIGINT NOT NULL REFERENCES work (id) ON DELETE RESTRICT,
    artist_id BIGINT NOT NULL REFERENCES artist (id) ON DELETE RESTRICT,
    PRIMARY KEY (work_id, artist_id)
);

CREATE TABLE recording_artist_mapping
(
    recording_id BIGINT NOT NULL REFERENCES recording (id) ON DELETE RESTRICT,
    artist_id    BIGINT NOT NULL REFERENCES artist (id) ON DELETE RESTRICT,
    PRIMARY KEY (recording_id, artist_id)
);

CREATE TABLE account
(
    id          BIGSERIAL PRIMARY KEY,
    name        TEXT    NOT NULL,
    password    TEXT    NOT NULL,
    email       TEXT    NOT NULL,
    admin       BOOLEAN NOT NULL DEFAULT FALSE,
    avatar_id   BIGINT  REFERENCES media_file (id) ON DELETE SET NULL,
    preferences JSONB   NOT NULL DEFAULT '{
      "preferredAssetFormat": "audio/opus"
    }'::jsonb,
    CONSTRAINT account_unique_name UNIQUE (name),
    CONSTRAINT account_unique_email UNIQUE (email)
);

CREATE UNIQUE INDEX account_unique_admin_true
    ON account (admin)
    WHERE admin = TRUE;

CREATE TABLE asset
(
    id            BIGSERIAL PRIMARY KEY,
    recording_id  BIGINT NOT NULL REFERENCES recording (id) ON DELETE RESTRICT,
    media_file_id BIGINT NOT NULL REFERENCES media_file (id) ON DELETE RESTRICT,
    comment       TEXT   NOT NULL DEFAULT ''
);

CREATE TABLE album
(
    id           BIGSERIAL PRIMARY KEY,
    title        TEXT   NOT NULL,
    release_date DATE,
    comment      TEXT   NOT NULL DEFAULT '',
    cover_id     BIGINT REFERENCES media_file (id) ON DELETE SET NULL
);

CREATE TABLE album_recording_mapping
(
    id           BIGSERIAL PRIMARY KEY,
    album_id     BIGINT NOT NULL REFERENCES album (id) ON DELETE CASCADE,
    recording_id BIGINT NOT NULL REFERENCES recording (id) ON DELETE RESTRICT,
    sort_order   INT    NOT NULL DEFAULT 0,
    CONSTRAINT album_recording_mapping_uniq UNIQUE (album_id, recording_id)
);

CREATE TABLE playlist
(
    id       BIGSERIAL PRIMARY KEY,
    owner_id BIGINT NOT NULL REFERENCES account (id) ON DELETE RESTRICT,
    name     TEXT   NOT NULL,
    comment  TEXT   NOT NULL DEFAULT ''
);

CREATE INDEX playlist_owner_id_idx ON playlist (owner_id);

CREATE TABLE playlist_recording_mapping
(
    id           BIGSERIAL PRIMARY KEY,
    playlist_id  BIGINT NOT NULL REFERENCES playlist (id) ON DELETE CASCADE,
    recording_id BIGINT NOT NULL REFERENCES recording (id) ON DELETE RESTRICT,
    sort_order   INT    NOT NULL DEFAULT 0,
    CONSTRAINT playlist_recording_mapping_uniq UNIQUE (playlist_id, recording_id)
);

CREATE TABLE system_config
(
    id              BIGINT PRIMARY KEY DEFAULT 0,
    oss_provider_id BIGINT REFERENCES file_provider_oss (id) ON DELETE RESTRICT,
    fs_provider_id  BIGINT REFERENCES file_provider_file_system (id) ON DELETE RESTRICT,

    CONSTRAINT ck_system_config_singleton CHECK (id = 0),
    CONSTRAINT ck_system_config_provider_xor
        CHECK ( (oss_provider_id IS NOT NULL) <> (fs_provider_id IS NOT NULL) )
);

CREATE TABLE play_queue
(
    account_id                BIGINT PRIMARY KEY REFERENCES account (id) ON DELETE CASCADE,
    recording_ids             BIGINT[]    NOT NULL DEFAULT '{}',
    current_index             INTEGER     NOT NULL DEFAULT 0,
    shuffle_indices           INTEGER[]   NOT NULL DEFAULT '{}',
    playback_strategy         VARCHAR     NOT NULL DEFAULT 'SEQUENTIAL',
    stop_strategy             VARCHAR     NOT NULL DEFAULT 'LIST',
    playback_status           VARCHAR     NOT NULL DEFAULT 'PAUSED',
    position_ms               BIGINT      NOT NULL DEFAULT 0,
    server_time_to_execute_ms BIGINT      NOT NULL DEFAULT 0,
    updated_at                TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    version                   BIGINT      NOT NULL DEFAULT 0,
    CONSTRAINT play_queue_current_index_check CHECK (
        current_index >= 0 AND
        (
            (cardinality(recording_ids) = 0 AND current_index = 0) OR
            (cardinality(recording_ids) > 0 AND current_index < cardinality(recording_ids))
            )
        ),
    CONSTRAINT play_queue_shuffle_indices_check CHECK (
        playback_strategy = 'SHUFFLE' OR cardinality(shuffle_indices) = 0
        ),
    CONSTRAINT play_queue_position_ms_check CHECK (position_ms >= 0),
    CONSTRAINT play_queue_empty_state_check CHECK (
        cardinality(recording_ids) > 0 OR
        (
            current_index = 0 AND
            playback_status = 'PAUSED' AND
            position_ms = 0 AND
            server_time_to_execute_ms = 0
            )
        ),
    CONSTRAINT play_queue_strategy_check CHECK (
        playback_strategy IN ('SEQUENTIAL', 'SHUFFLE', 'SINGLE', 'RADIO')
        ),
    CONSTRAINT play_queue_stop_strategy_check CHECK (
        stop_strategy IN ('TRACK', 'LIST', 'NEVER')
        ),
    CONSTRAINT play_queue_status_check CHECK (
        playback_status IN ('PLAYING', 'PAUSED')
        )
);

CREATE TABLE plugin
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

CREATE TABLE plugin_task
(
    id               BIGSERIAL PRIMARY KEY,
    plugin_id        TEXT    NOT NULL REFERENCES plugin (id) ON DELETE CASCADE,
    task_type        TEXT    NOT NULL,
    name             TEXT,
    concurrency      INTEGER NOT NULL,
    user_submittable BOOLEAN NOT NULL,
    form_definition  JSONB   NOT NULL,

    CONSTRAINT plugin_task_uniq
        UNIQUE (plugin_id, task_type),
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

CREATE TABLE plugin_data
(
    id              BIGSERIAL PRIMARY KEY,
    plugin_id       TEXT NOT NULL REFERENCES plugin (id) ON DELETE CASCADE,
    key             TEXT NOT NULL,
    value           JSONB,
    encrypted_value BYTEA,

    CONSTRAINT plugin_data_uniq
        UNIQUE (plugin_id, key),
    CONSTRAINT ck_plugin_data_value
        CHECK (num_nonnulls(value, encrypted_value) = 1)
);

CREATE TABLE async_task
(
    id               BIGINT GENERATED BY DEFAULT AS IDENTITY PRIMARY KEY,
    parent_task_id   BIGINT REFERENCES async_task (id) ON DELETE RESTRICT,
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
    ON async_task (status, namespace, task_type);

CREATE INDEX idx_async_task_claim
    ON async_task (namespace, task_type, status, created_at, id);

CREATE INDEX idx_async_task_parent
    ON async_task (parent_task_id, created_at, id);

-- 入口任务的 parent_task_id 为 NULL；PostgreSQL 唯一索引默认允许多个 NULL，
-- 因而只对同一父任务下、载荷完全相同的活动子任务去重。
CREATE UNIQUE INDEX uq_async_task_active_sibling
    ON async_task (
        parent_task_id,
        namespace,
        task_type,
        sha256(jsonb_send(payload))
    )
    WHERE status IN ('PENDING', 'RUNNING');
