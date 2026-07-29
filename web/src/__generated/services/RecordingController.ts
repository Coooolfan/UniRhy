import type {Executor} from '../';
import type {RecordingDto} from '../model/dto/';
import type {Page, RecordingMergeReq, RecordingUpdate} from '../model/static/';

/**
 * 录音管理接口
 * 
 * 提供录音信息的增删改查能力
 */
export class RecordingController {
    
    constructor(private executor: Executor) {}
    
    /**
     * 获取指定录音
     * 
     * 此接口用于根据录音 ID 获取播放器展示所需的最小录音信息。
     * 需要用户登录认证才能访问
     * 
     * @parameter {RecordingControllerOptions['getRecording']} options
     * - id Recording ID
     * @return Recording 返回录音信息（使用 PLAYBACK_RECORDING_FETCHER）
     * 
     */
    readonly getRecording: (options: RecordingControllerOptions['getRecording']) => Promise<
        RecordingDto['RecordingController/PLAYBACK_RECORDING_FETCHER']
    > = async(options) => {
        let _uri = '/api/recordings/';
        _uri += encodeURIComponent(options.id);
        return (await this.executor({uri: _uri, method: 'GET'})) as Promise<RecordingDto['RecordingController/PLAYBACK_RECORDING_FETCHER']>;
    }
    
    /**
     * 分页查询指定艺术家的录音
     * 
     * @parameter {RecordingControllerOptions['listRecordings']} options
     * - artistId 艺术家 ID
     * - pageIndex 页码（从 0 开始）
     * - pageSize 每页条数
     * @return Page<Recording> 返回录音分页列表
     * 
     */
    readonly listRecordings: (options: RecordingControllerOptions['listRecordings']) => Promise<
        Page<RecordingDto['RecordingController/RECORDING_LIST_FETCHER']>
    > = async(options) => {
        let _uri = '/api/recordings';
        let _separator = _uri.indexOf('?') === -1 ? '?' : '&';
        let _value: any = undefined;
        _value = options.artistId;
        _uri += _separator
        _uri += 'artistId='
        _uri += encodeURIComponent(_value);
        _separator = '&';
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
        return (await this.executor({uri: _uri, method: 'GET'})) as Promise<Page<RecordingDto['RecordingController/RECORDING_LIST_FETCHER']>>;
    }
    
    /**
     * 录音合并接口
     * 
     * 此接口用于将多个录音合并为一个录音
     * 需要用户登录认证才能访问
     * 
     * @parameter {RecordingControllerOptions['mergeRecording']} options
     * - input RecordingMergeReq 合并参数
     * 
     */
    readonly mergeRecording: (options: RecordingControllerOptions['mergeRecording']) => Promise<
        void
    > = async(options) => {
        let _uri = '/api/recordings/merge-requests';
        return (await this.executor({uri: _uri, method: 'POST', body: options.body})) as Promise<void>;
    }
    
    /**
     * 更新录音信息
     * 
     * 此接口用于更新系统中已有的录音信息
     * 需要用户登录认证才能访问
     * 
     * @parameter {RecordingControllerOptions['updateRecording']} options
     * - id Recording ID
     * - input RecordingUpdate 更新参数
     * 
     */
    readonly updateRecording: (options: RecordingControllerOptions['updateRecording']) => Promise<
        void
    > = async(options) => {
        let _uri = '/api/recordings/';
        _uri += encodeURIComponent(options.id);
        return (await this.executor({uri: _uri, method: 'PUT', body: options.body})) as Promise<void>;
    }
}

export type RecordingControllerOptions = {
    'listRecordings': {
        /**
         * 艺术家 ID
         */
        readonly artistId: number, 
        /**
         * 页码（从 0 开始）
         */
        readonly pageIndex?: number | undefined, 
        /**
         * 每页条数
         */
        readonly pageSize?: number | undefined
    }, 
    'getRecording': {
        /**
         * Recording ID
         */
        readonly id: number
    }, 
    'updateRecording': {
        /**
         * Recording ID
         */
        readonly id: number, 
        /**
         * RecordingUpdate 更新参数
         * 
         */
        readonly body: RecordingUpdate
    }, 
    'mergeRecording': {
        /**
         * RecordingMergeReq 合并参数
         * 
         */
        readonly body: RecordingMergeReq
    }
}
