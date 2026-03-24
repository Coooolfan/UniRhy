CREATE UNIQUE INDEX uq_async_task_log_playlist_generate_active
    ON public.async_task_log (
        ((params::jsonb ->> 'accountId')::bigint),
        (md5(params::jsonb ->> 'description'))
    )
    WHERE task_type = 'PLAYLIST_GENERATE'
      AND status IN ('PENDING', 'RUNNING');
