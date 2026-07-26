-- 为 plugin_task 与 plugin_data 引入业务无关代理主键 id，
-- 原复合主键降级为唯一约束，使两张表可由 Jimmer 实体管理。
-- 外键 ON DELETE CASCADE 与既有 CHECK 约束均保持不变。

ALTER TABLE public.plugin_task
    DROP CONSTRAINT plugin_task_pkey,
    ADD COLUMN id BIGSERIAL;

ALTER TABLE public.plugin_task
    ADD PRIMARY KEY (id);

ALTER TABLE public.plugin_task
    ADD CONSTRAINT plugin_task_uniq UNIQUE (plugin_id, task_type);

ALTER TABLE public.plugin_data
    DROP CONSTRAINT plugin_data_pkey,
    ADD COLUMN id BIGSERIAL;

ALTER TABLE public.plugin_data
    ADD PRIMARY KEY (id);

ALTER TABLE public.plugin_data
    ADD CONSTRAINT plugin_data_uniq UNIQUE (plugin_id, key);
