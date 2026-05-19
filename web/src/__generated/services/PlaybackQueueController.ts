import type {Executor} from '../';
import type {
    CurrentQueueAppendRequest, 
    CurrentQueueDto, 
    CurrentQueueRemoveRequest, 
    CurrentQueueReorderRequest, 
    CurrentQueueReplaceRequest, 
    CurrentQueueSetCurrentRequest, 
    CurrentQueueStrategyUpdateRequest, 
    CurrentQueueVersionRequest
} from '../model/static/';

/**
 * 当前播放队列管理接口
 * 
 * 提供当前播放队列的查询、替换、追加、重排、清空与导航能力，
 * 并在每次变更后通过同步通道广播给该账户的其他在线客户端
 */
export class PlaybackQueueController {
    
    constructor(private executor: Executor) {}
    
    /**
     * 向当前队列追加录音
     * 
     * 在当前队列末尾追加一批录音
     * 需要用户登录认证才能访问
     * 
     * @parameter {PlaybackQueueControllerOptions['appendToCurrentQueue']} options
     * - input 追加请求参数（录音列表、期望版本号）
     * @return CurrentQueueDto 返回追加后的队列
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
     * 移除当前队列中的全部录音，并同步清理播放状态
     * 需要用户登录认证才能访问
     * 
     * @parameter {PlaybackQueueControllerOptions['clearCurrentQueue']} options
     * - input 版本号请求参数（期望版本号）
     * @return CurrentQueueDto 返回清空后的队列
     * 
     */
    readonly clearCurrentQueue: (options: PlaybackQueueControllerOptions['clearCurrentQueue']) => Promise<
        CurrentQueueDto
    > = async(options) => {
        let _uri = '/api/playback/current-queue/actions/clear';
        return (await this.executor({uri: _uri, method: 'POST', body: options.body})) as Promise<CurrentQueueDto>;
    }
    
    /**
     * 获取当前播放队列
     * 
     * 此接口用于获取当前登录账户的播放队列快照
     * 需要用户登录认证才能访问
     * 
     * @return CurrentQueueDto 返回当前队列
     * 
     */
    readonly getCurrentQueue: () => Promise<
        CurrentQueueDto
    > = async() => {
        let _uri = '/api/playback/current-queue';
        return (await this.executor({uri: _uri, method: 'GET'})) as Promise<CurrentQueueDto>;
    }
    
    /**
     * 播放下一首
     * 
     * 根据当前播放策略导航到下一首录音，并同步播放状态
     * 需要用户登录认证才能访问
     * 
     * @parameter {PlaybackQueueControllerOptions['playNextInCurrentQueue']} options
     * - input 版本号请求参数（期望版本号）
     * @return CurrentQueueDto 返回更新后的队列
     * 
     */
    readonly playNextInCurrentQueue: (options: PlaybackQueueControllerOptions['playNextInCurrentQueue']) => Promise<
        CurrentQueueDto
    > = async(options) => {
        let _uri = '/api/playback/current-queue/actions/next';
        return (await this.executor({uri: _uri, method: 'POST', body: options.body})) as Promise<CurrentQueueDto>;
    }
    
    /**
     * 播放上一首
     * 
     * 根据当前播放策略导航到上一首录音，并同步播放状态
     * 需要用户登录认证才能访问
     * 
     * @parameter {PlaybackQueueControllerOptions['playPreviousInCurrentQueue']} options
     * - input 版本号请求参数（期望版本号）
     * @return CurrentQueueDto 返回更新后的队列
     * 
     */
    readonly playPreviousInCurrentQueue: (options: PlaybackQueueControllerOptions['playPreviousInCurrentQueue']) => Promise<
        CurrentQueueDto
    > = async(options) => {
        let _uri = '/api/playback/current-queue/actions/previous';
        return (await this.executor({uri: _uri, method: 'POST', body: options.body})) as Promise<CurrentQueueDto>;
    }
    
    /**
     * 从队列中移除指定索引项
     * 
     * 移除当前队列中指定下标的录音；若移除的是当前播放项，则同步推进播放状态
     * 需要用户登录认证才能访问
     * 
     * @parameter {PlaybackQueueControllerOptions['removeCurrentQueueEntry']} options
     * - input 移除请求参数（待移除项下标、期望版本号）
     * @return CurrentQueueDto 返回移除后的队列
     * 
     */
    readonly removeCurrentQueueEntry: (options: PlaybackQueueControllerOptions['removeCurrentQueueEntry']) => Promise<
        CurrentQueueDto
    > = async(options) => {
        let _uri = '/api/playback/current-queue/actions/remove';
        return (await this.executor({uri: _uri, method: 'POST', body: options.body})) as Promise<CurrentQueueDto>;
    }
    
    /**
     * 重排当前播放队列
     * 
     * 按给定的录音 ID 顺序重排当前队列，并指定新的当前索引
     * 需要用户登录认证才能访问
     * 
     * @parameter {PlaybackQueueControllerOptions['reorderCurrentQueue']} options
     * - input 重排请求参数（新顺序下的录音列表、当前索引、期望版本号）
     * @return CurrentQueueDto 返回重排后的队列
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
     * 使用给定的录音列表整体替换当前队列，并指定新的当前索引；
     * 变更后会通过同步通道广播，并将暂停态播放进度对齐到新的当前项
     * 需要用户登录认证才能访问
     * 
     * @parameter {PlaybackQueueControllerOptions['replaceCurrentQueue']} options
     * - input 替换请求参数（录音列表、当前索引、期望版本号）
     * @return CurrentQueueDto 返回替换后的队列
     * 
     */
    readonly replaceCurrentQueue: (options: PlaybackQueueControllerOptions['replaceCurrentQueue']) => Promise<
        CurrentQueueDto
    > = async(options) => {
        let _uri = '/api/playback/current-queue';
        return (await this.executor({uri: _uri, method: 'PUT', body: options.body})) as Promise<CurrentQueueDto>;
    }
    
    /**
     * 设置当前播放索引
     * 
     * 切换队列内当前播放项，并将暂停态播放进度对齐到该项
     * 需要用户登录认证才能访问
     * 
     * @parameter {PlaybackQueueControllerOptions['setCurrentIndex']} options
     * - input 设置当前索引的请求参数（当前索引、期望版本号）
     * @return CurrentQueueDto 返回更新后的队列
     * 
     */
    readonly setCurrentIndex: (options: PlaybackQueueControllerOptions['setCurrentIndex']) => Promise<
        CurrentQueueDto
    > = async(options) => {
        let _uri = '/api/playback/current-queue/current';
        return (await this.executor({uri: _uri, method: 'PUT', body: options.body})) as Promise<CurrentQueueDto>;
    }
    
    /**
     * 更新当前队列的播放/停止策略
     * 
     * 修改播放策略（顺序、随机等）与停止策略（单曲、列表等）
     * 需要用户登录认证才能访问
     * 
     * @parameter {PlaybackQueueControllerOptions['updateCurrentQueueStrategy']} options
     * - input 策略更新参数（播放策略、停止策略、期望版本号）
     * @return CurrentQueueDto 返回更新后的队列
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
         * 替换请求参数（录音列表、当前索引、期望版本号）
         */
        readonly body: CurrentQueueReplaceRequest
    }, 
    'appendToCurrentQueue': {
        /**
         * 追加请求参数（录音列表、期望版本号）
         */
        readonly body: CurrentQueueAppendRequest
    }, 
    'reorderCurrentQueue': {
        /**
         * 重排请求参数（新顺序下的录音列表、当前索引、期望版本号）
         */
        readonly body: CurrentQueueReorderRequest
    }, 
    'setCurrentIndex': {
        /**
         * 设置当前索引的请求参数（当前索引、期望版本号）
         */
        readonly body: CurrentQueueSetCurrentRequest
    }, 
    'updateCurrentQueueStrategy': {
        /**
         * 策略更新参数（播放策略、停止策略、期望版本号）
         */
        readonly body: CurrentQueueStrategyUpdateRequest
    }, 
    'playNextInCurrentQueue': {
        /**
         * 版本号请求参数（期望版本号）
         */
        readonly body: CurrentQueueVersionRequest
    }, 
    'playPreviousInCurrentQueue': {
        /**
         * 版本号请求参数（期望版本号）
         */
        readonly body: CurrentQueueVersionRequest
    }, 
    'removeCurrentQueueEntry': {
        /**
         * 移除请求参数（待移除项下标、期望版本号）
         */
        readonly body: CurrentQueueRemoveRequest
    }, 
    'clearCurrentQueue': {
        /**
         * 版本号请求参数（期望版本号）
         */
        readonly body: CurrentQueueVersionRequest
    }
}
