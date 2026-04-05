import type {Executor} from '../';
import type {
    CurrentQueueAppendRequest, 
    CurrentQueueDto, 
    CurrentQueueReorderRequest, 
    CurrentQueueReplaceRequest, 
    CurrentQueueSetCurrentRequest, 
    CurrentQueueStrategyUpdateRequest
} from '../model/static/';

export class PlaybackQueueController {
    
    constructor(private executor: Executor) {}
    
    readonly appendToCurrentQueue: (options: PlaybackQueueControllerOptions['appendToCurrentQueue']) => Promise<
        CurrentQueueDto
    > = async(options) => {
        let _uri = '/api/playback/current-queue/items';
        return (await this.executor({uri: _uri, method: 'POST', body: options.body})) as Promise<CurrentQueueDto>;
    }
    
    readonly clearCurrentQueue: () => Promise<
        CurrentQueueDto
    > = async() => {
        let _uri = '/api/playback/current-queue';
        return (await this.executor({uri: _uri, method: 'DELETE'})) as Promise<CurrentQueueDto>;
    }
    
    readonly getCurrentQueue: () => Promise<
        CurrentQueueDto
    > = async() => {
        let _uri = '/api/playback/current-queue';
        return (await this.executor({uri: _uri, method: 'GET'})) as Promise<CurrentQueueDto>;
    }
    
    readonly playNextInCurrentQueue: () => Promise<
        CurrentQueueDto
    > = async() => {
        let _uri = '/api/playback/current-queue/next';
        return (await this.executor({uri: _uri, method: 'POST'})) as Promise<CurrentQueueDto>;
    }
    
    readonly playPreviousInCurrentQueue: () => Promise<
        CurrentQueueDto
    > = async() => {
        let _uri = '/api/playback/current-queue/previous';
        return (await this.executor({uri: _uri, method: 'POST'})) as Promise<CurrentQueueDto>;
    }
    
    readonly removeCurrentQueueEntry: (options: PlaybackQueueControllerOptions['removeCurrentQueueEntry']) => Promise<
        CurrentQueueDto
    > = async(options) => {
        let _uri = '/api/playback/current-queue/items/';
        _uri += encodeURIComponent(options.entryId);
        return (await this.executor({uri: _uri, method: 'DELETE'})) as Promise<CurrentQueueDto>;
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
    
    readonly setCurrentEntry: (options: PlaybackQueueControllerOptions['setCurrentEntry']) => Promise<
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
    'setCurrentEntry': {
        readonly body: CurrentQueueSetCurrentRequest
    }, 
    'updateCurrentQueueStrategy': {
        readonly body: CurrentQueueStrategyUpdateRequest
    }, 
    'playNextInCurrentQueue': {}, 
    'playPreviousInCurrentQueue': {}, 
    'removeCurrentQueueEntry': {
        readonly entryId: number
    }, 
    'clearCurrentQueue': {}
}
