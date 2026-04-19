import type {Executor} from '../';
import type {AlbumDto} from '../model/dto/';
import type {AlbumUpdate, Page, RecordingReorderReq} from '../model/static/';

/**
 * 专辑管理接口
 * 
 * 提供专辑列表查询能力
 */
export class AlbumController {
    
    constructor(private executor: Executor) {}
    
    /**
     * 获取专辑详情
     * 
     * 根据专辑 ID 获取完整专辑信息（包含录音、资源、艺人和封面等）
     * 
     * @parameter {AlbumControllerOptions['getAlbum']} options
     * - id 专辑 ID
     * @return Album 返回专辑详情（使用 DETAIL_ALBUM_FETCHER）
     * 
     */
    readonly getAlbum: (options: AlbumControllerOptions['getAlbum']) => Promise<
        AlbumDto['AlbumController/DETAIL_ALBUM_FETCHER']
    > = async(options) => {
        let _uri = '/api/albums/';
        _uri += encodeURIComponent(options.id);
        return (await this.executor({uri: _uri, method: 'GET'})) as Promise<AlbumDto['AlbumController/DETAIL_ALBUM_FETCHER']>;
    }
    
    /**
     * 根据专辑名称搜索
     * 
     * @parameter {AlbumControllerOptions['getAlbumByName']} options
     * - name 专辑名称
     * @return List<Album> 返回专辑列表（默认 fetcher）
     * 
     */
    readonly getAlbumByName: (options: AlbumControllerOptions['getAlbumByName']) => Promise<
        ReadonlyArray<AlbumDto['AlbumController/DEFAULT_ALBUM_FETCHER']>
    > = async(options) => {
        let _uri = '/api/albums/search';
        let _separator = _uri.indexOf('?') === -1 ? '?' : '&';
        let _value: any = undefined;
        _value = options.name;
        _uri += _separator
        _uri += 'name='
        _uri += encodeURIComponent(_value);
        _separator = '&';
        return (await this.executor({uri: _uri, method: 'GET'})) as Promise<ReadonlyArray<AlbumDto['AlbumController/DEFAULT_ALBUM_FETCHER']>>;
    }
    
    /**
     * 获取专辑列表
     * 
     * 此接口用于获取系统中所有专辑信息
     * 需要用户登录认证才能访问
     * 
     * @return Page<Album> 返回专辑分页列表（默认 fetcher）
     * 
     */
    readonly listAlbums: (options: AlbumControllerOptions['listAlbums']) => Promise<
        Page<AlbumDto['AlbumController/DEFAULT_ALBUM_FETCHER']>
    > = async(options) => {
        let _uri = '/api/albums';
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
        return (await this.executor({uri: _uri, method: 'GET'})) as Promise<Page<AlbumDto['AlbumController/DEFAULT_ALBUM_FETCHER']>>;
    }
    
    /**
     * 调整专辑内录音顺序
     * 
     * 请求体需提供当前专辑中全部录音的 id 列表，按期望顺序排列。
     * 服务端严格校验集合一致性后，按下标重写映射表的 sortOrder。
     * 
     * @parameter {AlbumControllerOptions['reorderAlbumRecordings']} options
     * - id 专辑 ID
     * - input 新顺序下的录音 id 列表
     * 
     */
    readonly reorderAlbumRecordings: (options: AlbumControllerOptions['reorderAlbumRecordings']) => Promise<
        void
    > = async(options) => {
        let _uri = '/api/albums/';
        _uri += encodeURIComponent(options.id);
        _uri += '/recordings/reorder';
        return (await this.executor({uri: _uri, method: 'PUT', body: options.body})) as Promise<void>;
    }
    
    /**
     * 更新专辑
     * 
     * 按请求体内提供的字段更新指定专辑的标量信息
     * 
     * @parameter {AlbumControllerOptions['updateAlbum']} options
     * - id 专辑 ID
     * - input 专辑更新参数
     * @return Album 返回更新后的专辑（默认 fetcher）
     * 
     */
    readonly updateAlbum: (options: AlbumControllerOptions['updateAlbum']) => Promise<
        AlbumDto['AlbumController/DEFAULT_ALBUM_FETCHER']
    > = async(options) => {
        let _uri = '/api/albums/';
        _uri += encodeURIComponent(options.id);
        return (await this.executor({uri: _uri, method: 'PUT', body: options.body})) as Promise<AlbumDto['AlbumController/DEFAULT_ALBUM_FETCHER']>;
    }
}

export type AlbumControllerOptions = {
    'listAlbums': {
        readonly pageIndex?: number | undefined, 
        readonly pageSize?: number | undefined
    }, 
    'getAlbum': {
        /**
         * 专辑 ID
         */
        readonly id: number
    }, 
    'getAlbumByName': {
        /**
         * 专辑名称
         */
        readonly name: string
    }, 
    'updateAlbum': {
        /**
         * 专辑 ID
         */
        readonly id: number, 
        /**
         * 专辑更新参数
         */
        readonly body: AlbumUpdate
    }, 
    'reorderAlbumRecordings': {
        /**
         * 专辑 ID
         */
        readonly id: number, 
        /**
         * 新顺序下的录音 id 列表
         * 
         */
        readonly body: RecordingReorderReq
    }
}
