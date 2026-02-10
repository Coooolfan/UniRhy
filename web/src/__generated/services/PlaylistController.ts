import type {Executor} from '../';
import type {PlaylistDto} from '../model/dto/';
import type {PlaylistCreate, PlaylistUpdate} from '../model/static/';

export class PlaylistController {
    
    constructor(private executor: Executor) {}
    
    /**
     * 创建播放列表
     * 
     * 此接口用于创建新的播放列表
     * 需要用户登录认证才能访问
     * 
     * @parameter {PlaylistControllerOptions['createPlaylist']} options
     * - input 播放列表创建参数
     * @return Playlist 返回创建后的播放列表（默认 fetcher）
     * 
     */
    readonly createPlaylist: (options: PlaylistControllerOptions['createPlaylist']) => Promise<
        PlaylistDto['PlaylistController/DEFAULT_PLAYLIST_FETCHER']
    > = async(options) => {
        let _uri = '/api/playlist';
        return (await this.executor({uri: _uri, method: 'POST', body: options.body})) as Promise<PlaylistDto['PlaylistController/DEFAULT_PLAYLIST_FETCHER']>;
    }
    
    /**
     * 删除播放列表
     * 
     * 此接口用于删除指定 ID 的播放列表
     * 需要用户登录认证才能访问
     * 
     * @parameter {PlaylistControllerOptions['deletePlaylist']} options
     * - id 播放列表 ID
     * 
     */
    readonly deletePlaylist: (options: PlaylistControllerOptions['deletePlaylist']) => Promise<
        void
    > = async(options) => {
        let _uri = '/api/playlist/';
        _uri += encodeURIComponent(options.id);
        return (await this.executor({uri: _uri, method: 'DELETE'})) as Promise<void>;
    }
    
    /**
     * 获取播放列表详情
     * 
     * 此接口用于根据播放列表 ID 查询播放列表详情
     * 需要用户登录认证才能访问
     * 
     * @parameter {PlaylistControllerOptions['getPlaylist']} options
     * - id 播放列表 ID
     * @return Playlist 返回播放列表详情（详细 fetcher）
     * 
     */
    readonly getPlaylist: (options: PlaylistControllerOptions['getPlaylist']) => Promise<
        PlaylistDto['PlaylistController/DETAIL_PLAYLIST_FETCHER']
    > = async(options) => {
        let _uri = '/api/playlist/';
        _uri += encodeURIComponent(options.id);
        return (await this.executor({uri: _uri, method: 'GET'})) as Promise<PlaylistDto['PlaylistController/DETAIL_PLAYLIST_FETCHER']>;
    }
    
    /**
     * 获取播放列表列表
     * 
     * 此接口用于获取系统中所有播放列表信息
     * 需要用户登录认证才能访问
     * 
     * @return List<Playlist> 返回播放列表集合（默认 fetcher）
     * 
     */
    readonly listPlaylists: () => Promise<
        ReadonlyArray<PlaylistDto['PlaylistController/DEFAULT_PLAYLIST_FETCHER']>
    > = async() => {
        let _uri = '/api/playlist';
        return (await this.executor({uri: _uri, method: 'GET'})) as Promise<ReadonlyArray<PlaylistDto['PlaylistController/DEFAULT_PLAYLIST_FETCHER']>>;
    }
    
    /**
     * 更新播放列表
     * 
     * 此接口用于更新指定 ID 的播放列表信息
     * 需要用户登录认证才能访问
     * 
     * @parameter {PlaylistControllerOptions['updatePlaylist']} options
     * - id 播放列表 ID
     * - input 播放列表更新参数
     * @return Playlist 返回更新后的播放列表（默认 fetcher）
     * 
     */
    readonly updatePlaylist: (options: PlaylistControllerOptions['updatePlaylist']) => Promise<
        PlaylistDto['PlaylistController/DEFAULT_PLAYLIST_FETCHER']
    > = async(options) => {
        let _uri = '/api/playlist/';
        _uri += encodeURIComponent(options.id);
        return (await this.executor({uri: _uri, method: 'PUT', body: options.body})) as Promise<PlaylistDto['PlaylistController/DEFAULT_PLAYLIST_FETCHER']>;
    }
}

export type PlaylistControllerOptions = {
    'listPlaylists': {}, 
    'getPlaylist': {
        /**
         * 播放列表 ID
         */
        readonly id: number
    }, 
    'updatePlaylist': {
        /**
         * 播放列表 ID
         */
        readonly id: number, 
        /**
         * 播放列表更新参数
         */
        readonly body: PlaylistUpdate
    }, 
    'createPlaylist': {
        /**
         * 播放列表创建参数
         */
        readonly body: PlaylistCreate
    }, 
    'deletePlaylist': {
        /**
         * 播放列表 ID
         * 
         */
        readonly id: number
    }
}
