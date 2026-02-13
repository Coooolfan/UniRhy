import type {Executor} from '../';
import type {AlbumDto} from '../model/dto/';
import type {Page} from '../model/static/';

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
     * 根据专辑 ID 获取完整专辑信息（包含曲目、资源、艺人和封面等）
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
    }
}
