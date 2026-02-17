import type {Executor} from '../';
import type {RecordingMergeReq, RecordingUpdate} from '../model/static/';

/**
 * 录音管理接口
 * 
 * 提供录音信息的增删改查能力
 */
export class RecordingController {
    
    constructor(private executor: Executor) {}
    
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
