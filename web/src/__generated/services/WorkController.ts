import type {Executor} from '../';
import type {WorkDto} from '../model/dto/';
import type {Page, WorkMergeReq} from '../model/static/';

/**
 * 作品管理接口
 * 
 * 提供作品列表查询、随机获取与删除能力
 */
export class WorkController {
    
    constructor(private executor: Executor) {}
    
    /**
     * 删除指定作品
     * 
     * @parameter {WorkControllerOptions['deleteWork']} options
     * - id 作品 ID
     * 
     */
    readonly deleteWork: (options: WorkControllerOptions['deleteWork']) => Promise<
        void
    > = async(options) => {
        let _uri = '/api/works/';
        _uri += encodeURIComponent(options.id);
        return (await this.executor({uri: _uri, method: 'DELETE'})) as Promise<void>;
    }
    
    /**
     * 获取指定作品
     * 
     * @parameter {WorkControllerOptions['getWorkById']} options
     * - id 作品 ID
     * @return Work 返回作品详情（默认 fetcher）
     * 
     */
    readonly getWorkById: (options: WorkControllerOptions['getWorkById']) => Promise<
        WorkDto['WorkController/DEFAULT_WORK_FETCHER']
    > = async(options) => {
        let _uri = '/api/works/';
        _uri += encodeURIComponent(options.id);
        return (await this.executor({uri: _uri, method: 'GET'})) as Promise<WorkDto['WorkController/DEFAULT_WORK_FETCHER']>;
    }
    
    /**
     * 根据作品名称搜索
     * 
     * @parameter {WorkControllerOptions['getWorkByName']} options
     * - name 作品名称
     * @return List<Work> 返回作品列表（默认 fetcher）
     * 
     */
    readonly getWorkByName: (options: WorkControllerOptions['getWorkByName']) => Promise<
        ReadonlyArray<WorkDto['WorkController/DEFAULT_WORK_FETCHER']>
    > = async(options) => {
        let _uri = '/api/works/search';
        let _separator = _uri.indexOf('?') === -1 ? '?' : '&';
        let _value: any = undefined;
        _value = options.name;
        _uri += _separator
        _uri += 'name='
        _uri += encodeURIComponent(_value);
        _separator = '&';
        return (await this.executor({uri: _uri, method: 'GET'})) as Promise<ReadonlyArray<WorkDto['WorkController/DEFAULT_WORK_FETCHER']>>;
    }
    
    /**
     * 获取作品列表
     * 
     * @return Page<Work> 返回作品分页列表（默认 fetcher）
     * 
     */
    readonly listWork: (options: WorkControllerOptions['listWork']) => Promise<
        Page<WorkDto['WorkController/DEFAULT_WORK_FETCHER']>
    > = async(options) => {
        let _uri = '/api/works';
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
        return (await this.executor({uri: _uri, method: 'GET'})) as Promise<Page<WorkDto['WorkController/DEFAULT_WORK_FETCHER']>>;
    }
    
    /**
     * 合并作品
     * 
     * 将多个作品合并为一个作品，将所有曲目合并到目标作品中，
     * 并删除其他作品。
     * 
     * @parameter {WorkControllerOptions['mergeWork']} options
     * - input 合并请求体
     * 
     */
    readonly mergeWork: (options: WorkControllerOptions['mergeWork']) => Promise<
        void
    > = async(options) => {
        let _uri = '/api/works/merge';
        return (await this.executor({uri: _uri, method: 'POST', body: options.body})) as Promise<void>;
    }
    
    /**
     * 获取时间窗口内的随机作品
     * 
     * 通过时间戳、时间长度与时区偏移计算出“时间窗口编号”，并将其作为随机种子，
     * 从而保证同一窗口内的所有请求都返回相同的作品。
     * 
     * @parameter {WorkControllerOptions['randomWork']} options
     * - timestamp 时间戳（秒/毫秒），可空，默认当前时间
     * - length 时间长度（毫秒），可空，默认 86400000（天），常用：3600000（小时）/43200000（半天）/86400000（天）/604800000（周）
     * - offset 时区偏移（毫秒），可空，默认 0L，建议传浏览器 `Date.getTimezoneOffset() * 60000`（毫秒，满足 `utc = local + offset`）
     * @return Work 返回随机作品（默认 fetcher）
     * 
     */
    readonly randomWork: (options: WorkControllerOptions['randomWork']) => Promise<
        WorkDto['WorkController/DEFAULT_WORK_FETCHER']
    > = async(options) => {
        let _uri = '/api/works/random';
        let _separator = _uri.indexOf('?') === -1 ? '?' : '&';
        let _value: any = undefined;
        _value = options.timestamp;
        if (_value !== undefined && _value !== null) {
            _uri += _separator
            _uri += 'timestamp='
            _uri += encodeURIComponent(_value);
            _separator = '&';
        }
        _value = options.length;
        if (_value !== undefined && _value !== null) {
            _uri += _separator
            _uri += 'length='
            _uri += encodeURIComponent(_value);
            _separator = '&';
        }
        _value = options.offset;
        if (_value !== undefined && _value !== null) {
            _uri += _separator
            _uri += 'offset='
            _uri += encodeURIComponent(_value);
            _separator = '&';
        }
        return (await this.executor({uri: _uri, method: 'GET'})) as Promise<WorkDto['WorkController/DEFAULT_WORK_FETCHER']>;
    }
}

export type WorkControllerOptions = {
    'listWork': {
        readonly pageIndex?: number | undefined, 
        readonly pageSize?: number | undefined
    }, 
    'getWorkById': {
        /**
         * 作品 ID
         */
        readonly id: number
    }, 
    'randomWork': {
        /**
         * 时间戳（秒/毫秒），可空，默认当前时间
         */
        readonly timestamp?: number | undefined, 
        /**
         * 时间长度（毫秒），可空，默认 86400000（天），常用：3600000（小时）/43200000（半天）/86400000（天）/604800000（周）
         */
        readonly length?: number | undefined, 
        /**
         * 时区偏移（毫秒），可空，默认 0L，建议传浏览器 `Date.getTimezoneOffset() * 60000`（毫秒，满足 `utc = local + offset`）
         */
        readonly offset?: number | undefined
    }, 
    'getWorkByName': {
        /**
         * 作品名称
         */
        readonly name: string
    }, 
    'deleteWork': {
        /**
         * 作品 ID
         * 
         */
        readonly id: number
    }, 
    'mergeWork': {
        /**
         * 合并请求体
         * 
         */
        readonly body: WorkMergeReq
    }
}
