import type {Executor} from '../';
import type {AsyncTaskLogDto} from '../model/dto/';
import type {AsyncTaskLogStatus, TaskType} from '../model/enums/';
import type {Page, ScanTaskRequest, TranscodeTaskRequest} from '../model/static/';

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
     * 触发转码任务
     * 
     * 此接口用于提交媒体转码任务，非幂等操作
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
     * 分页查询任务日志
     * 
     */
    readonly listTaskLogs: (options: TaskControllerOptions['listTaskLogs']) => Promise<
        Page<AsyncTaskLogDto['TaskController/DEFAULT_ASYNC_TASK_LOG_FETCHER']>
    > = async(options) => {
        let _uri = '/api/task/logs';
        let _separator = _uri.indexOf('?') === -1 ? '?' : '&';
        let _value: any = undefined;
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
        _value = options.taskType;
        if (_value !== undefined && _value !== null) {
            _uri += _separator
            _uri += 'taskType='
            _uri += encodeURIComponent(_value);
            _separator = '&';
        }
        _value = options.status;
        if (_value !== undefined && _value !== null) {
            _uri += _separator
            _uri += 'status='
            _uri += encodeURIComponent(_value);
            _separator = '&';
        }
        return (await this.executor({uri: _uri, method: 'GET'})) as Promise<Page<AsyncTaskLogDto['TaskController/DEFAULT_ASYNC_TASK_LOG_FETCHER']>>;
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
    'listTaskLogs': {
        readonly pageIndex?: number | undefined, 
        readonly pageSize?: number | undefined, 
        readonly taskType?: TaskType | undefined, 
        readonly status?: AsyncTaskLogStatus | undefined
    }
}
