-- ==========================================================
-- 1. 用户与权限模块
-- ==========================================================

-- 用户账户表：存储基本登录信息与权限
CREATE TABLE public.account
(
    id       BIGSERIAL PRIMARY KEY,
    name     TEXT    NOT NULL,               -- 用户名（唯一）
    password TEXT    NOT NULL,               -- 密码（应存储加密后的 hash）
    email    TEXT    NOT NULL,               -- 电子邮箱（唯一）
    admin    BOOLEAN NOT NULL DEFAULT FALSE, -- 是否为管理员
    CONSTRAINT account_unique_name UNIQUE (name),
    CONSTRAINT account_unique_email UNIQUE (email)
);

-- ==========================================================
-- 2. 核心作品与录音模块
-- ==========================================================

-- 作品表：指代抽象的艺术创作（如：贝多芬第五交响曲这个“作品”本身）
CREATE TABLE public.work
(
    id    BIGSERIAL PRIMARY KEY,
    title TEXT NOT NULL -- 作品标题
);

-- 录音表：指代作品的具体音频实现（如：某乐团在某年录制的版本）
CREATE TABLE public.recording
(
    id      BIGSERIAL PRIMARY KEY,
    work_id BIGINT NOT NULL REFERENCES work (id) ON DELETE RESTRICT, -- 关联的作品
    kind    TEXT   NOT NULL,                                         -- 录音类型（如：录音室版、现场版）
    label   TEXT,                                                    -- 唱片公司/厂牌
    title   TEXT,                                                    -- 录音特定标题（可选）
    comment TEXT   NOT NULL DEFAULT ''                               -- 备注
);

-- ==========================================================
-- 3. 存储提供商模块 (多后端支持)
-- ==========================================================

-- 对象存储提供商 (如 阿里云 OSS, AWS S3)
CREATE TABLE public.file_provider_oss
(
    id          BIGSERIAL PRIMARY KEY,
    name        TEXT NOT NULL, -- 配置名称
    host        TEXT NOT NULL, -- 访问域名
    bucket      TEXT NOT NULL, -- 桶名称
    access_key  TEXT NOT NULL, -- 访问密钥 ID
    secret_key  TEXT NOT NULL, -- 访问密钥 Secret
    parent_path TEXT           -- 根路径前缀
);

-- 本地文件系统存储提供商
CREATE TABLE public.file_provider_file_system
(
    id          BIGSERIAL PRIMARY KEY,
    name        TEXT NOT NULL, -- 配置名称
    parent_path TEXT NOT NULL  -- 物理路径
);

-- ==========================================================
-- 4. 资源实体模块
-- ==========================================================

-- 资源表：将录音与物理文件关联
CREATE TABLE public.asset
(
    id              BIGSERIAL PRIMARY KEY,
    recording_id    BIGINT NOT NULL REFERENCES public.recording (id) ON DELETE RESTRICT,        -- 关联的录音
    sha256          TEXT   NOT NULL,                                                            -- 文件哈希（校验/去重）
    object_key      TEXT   NOT NULL,                                                            -- 文件在存储空间中的路径/Key

    oss_provider_id BIGINT REFERENCES public.file_provider_oss (id) ON DELETE RESTRICT,         -- 关联 OSS 节点
    fs_provider_id  BIGINT REFERENCES public.file_provider_file_system (id) ON DELETE RESTRICT, -- 关联文件系统节点

    -- 互斥约束：确保一个资源要么存在 OSS，要么存在本地文件系统，不能同时为 NULL 或同时有值
    CONSTRAINT ck_asset_provider_xor
        CHECK ( (oss_provider_id IS NOT NULL) <> (fs_provider_id IS NOT NULL) )
);

-- 唯一性索引：防止在同一个存储空间内出现重复的 Key
CREATE UNIQUE INDEX asset_oss_key_uniq
    ON public.asset (oss_provider_id, object_key) WHERE oss_provider_id IS NOT NULL;

CREATE UNIQUE INDEX asset_fs_key_uniq
    ON public.asset (fs_provider_id, object_key) WHERE fs_provider_id IS NOT NULL;

-- ==========================================================
-- 5. 专辑 (Album) 模块
-- ==========================================================

-- 专辑表
CREATE TABLE public.album
(
    id           BIGSERIAL PRIMARY KEY,
    title        TEXT NOT NULL,                 -- 专辑名称
    kind         TEXT NOT NULL DEFAULT 'album', -- 类型：album(专辑)/single(单曲)/ep/compilation(精选)
    release_date DATE,                          -- 发行日期
    comment      TEXT NOT NULL DEFAULT ''       -- 专辑简介
);

-- 专辑与录音的关联表（多对多）
CREATE TABLE public.album_recording_mapping
(
    album_id     BIGINT NOT NULL REFERENCES public.album (id) ON DELETE CASCADE,
    recording_id BIGINT NOT NULL REFERENCES public.recording (id) ON DELETE RESTRICT,
    PRIMARY KEY (album_id, recording_id) -- 联合主键防止重复添加
);

-- ==========================================================
-- 6. 歌单 (Playlist) 模块
-- ==========================================================

-- 歌单表
CREATE TABLE public.playlist
(
    id       BIGSERIAL PRIMARY KEY,
    owner_id BIGINT  NOT NULL REFERENCES public.account (id) ON DELETE RESTRICT, -- 创建者
    name     TEXT    NOT NULL,                                                   -- 歌单名称
    comment  TEXT    NOT NULL DEFAULT ''                                         -- 歌单描述
);

-- 歌单与录音的关联表（多对多）
CREATE TABLE public.playlist_recording_mapping
(
    playlist_id  BIGINT NOT NULL REFERENCES public.playlist (id) ON DELETE CASCADE,
    recording_id BIGINT NOT NULL REFERENCES public.recording (id) ON DELETE RESTRICT,
    PRIMARY KEY (playlist_id, recording_id) -- 联合主键防止重复添加
);