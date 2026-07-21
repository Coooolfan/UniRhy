import type {Executor} from '../';
import type {AsyncTaskDto, TaskSubmissionDto} from '../model/dto/';
import type {TaskStatus} from '../model/enums/';
import type {
    Page, 
    TaskStatusBatchPatchRequest, 
    TaskStatusPatchRequest, 
    TaskSubmissionCreateRequest, 
    TaskSubmissionCreatedResponse, 
    TaskSubmissionDetailResponse
} from '../model/static/';

/**
 * 任务提交管理接口
 * 
 * 内建任务与插件任务共用的统一 submission 资源
 */
export class TaskSubmissionController {
    
    constructor(private executor: Executor) {}
    
    /**
     * 创建 submission（触发一次任务规划）
     * 
     * 普通非幂等 POST：每个通过校验并被受理的请求都创建新的 submission。
     * HTTP 线程只校验任务身份、可用性与请求结构，规划由 Planner worker 异步执行。
     * 
     * @parameter {TaskSubmissionControllerOptions['createSubmission']} options
     * - request 任务身份二元组与根 Object 参数
     * @return 202 Accepted，`Location: /api/task-submissions/{id}` 与 submissionId
     * 
     */
    readonly createSubmission: (options: TaskSubmissionControllerOptions['createSubmission']) => Promise<
        TaskSubmissionCreatedResponse
    > = async(options) => {
        let _uri = '/api/task-submissions';
        return (await this.executor({uri: _uri, method: 'POST', body: options.body})) as Promise<TaskSubmissionCreatedResponse>;
    }
    
    /**
     * 删除 submission 及其全部子任务（数据库级联）
     * 
     * 仅当 submission 与全部子任务都处于终态时允许
     * 
     */
    readonly deleteSubmission: (options: TaskSubmissionControllerOptions['deleteSubmission']) => Promise<
        void
    > = async(options) => {
        let _uri = '/api/task-submissions/';
        _uri += encodeURIComponent(options.id);
        return (await this.executor({uri: _uri, method: 'DELETE'})) as Promise<void>;
    }
    
    /**
     * 查询单项 submission 及其子任务状态计数
     * 
     * 规划状态与子任务状态彼此独立，前端不把子任务汇总结果回写为 submission 状态
     * 
     */
    readonly getSubmission: (options: TaskSubmissionControllerOptions['getSubmission']) => Promise<
        TaskSubmissionDetailResponse
    > = async(options) => {
        let _uri = '/api/task-submissions/';
        _uri += encodeURIComponent(options.id);
        return (await this.executor({uri: _uri, method: 'GET'})) as Promise<TaskSubmissionDetailResponse>;
    }
    
    /**
     * 分页查询 submission 的关联任务
     * 
     */
    readonly listSubmissionTasks: (options: TaskSubmissionControllerOptions['listSubmissionTasks']) => Promise<
        Page<AsyncTaskDto['TaskController/DEFAULT_TASK_FETCHER']>
    > = async(options) => {
        let _uri = '/api/task-submissions/';
        _uri += encodeURIComponent(options.id);
        _uri += '/tasks';
        let _separator = _uri.indexOf('?') === -1 ? '?' : '&';
        let _value: any = undefined;
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
        return (await this.executor({uri: _uri, method: 'GET'})) as Promise<Page<AsyncTaskDto['TaskController/DEFAULT_TASK_FETCHER']>>;
    }
    
    /**
     * 分页查询 submission
     * 
     */
    readonly listSubmissions: (options: TaskSubmissionControllerOptions['listSubmissions']) => Promise<
        Page<TaskSubmissionDto['TaskSubmissionController/DEFAULT_SUBMISSION_FETCHER']>
    > = async(options) => {
        let _uri = '/api/task-submissions';
        let _separator = _uri.indexOf('?') === -1 ? '?' : '&';
        let _value: any = undefined;
        _value = options.namespace;
        if (_value !== undefined && _value !== null) {
            _uri += _separator
            _uri += 'namespace='
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
        return (await this.executor({uri: _uri, method: 'GET'})) as Promise<Page<TaskSubmissionDto['TaskSubmissionController/DEFAULT_SUBMISSION_FETCHER']>>;
    }
    
    /**
     * 单项状态变更；只接受 `PENDING -> CANCELLED` 与 `FAILED -> PENDING`
     * 
     */
    readonly patchSubmission: (options: TaskSubmissionControllerOptions['patchSubmission']) => Promise<
        TaskSubmissionDto['TaskSubmissionController/DEFAULT_SUBMISSION_FETCHER']
    > = async(options) => {
        let _uri = '/api/task-submissions/';
        _uri += encodeURIComponent(options.id);
        return (await this.executor({uri: _uri, method: 'PATCH', body: options.body})) as Promise<TaskSubmissionDto['TaskSubmissionController/DEFAULT_SUBMISSION_FETCHER']>;
    }
    
    /**
     * 批量状态变更，返回实际更新数量
     * 
     */
    readonly patchSubmissions: (options: TaskSubmissionControllerOptions['patchSubmissions']) => Promise<
        number
    > = async(options) => {
        let _uri = '/api/task-submissions';
        return (await this.executor({uri: _uri, method: 'PATCH', body: options.body})) as Promise<number>;
    }
}

export type TaskSubmissionControllerOptions = {
    'createSubmission': {
        /**
         * 任务身份二元组与根 Object 参数
         */
        readonly body: TaskSubmissionCreateRequest
    }, 
    'listSubmissions': {
        readonly namespace?: string | undefined, 
        readonly taskType?: string | undefined, 
        readonly statuses?: ReadonlyArray<TaskStatus> | undefined, 
        readonly pageIndex?: number | undefined, 
        readonly pageSize?: number | undefined
    }, 
    'getSubmission': {
        readonly id: number
    }, 
    'listSubmissionTasks': {
        readonly id: number, 
        readonly statuses?: ReadonlyArray<TaskStatus> | undefined, 
        readonly pageIndex?: number | undefined, 
        readonly pageSize?: number | undefined
    }, 
    'patchSubmission': {
        readonly id: number, 
        readonly body: TaskStatusPatchRequest
    }, 
    'patchSubmissions': {
        readonly body: TaskStatusBatchPatchRequest
    }, 
    'deleteSubmission': {
        readonly id: number
    }
}
