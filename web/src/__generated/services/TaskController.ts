import type {Executor} from '../';
import type {
    AsyncTaskLogCountRow, 
    DataCleanTaskRequest, 
    ScanTaskRequest, 
    TranscodeTaskRequest, 
    VectorizeTaskRequest
} from '../model/static/';

/**
 * 任务管理接口
 * 
 * 提供任务触发能力（例如扫描任务）
 */
export class TaskController {
    
    constructor(private executor: Executor) {}
    
    readonly executeDataCleanTask: (options: TaskControllerOptions['executeDataCleanTask']) => Promise<
        void
    > = async(options) => {
        let _uri = '/api/task/data-clean';
        return (await this.executor({uri: _uri, method: 'POST', body: options.body})) as Promise<void>;
    }
    
    /**
     * 触发扫描任务
     * 
     * 此接口用于提交媒体扫描任务，对同一存储节点的重复请求会增量补充缺失文件任务
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
     * 触发转码任务
     * 
     * 此接口用于提交媒体转码任务，非幂等操作，但活跃中的相同转码任务会自动去重
     * 需要用户登录认证才能访问
     * 
     * @parameter {TaskControllerOptions['executeTranscodeTask']} options
     * - request 转码任务请求参数
     * 
     */
    readonly executeTranscodeTask: (options: TaskControllerOptions['executeTranscodeTask']) => Promise<
        void
    > = async(options) => {
        let _uri = '/api/task/transcode';
        return (await this.executor({uri: _uri, method: 'POST', body: options.body})) as Promise<void>;
    }
    
    /**
     * 查询任务状态统计
     * 
     */
    readonly executeVectorizeTask: (options: TaskControllerOptions['executeVectorizeTask']) => Promise<
        void
    > = async(options) => {
        let _uri = '/api/task/vectorize';
        return (await this.executor({uri: _uri, method: 'POST', body: options.body})) as Promise<void>;
    }
    
    readonly listTaskLogs: () => Promise<
        ReadonlyArray<AsyncTaskLogCountRow>
    > = async() => {
        let _uri = '/api/task/logs';
        return (await this.executor({uri: _uri, method: 'GET'})) as Promise<ReadonlyArray<AsyncTaskLogCountRow>>;
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
    'executeTranscodeTask': {
        /**
         * 转码任务请求参数
         * 
         */
        readonly body: TranscodeTaskRequest
    }, 
    'executeVectorizeTask': {
        readonly body: VectorizeTaskRequest
    }, 
    'executeDataCleanTask': {
        readonly body: DataCleanTaskRequest
    }, 
    'listTaskLogs': {}
}
