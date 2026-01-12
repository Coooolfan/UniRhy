CREATE TABLE public.account
(
    id       BIGSERIAL PRIMARY KEY,
    name     TEXT    NOT NULL,
    password TEXT    NOT NULL,
    email    TEXT    NOT NULL,
    admin    BOOLEAN NOT NULL DEFAULT FALSE,
    CONSTRAINT account_unique_name UNIQUE (name),
    CONSTRAINT account_unique_email UNIQUE (email)
);

CREATE TABLE public.work
(
    id    BIGSERIAL PRIMARY KEY,
    title TEXT NOT NULL
);

CREATE TABLE public.recording
(
    id      BIGSERIAL PRIMARY KEY,
    work_id BIGINT NOT NULL REFERENCES work (id) ON DELETE RESTRICT,
    kind    TEXT   NOT NULL,
    label   TEXT,
    title   TEXT,
    comment TEXT   NOT NULL DEFAULT ''
);

CREATE TABLE public.file_provider_oss
(
    id          BIGSERIAL PRIMARY KEY,
    host        TEXT NOT NULL,
    bucket      TEXT NOT NULL,
    access_key  TEXT NOT NULL,
    secret_key  TEXT NOT NULL,
    parent_path TEXT
);

CREATE TABLE public.file_provider_file_system
(
    id          BIGSERIAL PRIMARY KEY,
    parent_path TEXT    NOT NULL,
    mounted     BOOLEAN NOT NULL DEFAULT TRUE
);

CREATE TABLE public.asset
(
    id              BIGSERIAL PRIMARY KEY,
    recording_id    BIGINT NOT NULL REFERENCES public.recording (id) ON DELETE RESTRICT,
    sha256          TEXT   NOT NULL,
    object_key      TEXT   NOT NULL,

    oss_provider_id BIGINT REFERENCES public.file_provider_oss (id) ON DELETE RESTRICT,
    fs_provider_id  BIGINT REFERENCES public.file_provider_file_system (id) ON DELETE RESTRICT,

    CONSTRAINT ck_asset_provider_xor
        CHECK ( (oss_provider_id IS NOT NULL) <> (fs_provider_id IS NOT NULL) )
);

CREATE UNIQUE INDEX asset_oss_key_uniq
    ON public.asset (oss_provider_id, object_key) WHERE oss_provider_id IS NOT NULL;

CREATE UNIQUE INDEX asset_fs_key_uniq
    ON public.asset (fs_provider_id, object_key) WHERE fs_provider_id IS NOT NULL;
