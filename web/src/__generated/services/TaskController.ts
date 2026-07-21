import type {Executor} from '../';
import type {AsyncTaskDto} from '../model/dto/';
import type {TaskStatus} from '../model/enums/';
import type {Page, TaskStatusBatchPatchRequest, TaskStatusPatchRequest} from '../model/static/';

/**
 * 任务执行资源管理接口
 * 
 * 任务提交经由 `/api/task-submissions`；本接口只查询与管理单条执行任务
 */
export class TaskController {
    
    constructor(private executor: Executor) {}
    
    /**
     * 查询单条任务
     * 
     */
    readonly getTask: (options: TaskControllerOptions['getTask']) => Promise<
        AsyncTaskDto['TaskController/DEFAULT_TASK_FETCHER']
    > = async(options) => {
        let _uri = '/api/tasks/';
        _uri += encodeURIComponent(options.id);
        return (await this.executor({uri: _uri, method: 'GET'})) as Promise<AsyncTaskDto['TaskController/DEFAULT_TASK_FETCHER']>;
    }
    
    /**
     * 分页查询任务
     * 
     */
    readonly listTasks: (options: TaskControllerOptions['listTasks']) => Promise<
        Page<AsyncTaskDto['TaskController/DEFAULT_TASK_FETCHER']>
    > = async(options) => {
        let _uri = '/api/tasks';
        let _separator = _uri.indexOf('?') === -1 ? '?' : '&';
        let _value: any = undefined;
        _value = options.submissionId;
        if (_value !== undefined && _value !== null) {
            _uri += _separator
            _uri += 'submissionId='
            _uri += encodeURIComponent(_value);
            _separator = '&';
        }
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
        return (await this.executor({uri: _uri, method: 'GET'})) as Promise<Page<AsyncTaskDto['TaskController/DEFAULT_TASK_FETCHER']>>;
    }
    
    /**
     * 单项状态变更；只接受 `PENDING -> CANCELLED` 与 `FAILED -> PENDING`
     * 
     */
    readonly patchTask: (options: TaskControllerOptions['patchTask']) => Promise<
        AsyncTaskDto['TaskController/DEFAULT_TASK_FETCHER']
    > = async(options) => {
        let _uri = '/api/tasks/';
        _uri += encodeURIComponent(options.id);
        return (await this.executor({uri: _uri, method: 'PATCH', body: options.body})) as Promise<AsyncTaskDto['TaskController/DEFAULT_TASK_FETCHER']>;
    }
    
    /**
     * 批量状态变更，返回实际更新数量
     * 
     */
    readonly patchTasks: (options: TaskControllerOptions['patchTasks']) => Promise<
        number
    > = async(options) => {
        let _uri = '/api/tasks';
        return (await this.executor({uri: _uri, method: 'PATCH', body: options.body})) as Promise<number>;
    }
}

export type TaskControllerOptions = {
    'listTasks': {
        readonly submissionId?: number | undefined, 
        readonly namespace?: string | undefined, 
        readonly taskType?: string | undefined, 
        readonly statuses?: ReadonlyArray<TaskStatus> | undefined, 
        readonly pageIndex?: number | undefined, 
        readonly pageSize?: number | undefined
    }, 
    'getTask': {
        readonly id: number
    }, 
    'patchTask': {
        readonly id: number, 
        readonly body: TaskStatusPatchRequest
    }, 
    'patchTasks': {
        readonly body: TaskStatusBatchPatchRequest
    }
}
