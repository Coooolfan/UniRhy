-- 插件任务可声明自己的显示名，用于任务列表与筛选器展示。
-- 未声明时由服务端回退到 task_type。

ALTER TABLE public.plugin_task
    ADD COLUMN name TEXT;
