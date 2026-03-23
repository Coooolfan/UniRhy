-- 数据清洗任务去重索引：同一录音 + 同源文件 + 同源 Provider 不允许重复活跃任务
CREATE UNIQUE INDEX uq_async_task_log_data_clean_active
    ON public.async_task_log (
                              (((params::jsonb ->> 'recordingId')::bigint)),
                              ((params::jsonb ->> 'srcObjectKey')),
                              (((params::jsonb ->> 'srcProviderId')::bigint))
        )
    WHERE task_type = 'DATA_CLEAN'
        AND status IN ('PENDING', 'RUNNING')
        AND params::jsonb ? 'recordingId';
