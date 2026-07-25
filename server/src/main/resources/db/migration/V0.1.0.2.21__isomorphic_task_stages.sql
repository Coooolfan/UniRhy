-- 规划与执行同构：阶段编码进 TaskKey，一个插件声明多个任务。
-- 入口任务与工作任务在 async_task 中不再有结构区别，调度只按 (namespace, task_type) 索引。
-- 破坏性升级：清空历史任务与已安装插件，不做数据转换。

TRUNCATE public.async_task;
DELETE FROM public.plugin;

-- 任务身份、并发与表单定义都是"每任务一份"的属性，从 plugin 移出
ALTER TABLE public.plugin
    DROP CONSTRAINT ck_plugin_concurrency_positive,
    DROP CONSTRAINT ck_plugin_form_definition,
    DROP COLUMN task_type,
    DROP COLUMN concurrency,
    DROP COLUMN form_definition;

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

-- 活动任务去重：唯一键不再需要 WHERE parent_task_id IS NOT NULL。
-- 唯一索引默认 NULLS DISTINCT，入口任务（parent_task_id IS NULL）天然不参与去重。
DROP INDEX public.uq_async_task_active_child;

CREATE UNIQUE INDEX uq_async_task_active_sibling
    ON public.async_task (
        parent_task_id,
        namespace,
        task_type,
        sha256(jsonb_send(payload))
    )
    WHERE status IN ('PENDING', 'RUNNING');
