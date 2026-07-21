import type {Executor} from '../';
import type {TaskStatisticsResponse} from '../model/static/';

/**
 * 任务统计接口
 */
export class TaskStatisticsController {
    
    constructor(private executor: Executor) {}
    
    /**
     * 按 TaskKey 统计 submission 与 async task 的状态计数
     * 
     * @parameter {TaskStatisticsControllerOptions['getTaskStatistics']} options
     * - taskKeys 紧凑序列化形式（`namespace:TASK_TYPE`）的 TaskKey，
     *   可重复传递以过滤多个；缺省返回全部当前定义或存在历史记录的 TaskKey
     * 
     */
    readonly getTaskStatistics: (options: TaskStatisticsControllerOptions['getTaskStatistics']) => Promise<
        ReadonlyArray<TaskStatisticsResponse>
    > = async(options) => {
        let _uri = '/api/task-statistics';
        let _separator = _uri.indexOf('?') === -1 ? '?' : '&';
        let _value: any = undefined;
        _value = options.taskKeys;
        if (_value !== undefined && _value !== null) {
            for (const _item of _value) {
                _uri += _separator
                _uri += 'taskKeys='
                _uri += encodeURIComponent(_item);
                _separator = '&';
            }
        }
        return (await this.executor({uri: _uri, method: 'GET'})) as Promise<ReadonlyArray<TaskStatisticsResponse>>;
    }
}

export type TaskStatisticsControllerOptions = {
    'getTaskStatistics': {
        /**
         * 紧凑序列化形式（`namespace:TASK_TYPE`）的 TaskKey，
         *   可重复传递以过滤多个；缺省返回全部当前定义或存在历史记录的 TaskKey
         * 
         */
        readonly taskKeys?: ReadonlyArray<string> | undefined
    }
}
