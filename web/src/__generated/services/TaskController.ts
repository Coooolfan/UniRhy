import type {Executor} from '../';
import type {RunningTaskView, ScanTaskRequest} from '../model/static/';

/**
 * 任务管理接口
 * 
 * 提供任务触发能力（例如扫描任务）
 */
export class TaskController {
    
    constructor(private executor: Executor) {}
    
    /**
     * 触发扫描任务
     * 
     * 此接口用于提交媒体扫描任务，对同一存储节点的请求是幂等的
     * 需要用户登录认证才能访问
     * 
     * @parameter {TaskControllerOptions['executeScanTask']} options
     * - request 扫描任务请求参数
     * 
     */
    readonly executeScanTask: (options: TaskControllerOptions['executeScanTask']) => Promise<
        void
    > = async(options) => {
        let _uri = '/api/task/scan';
        return (await this.executor({uri: _uri, method: 'POST', body: options.body})) as Promise<void>;
    }
    
    /**
     * 获取运行中的任务
     * 
     */
    readonly listRunningTasks: () => Promise<
        ReadonlyArray<RunningTaskView>
    > = async() => {
        let _uri = '/api/task/running';
        return (await this.executor({uri: _uri, method: 'GET'})) as Promise<ReadonlyArray<RunningTaskView>>;
    }
}

export type TaskControllerOptions = {
    'executeScanTask': {
        /**
         * 扫描任务请求参数
         * 
         */
        readonly body: ScanTaskRequest
    }, 
    'listRunningTasks': {}
}
