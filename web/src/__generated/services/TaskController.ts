import type {Executor} from '../';
import type {AsyncTaskLogDto} from '../model/dto/';
import type {TaskStatus, TaskType} from '../model/enums/';
import type {
    AsyncTaskLogCountRow, 
    Page, 
    ScanTaskRequest, 
    TranscodeTaskRequest
} from '../model/static/';

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
     * 按任务类型 + 状态集合分页查询任务日志明细
     * 
     * @parameter {TaskControllerOptions['listTaskLogDetails']} options
     * - taskType 任务类型
     * - statuses 任务状态集合（允许多值）
     * - pageIndex 页码（从 0 开始）
     * - pageSize 每页条数
     * @return Page<AsyncTaskLog> 分页结果
     * 
     */
    readonly listTaskLogDetails: (options: TaskControllerOptions['listTaskLogDetails']) => Promise<
        Page<AsyncTaskLogDto['TaskController/DEFAULT_TASK_LOG_FETCHER']>
    > = async(options) => {
        let _uri = '/api/tasks/logs';
        let _separator = _uri.indexOf('?') === -1 ? '?' : '&';
        let _value: any = undefined;
        _value = options.taskType;
        _uri += _separator
        _uri += 'taskType='
        _uri += encodeURIComponent(_value);
        _separator = '&';
        _value = options.statuses;
        if (_value !== undefined && _value !== null) {
            for (const _item of _value) {
                _uri += _separator
                _uri += 'statuses='
                _uri += encodeURIComponent(_item);
                _separator = '&';
            }
        }
        _value = options.pageIndex;
        if (_value !== undefined && _value !== null) {
            _uri += _separator
            _uri += 'pageIndex='
            _uri += encodeURIComponent(_value);
            _separator = '&';
        }
        _value = options.pageSize;
        if (_value !== undefined && _value !== null) {
            _uri += _separator
            _uri += 'pageSize='
            _uri += encodeURIComponent(_value);
            _separator = '&';
        }
        return (await this.executor({uri: _uri, method: 'GET'})) as Promise<Page<AsyncTaskLogDto['TaskController/DEFAULT_TASK_LOG_FETCHER']>>;
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
    
    /**
     * 将 FAILED / COMPLETED 状态的任务重置为 PENDING，让 worker 重新执行
     * 
     * @parameter {TaskControllerOptions['resetTaskLog']} options
     * - id 任务日志 ID
     * 
     */
    readonly resetTaskLog: (options: TaskControllerOptions['resetTaskLog']) => Promise<
        void
    > = async(options) => {
        let _uri = '/api/tasks/logs/';
        _uri += encodeURIComponent(options.id);
        _uri += '/reset';
        return (await this.executor({uri: _uri, method: 'POST'})) as Promise<void>;
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
    'listTaskLogs': {}, 
    'listTaskLogDetails': {
        /**
         * 任务类型
         */
        readonly taskType: TaskType, 
        /**
         * 任务状态集合（允许多值）
         */
        readonly statuses?: ReadonlyArray<TaskStatus> | undefined, 
        /**
         * 页码（从 0 开始）
         */
        readonly pageIndex?: number | undefined, 
        /**
         * 每页条数
         */
        readonly pageSize?: number | undefined
    }, 
    'resetTaskLog': {
        /**
         * 任务日志 ID
         * 
         */
        readonly id: number
    }
}
