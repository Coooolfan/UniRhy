import type {Executor} from '../';
import type {WorkDto} from '../model/dto/';
import type {Page} from '../model/static/';

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
        let _uri = '/api/work/';
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
        let _uri = '/api/work/';
        _uri += encodeURIComponent(options.id);
        return (await this.executor({uri: _uri, method: 'GET'})) as Promise<WorkDto['WorkController/DEFAULT_WORK_FETCHER']>;
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
        let _uri = '/api/work';
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
        let _uri = '/api/work/random';
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
    'deleteWork': {
        /**
         * 作品 ID
         * 
         */
        readonly id: number
    }
}
