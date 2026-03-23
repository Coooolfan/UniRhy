-- 启用 pgvector 扩展
CREATE EXTENSION IF NOT EXISTS vector;

-- Recording 表新增向量列（可空，向量化前为 NULL）
ALTER TABLE public.recording
    ADD COLUMN embedding vector(1024);

-- 向量化任务去重索引：同一录音 + 同源文件 + 同源 Provider 不允许重复活跃任务
CREATE UNIQUE INDEX uq_async_task_log_vectorize_active
    ON public.async_task_log (
                              (((params::jsonb ->> 'recordingId')::bigint)),
                              ((params::jsonb ->> 'srcObjectKey')),
                              (((params::jsonb ->> 'srcProviderId')::bigint))
        )
    WHERE task_type = 'VECTORIZE'
        AND status IN ('PENDING', 'RUNNING')
        AND params::jsonb ? 'recordingId';
