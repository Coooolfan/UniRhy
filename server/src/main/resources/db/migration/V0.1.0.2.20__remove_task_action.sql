-- 根任务由 Planner 处理，非根任务由 Handler 处理，执行角色直接由 parent_task_id 决定。
DROP INDEX public.uq_async_task_active_child;

ALTER TABLE public.async_task
    DROP CONSTRAINT ck_async_task_action,
    DROP COLUMN action;

CREATE UNIQUE INDEX uq_async_task_active_child
    ON public.async_task (
        parent_task_id,
        namespace,
        task_type,
        sha256(jsonb_send(payload))
    )
    WHERE parent_task_id IS NOT NULL AND status IN ('PENDING', 'RUNNING');
