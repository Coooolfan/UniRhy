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

export class PlaybackQueueController {
    
    constructor(private executor: Executor) {}
    
    readonly appendToCurrentQueue: (options: PlaybackQueueControllerOptions['appendToCurrentQueue']) => Promise<
        CurrentQueueDto
    > = async(options) => {
        let _uri = '/api/playback/current-queue/items';
        return (await this.executor({uri: _uri, method: 'POST', body: options.body})) as Promise<CurrentQueueDto>;
    }
    
    readonly clearCurrentQueue: (options: PlaybackQueueControllerOptions['clearCurrentQueue']) => Promise<
        CurrentQueueDto
    > = async(options) => {
        let _uri = '/api/playback/current-queue/actions/clear';
        return (await this.executor({uri: _uri, method: 'POST', body: options.body})) as Promise<CurrentQueueDto>;
    }
    
    readonly getCurrentQueue: () => Promise<
        CurrentQueueDto
    > = async() => {
        let _uri = '/api/playback/current-queue';
        return (await this.executor({uri: _uri, method: 'GET'})) as Promise<CurrentQueueDto>;
    }
    
    readonly playNextInCurrentQueue: (options: PlaybackQueueControllerOptions['playNextInCurrentQueue']) => Promise<
        CurrentQueueDto
    > = async(options) => {
        let _uri = '/api/playback/current-queue/actions/next';
        return (await this.executor({uri: _uri, method: 'POST', body: options.body})) as Promise<CurrentQueueDto>;
    }
    
    readonly playPreviousInCurrentQueue: (options: PlaybackQueueControllerOptions['playPreviousInCurrentQueue']) => Promise<
        CurrentQueueDto
    > = async(options) => {
        let _uri = '/api/playback/current-queue/actions/previous';
        return (await this.executor({uri: _uri, method: 'POST', body: options.body})) as Promise<CurrentQueueDto>;
    }
    
    readonly removeCurrentQueueEntry: (options: PlaybackQueueControllerOptions['removeCurrentQueueEntry']) => Promise<
        CurrentQueueDto
    > = async(options) => {
        let _uri = '/api/playback/current-queue/actions/remove';
        return (await this.executor({uri: _uri, method: 'POST', body: options.body})) as Promise<CurrentQueueDto>;
    }
    
    readonly reorderCurrentQueue: (options: PlaybackQueueControllerOptions['reorderCurrentQueue']) => Promise<
        CurrentQueueDto
    > = async(options) => {
        let _uri = '/api/playback/current-queue/order';
        return (await this.executor({uri: _uri, method: 'PUT', body: options.body})) as Promise<CurrentQueueDto>;
    }
    
    readonly replaceCurrentQueue: (options: PlaybackQueueControllerOptions['replaceCurrentQueue']) => Promise<
        CurrentQueueDto
    > = async(options) => {
        let _uri = '/api/playback/current-queue';
        return (await this.executor({uri: _uri, method: 'PUT', body: options.body})) as Promise<CurrentQueueDto>;
    }
    
    readonly setCurrentIndex: (options: PlaybackQueueControllerOptions['setCurrentIndex']) => Promise<
        CurrentQueueDto
    > = async(options) => {
        let _uri = '/api/playback/current-queue/current';
        return (await this.executor({uri: _uri, method: 'PUT', body: options.body})) as Promise<CurrentQueueDto>;
    }
    
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
        readonly body: CurrentQueueReplaceRequest
    }, 
    'appendToCurrentQueue': {
        readonly body: CurrentQueueAppendRequest
    }, 
    'reorderCurrentQueue': {
        readonly body: CurrentQueueReorderRequest
    }, 
    'setCurrentIndex': {
        readonly body: CurrentQueueSetCurrentRequest
    }, 
    'updateCurrentQueueStrategy': {
        readonly body: CurrentQueueStrategyUpdateRequest
    }, 
    'playNextInCurrentQueue': {
        readonly body: CurrentQueueVersionRequest
    }, 
    'playPreviousInCurrentQueue': {
        readonly body: CurrentQueueVersionRequest
    }, 
    'removeCurrentQueueEntry': {
        readonly body: CurrentQueueRemoveRequest
    }, 
    'clearCurrentQueue': {
        readonly body: CurrentQueueVersionRequest
    }
}
