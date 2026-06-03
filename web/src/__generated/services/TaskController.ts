import type {Executor} from '../';
import type {AsyncTaskLogCountRow, ScanTaskRequest, TranscodeTaskRequest} from '../model/static/';

/**
 * 任务管理接口
 * 
 * 提供内置任务触发能力（扫描、转码）；插件任务通过 PluginController 提交
 */
export class TaskController {
    
    constructor(private executor: Executor) {}
    
    /**
     * 提交扫描任务
     * 
     * 此接口用于异步触发媒体库扫描任务
     * 需要用户登录认证才能访问
     * 
     * @parameter {TaskControllerOptions['executeScanTask']} options
     * - request 扫描任务请求参数
     * 
     */
    readonly executeScanTask: (options: TaskControllerOptions['executeScanTask']) => Promise<
        void
    > = async(options) => {
        let _uri = '/api/tasks/scans';
        return (await this.executor({uri: _uri, method: 'POST', body: options.body})) as Promise<void>;
    }
    
    /**
     * 提交转码任务
     * 
     * 此接口用于异步触发音频转码任务
     * 需要用户登录认证才能访问
     * 
     * @parameter {TaskControllerOptions['executeTranscodeTask']} options
     * - request 转码任务请求参数
     * 
     */
    readonly executeTranscodeTask: (options: TaskControllerOptions['executeTranscodeTask']) => Promise<
        void
    > = async(options) => {
        let _uri = '/api/tasks/transcodes';
        return (await this.executor({uri: _uri, method: 'POST', body: options.body})) as Promise<void>;
    }
    
    /**
     * 获取任务日志计数列表
     * 
     * 此接口用于按任务维度聚合查询异步任务日志计数
     * 需要用户登录认证才能访问
     * 
     * @return List<AsyncTaskLogCountRow> 返回任务日志计数列表
     * 
     */
    readonly listTaskLogs: () => Promise<
        ReadonlyArray<AsyncTaskLogCountRow>
    > = async() => {
        let _uri = '/api/tasks/log-counts';
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
    'listTaskLogs': {}
}
