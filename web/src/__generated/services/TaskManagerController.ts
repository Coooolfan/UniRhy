import type {Executor} from '../';
import type {TaskType} from '../model/enums/';
import type {TaskInfo, TaskRequest} from '../model/static/';

export class TaskManagerController {
    
    constructor(private executor: Executor) {}
    
    /**
     * 执行指定任务
     * 
     * @parameter {TaskManagerControllerOptions['executeTask']} options
     * - request 任务请求参数（密封类多态）
     * @return TaskInfo 返回任务执行信息
     * 
     */
    readonly executeTask: (options: TaskManagerControllerOptions['executeTask']) => Promise<
        TaskInfo
    > = async(options) => {
        let _uri = '/api/task';
        return (await this.executor({uri: _uri, method: 'POST', body: options.body})) as Promise<TaskInfo>;
    }
    
    /**
     * 获取当前可用的任务类型列表
     * 
     * @return List<TaskType> 返回当前已注册的任务类型
     * 
     */
    readonly listAvailableTasks: () => Promise<
        ReadonlyArray<TaskType>
    > = async() => {
        let _uri = '/api/task';
        return (await this.executor({uri: _uri, method: 'GET'})) as Promise<ReadonlyArray<TaskType>>;
    }
}

export type TaskManagerControllerOptions = {
    'listAvailableTasks': {}, 
    'executeTask': {
        /**
         * 任务请求参数（密封类多态）
         */
        readonly body: TaskRequest
    }
}
