import type {Executor} from '../';
import type {AsyncTaskDto} from '../model/dto/';
import type {TaskAction, TaskStatus} from '../model/enums/';
import type {
    Page, 
    TaskCreateRequest, 
    TaskCreatedResponse, 
    TaskDetailResponse, 
    TaskStatusBatchPatchRequest, 
    TaskStatusPatchRequest
} from '../model/static/';

/**
 * 统一任务资源管理接口。
 */
export class TaskController {
    
    constructor(private executor: Executor) {}
    
    /**
     * 创建一个 PLAN 根任务。
     */
    readonly createTask: (options: TaskControllerOptions['createTask']) => Promise<
        TaskCreatedResponse
    > = async(options) => {
        let _uri = '/api/tasks';
        return (await this.executor({uri: _uri, method: 'POST', body: options.body})) as Promise<TaskCreatedResponse>;
    }
    
    readonly getTask: (options: TaskControllerOptions['getTask']) => Promise<
        TaskDetailResponse
    > = async(options) => {
        let _uri = '/api/tasks/';
        _uri += encodeURIComponent(options.id);
        return (await this.executor({uri: _uri, method: 'GET'})) as Promise<TaskDetailResponse>;
    }
    
    readonly getTaskTree: (options: TaskControllerOptions['getTaskTree']) => Promise<
        AsyncTaskDto['TaskController/TASK_TREE_FETCHER']
    > = async(options) => {
        let _uri = '/api/tasks/';
        _uri += encodeURIComponent(options.id);
        _uri += '/tree';
        return (await this.executor({uri: _uri, method: 'GET'})) as Promise<AsyncTaskDto['TaskController/TASK_TREE_FETCHER']>;
    }
    
    readonly listTasks: (options: TaskControllerOptions['listTasks']) => Promise<
        Page<AsyncTaskDto['TaskController/DEFAULT_TASK_FETCHER']>
    > = async(options) => {
        let _uri = '/api/tasks';
        let _separator = _uri.indexOf('?') === -1 ? '?' : '&';
        let _value: any = undefined;
        _value = options.parentId;
        if (_value !== undefined && _value !== null) {
            _uri += _separator
            _uri += 'parentId='
            _uri += encodeURIComponent(_value);
            _separator = '&';
        }
        _value = options.rootsOnly;
        if (_value !== undefined && _value !== null) {
            _uri += _separator
            _uri += 'rootsOnly='
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
        _value = options.actions;
        if (_value !== undefined && _value !== null) {
            for (const _item of _value) {
                _uri += _separator
                _uri += 'actions='
                _uri += encodeURIComponent(_item);
                _separator = '&';
            }
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
    
    readonly patchTask: (options: TaskControllerOptions['patchTask']) => Promise<
        AsyncTaskDto['TaskController/DEFAULT_TASK_FETCHER']
    > = async(options) => {
        let _uri = '/api/tasks/';
        _uri += encodeURIComponent(options.id);
        return (await this.executor({uri: _uri, method: 'PATCH', body: options.body})) as Promise<AsyncTaskDto['TaskController/DEFAULT_TASK_FETCHER']>;
    }
    
    readonly patchTasks: (options: TaskControllerOptions['patchTasks']) => Promise<
        number
    > = async(options) => {
        let _uri = '/api/tasks';
        return (await this.executor({uri: _uri, method: 'PATCH', body: options.body})) as Promise<number>;
    }
}

export type TaskControllerOptions = {
    'createTask': {
        readonly body: TaskCreateRequest
    }, 
    'listTasks': {
        readonly parentId?: number | undefined, 
        readonly rootsOnly?: boolean | undefined, 
        readonly namespace?: string | undefined, 
        readonly taskType?: string | undefined, 
        readonly actions?: ReadonlyArray<TaskAction> | undefined, 
        readonly statuses?: ReadonlyArray<TaskStatus> | undefined, 
        readonly pageIndex?: number | undefined, 
        readonly pageSize?: number | undefined
    }, 
    'getTask': {
        readonly id: number
    }, 
    'getTaskTree': {
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
