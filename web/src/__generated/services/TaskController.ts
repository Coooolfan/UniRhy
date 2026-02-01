import type {Executor} from '../';
import type {ScanTaskRequest} from '../model/static/';

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
     * 此接口用于提交媒体扫描任务
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
}

export type TaskControllerOptions = {
    'executeScanTask': {
        /**
         * 扫描任务请求参数
         * 
         */
        readonly body: ScanTaskRequest
    }
}
