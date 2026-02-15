import type {Executor} from '../';
import type {RecordingUpdate} from '../model/static/';

/**
 * 录音管理接口
 * 
 * 提供录音信息的增删改查能力
 */
export class RecordingController {
    
    constructor(private executor: Executor) {}
    
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
    }
}
