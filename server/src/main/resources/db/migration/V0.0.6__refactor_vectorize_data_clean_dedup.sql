-- 清空旧任务数据，避免 payload 格式不兼容
TRUNCATE TABLE async_task_log;

DROP INDEX IF EXISTS uq_async_task_log_vectorize_active;
CREATE UNIQUE INDEX uq_async_task_log_vectorize_active
    ON public.async_task_log (((params::jsonb ->> 'recordingId')::bigint))
    WHERE task_type = 'VECTORIZE' AND status IN ('PENDING', 'RUNNING');

DROP INDEX IF EXISTS uq_async_task_log_data_clean_active;
CREATE UNIQUE INDEX uq_async_task_log_data_clean_active
    ON public.async_task_log (((params::jsonb ->> 'recordingId')::bigint))
    WHERE task_type = 'DATA_CLEAN' AND status IN ('PENDING', 'RUNNING');
