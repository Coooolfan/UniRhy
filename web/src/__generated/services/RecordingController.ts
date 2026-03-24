import type {Executor} from '../';
import type {RecordingDto} from '../model/dto/';
import type {RecordingMergeReq, RecordingUpdate} from '../model/static/';

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
     * 获取相似录音
     * 
     * 基于 pgvector 余弦距离查找与指定录音最相似的录音列表
     * 
     * @parameter {RecordingControllerOptions['getSimilarRecordings']} options
     * - id Recording ID
     * - limit 返回数量上限，默认 5
     * @return 相似录音列表
     */
    readonly getSimilarRecordings: (options: RecordingControllerOptions['getSimilarRecordings']) => Promise<
        ReadonlyArray<RecordingDto['RecordingController/SIMILAR_RECORDING_FETCHER']>
    > = async(options) => {
        let _uri = '/api/recordings/';
        _uri += encodeURIComponent(options.id);
        _uri += '/similar';
        let _separator = _uri.indexOf('?') === -1 ? '?' : '&';
        let _value: any = undefined;
        _value = options.limit;
        if (_value !== undefined && _value !== null) {
            _uri += _separator
            _uri += 'limit='
            _uri += encodeURIComponent(_value);
            _separator = '&';
        }
        return (await this.executor({uri: _uri, method: 'GET'})) as Promise<ReadonlyArray<RecordingDto['RecordingController/SIMILAR_RECORDING_FETCHER']>>;
    }
    
    readonly mergeRecording: (options: RecordingControllerOptions['mergeRecording']) => Promise<
        void
    > = async(options) => {
        let _uri = '/api/recordings/merge';
        return (await this.executor({uri: _uri, method: 'PUT', body: options.body})) as Promise<void>;
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
    'getSimilarRecordings': {
        /**
         * Recording ID
         */
        readonly id: number, 
        /**
         * 返回数量上限，默认 5
         */
        readonly limit?: number | undefined
    }, 
    'mergeRecording': {
        readonly body: RecordingMergeReq
    }
}
