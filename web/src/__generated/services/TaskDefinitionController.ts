import type {Executor} from '../';
import type {TaskDefinitionView} from '../model/static/';

/**
 * 任务定义查询接口
 * 
 * 内建任务与已启用插件任务的统一定义视图，供前端展示任务名称、
 * 过滤可提交任务并渲染对应表单
 */
export class TaskDefinitionController {
    
    constructor(private executor: Executor) {}
    
    /**
     * 获取单项任务定义
     * 
     * @parameter {TaskDefinitionControllerOptions['getTaskDefinition']} options
     * - namespace 任务命名空间
     * - taskType 任务类型
     * 
     */
    readonly getTaskDefinition: (options: TaskDefinitionControllerOptions['getTaskDefinition']) => Promise<
        TaskDefinitionView
    > = async(options) => {
        let _uri = '/api/task-definitions/';
        _uri += encodeURIComponent(options.namespace);
        _uri += '/';
        _uri += encodeURIComponent(options.taskType);
        return (await this.executor({uri: _uri, method: 'GET'})) as Promise<TaskDefinitionView>;
    }
    
    /**
     * 获取当前完整任务定义列表
     * 
     * 返回入口任务和工作任务；调用方根据 `userSubmittable` 判断任务能否由用户直接提交
     * 
     * @return 每项包含 namespace、taskType、名称、提交能力以及 form.schema / form.order
     * 
     */
    readonly listTaskDefinitions: () => Promise<
        ReadonlyArray<TaskDefinitionView>
    > = async() => {
        let _uri = '/api/task-definitions';
        return (await this.executor({uri: _uri, method: 'GET'})) as Promise<ReadonlyArray<TaskDefinitionView>>;
    }
}

export type TaskDefinitionControllerOptions = {
    'listTaskDefinitions': {}, 
    'getTaskDefinition': {
        /**
         * 任务命名空间
         */
        readonly namespace: string, 
        /**
         * 任务类型
         * 
         */
        readonly taskType: string
    }
}
