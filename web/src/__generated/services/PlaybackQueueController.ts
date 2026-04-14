import type {Executor} from '../';
import type {
    CurrentQueueAppendRequest, 
    CurrentQueueDto, 
    CurrentQueueReorderRequest, 
    CurrentQueueReplaceRequest, 
    CurrentQueueSetCurrentRequest, 
    CurrentQueueStrategyUpdateRequest
} from '../model/static/';

/**
 * 当前播放队列管理接口
 * 
 * 提供当前账号播放队列的查询、替换、追加、重排与导航能力，
 * 并在需要时同步触发播放状态广播。
 */
export class PlaybackQueueController {
    
    constructor(private executor: Executor) {}
    
    /**
     * 向当前播放队列追加录音
     * 
     * 此接口用于向当前登录账号的播放队列末尾追加录音
     * 需要用户登录认证才能访问
     * 
     * @parameter {PlaybackQueueControllerOptions['appendToCurrentQueue']} options
     * - input CurrentQueueAppendRequest 队列追加参数
     * @return CurrentQueueDto 返回追加后的当前播放队列
     * 
     */
    readonly appendToCurrentQueue: (options: PlaybackQueueControllerOptions['appendToCurrentQueue']) => Promise<
        CurrentQueueDto
    > = async(options) => {
        let _uri = '/api/playback/current-queue/items';
        return (await this.executor({uri: _uri, method: 'POST', body: options.body})) as Promise<CurrentQueueDto>;
    }
    
    /**
     * 清空当前播放队列
     * 
     * 此接口用于清空当前登录账号的播放队列
     * 需要用户登录认证才能访问
     * 
     * @return CurrentQueueDto 返回清空后的当前播放队列
     * 
     */
    readonly clearCurrentQueue: () => Promise<
        CurrentQueueDto
    > = async() => {
        let _uri = '/api/playback/current-queue';
        return (await this.executor({uri: _uri, method: 'DELETE'})) as Promise<CurrentQueueDto>;
    }
    
    /**
     * 获取当前播放队列
     * 
     * 此接口用于获取当前登录账号的播放队列快照
     * 需要用户登录认证才能访问
     * 
     * @return CurrentQueueDto 返回当前播放队列
     * 
     */
    readonly getCurrentQueue: () => Promise<
        CurrentQueueDto
    > = async() => {
        let _uri = '/api/playback/current-queue';
        return (await this.executor({uri: _uri, method: 'GET'})) as Promise<CurrentQueueDto>;
    }
    
    /**
     * 执行下一首动作
     * 
     * 此接口用于按当前播放队列策略跳转到下一首，并在需要时触发播放同步
     * 需要用户登录认证才能访问
     * 
     * @return CurrentQueueDto 返回跳转后的当前播放队列
     * 
     */
    readonly playNextInCurrentQueue: () => Promise<
        CurrentQueueDto
    > = async() => {
        let _uri = '/api/playback/current-queue/actions/next';
        return (await this.executor({uri: _uri, method: 'POST'})) as Promise<CurrentQueueDto>;
    }
    
    /**
     * 执行上一首动作
     * 
     * 此接口用于按当前播放队列策略跳转到上一首，并在需要时触发播放同步
     * 需要用户登录认证才能访问
     * 
     * @return CurrentQueueDto 返回跳转后的当前播放队列
     * 
     */
    readonly playPreviousInCurrentQueue: () => Promise<
        CurrentQueueDto
    > = async() => {
        let _uri = '/api/playback/current-queue/actions/previous';
        return (await this.executor({uri: _uri, method: 'POST'})) as Promise<CurrentQueueDto>;
    }
    
    /**
     * 删除当前播放队列项
     * 
     * 此接口用于从当前播放队列中删除指定 entryId 对应的队列项
     * 需要用户登录认证才能访问
     * 
     * @parameter {PlaybackQueueControllerOptions['removeCurrentQueueEntry']} options
     * - entryId 队列项 ID
     * @return CurrentQueueDto 返回删除后的当前播放队列
     * 
     */
    readonly removeCurrentQueueEntry: (options: PlaybackQueueControllerOptions['removeCurrentQueueEntry']) => Promise<
        CurrentQueueDto
    > = async(options) => {
        let _uri = '/api/playback/current-queue/items/';
        _uri += encodeURIComponent(options.entryId);
        return (await this.executor({uri: _uri, method: 'DELETE'})) as Promise<CurrentQueueDto>;
    }
    
    /**
     * 重排当前播放队列
     * 
     * 此接口用于按指定 entryId 顺序重排当前播放队列
     * 需要用户登录认证才能访问
     * 
     * @parameter {PlaybackQueueControllerOptions['reorderCurrentQueue']} options
     * - input CurrentQueueReorderRequest 队列重排参数
     * @return CurrentQueueDto 返回重排后的当前播放队列
     * 
     */
    readonly reorderCurrentQueue: (options: PlaybackQueueControllerOptions['reorderCurrentQueue']) => Promise<
        CurrentQueueDto
    > = async(options) => {
        let _uri = '/api/playback/current-queue/order';
        return (await this.executor({uri: _uri, method: 'PUT', body: options.body})) as Promise<CurrentQueueDto>;
    }
    
    /**
     * 替换当前播放队列
     * 
     * 此接口用于使用指定录音列表整体替换当前播放队列，并设置当前项
     * 需要用户登录认证才能访问
     * 
     * @parameter {PlaybackQueueControllerOptions['replaceCurrentQueue']} options
     * - input CurrentQueueReplaceRequest 队列替换参数
     * @return CurrentQueueDto 返回替换后的当前播放队列
     * 
     */
    readonly replaceCurrentQueue: (options: PlaybackQueueControllerOptions['replaceCurrentQueue']) => Promise<
        CurrentQueueDto
    > = async(options) => {
        let _uri = '/api/playback/current-queue';
        return (await this.executor({uri: _uri, method: 'PUT', body: options.body})) as Promise<CurrentQueueDto>;
    }
    
    /**
     * 设置当前播放队列项
     * 
     * 此接口用于切换当前播放队列中的当前项
     * 需要用户登录认证才能访问
     * 
     * @parameter {PlaybackQueueControllerOptions['setCurrentEntry']} options
     * - input CurrentQueueSetCurrentRequest 当前项设置参数
     * @return CurrentQueueDto 返回更新后的当前播放队列
     * 
     */
    readonly setCurrentEntry: (options: PlaybackQueueControllerOptions['setCurrentEntry']) => Promise<
        CurrentQueueDto
    > = async(options) => {
        let _uri = '/api/playback/current-queue/current';
        return (await this.executor({uri: _uri, method: 'PUT', body: options.body})) as Promise<CurrentQueueDto>;
    }
    
    /**
     * 更新当前播放队列策略
     * 
     * 此接口用于更新当前播放队列的播放策略与停止策略
     * 需要用户登录认证才能访问
     * 
     * @parameter {PlaybackQueueControllerOptions['updateCurrentQueueStrategy']} options
     * - input CurrentQueueStrategyUpdateRequest 队列策略更新参数
     * @return CurrentQueueDto 返回更新后的当前播放队列
     * 
     */
    readonly updateCurrentQueueStrategy: (options: PlaybackQueueControllerOptions['updateCurrentQueueStrategy']) => Promise<
        CurrentQueueDto
    > = async(options) => {
        let _uri = '/api/playback/current-queue/strategy';
        return (await this.executor({uri: _uri, method: 'PUT', body: options.body})) as Promise<CurrentQueueDto>;
    }
}

export type PlaybackQueueControllerOptions = {
    'getCurrentQueue': {}, 
    'replaceCurrentQueue': {
        /**
         * CurrentQueueReplaceRequest 队列替换参数
         */
        readonly body: CurrentQueueReplaceRequest
    }, 
    'appendToCurrentQueue': {
        /**
         * CurrentQueueAppendRequest 队列追加参数
         */
        readonly body: CurrentQueueAppendRequest
    }, 
    'reorderCurrentQueue': {
        /**
         * CurrentQueueReorderRequest 队列重排参数
         */
        readonly body: CurrentQueueReorderRequest
    }, 
    'setCurrentEntry': {
        /**
         * CurrentQueueSetCurrentRequest 当前项设置参数
         */
        readonly body: CurrentQueueSetCurrentRequest
    }, 
    'updateCurrentQueueStrategy': {
        /**
         * CurrentQueueStrategyUpdateRequest 队列策略更新参数
         */
        readonly body: CurrentQueueStrategyUpdateRequest
    }, 
    'playNextInCurrentQueue': {}, 
    'playPreviousInCurrentQueue': {}, 
    'removeCurrentQueueEntry': {
        /**
         * 队列项 ID
         */
        readonly entryId: number
    }, 
    'clearCurrentQueue': {}
}
