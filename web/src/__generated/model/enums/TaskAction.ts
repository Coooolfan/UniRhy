export const TaskAction_CONSTANTS = [
    /**
     * 将入口表单参数展开为执行任务。
     */
    'PLAN', 
    /**
     * 执行具体任务载荷。
     */
    'RUN'
] as const;
export type TaskAction = typeof TaskAction_CONSTANTS[number];
