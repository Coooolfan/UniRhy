import type {Executor} from '../';
import type {ArtistDto} from '../model/dto/';
import type {
    ArtistCreate, 
    ArtistMergeReq, 
    ArtistUpdate, 
    Page
} from '../model/static/';

/**
 * 艺术家管理接口
 * 
 * 提供艺术家的分页查询、按名搜索、创建、更新与合并能力
 */
export class ArtistController {
    
    constructor(private executor: Executor) {}
    
    /**
     * 创建艺术家
     * 
     * @parameter {ArtistControllerOptions['createArtist']} options
     * - input 艺术家创建参数
     * @return Artist 返回创建后的艺术家（默认 fetcher）
     * 
     */
    readonly createArtist: (options: ArtistControllerOptions['createArtist']) => Promise<
        ArtistDto['ArtistController/DEFAULT_ARTIST_FETCHER']
    > = async(options) => {
        let _uri = '/api/artists';
        let _separator = _uri.indexOf('?') === -1 ? '?' : '&';
        let _value: any = undefined;
        _value = options.copyAssociationsFrom;
        if (_value !== undefined && _value !== null) {
            _uri += _separator
            _uri += 'copyAssociationsFrom='
            _uri += encodeURIComponent(_value);
            _separator = '&';
        }
        return (await this.executor({uri: _uri, method: 'POST', body: options.body})) as Promise<ArtistDto['ArtistController/DEFAULT_ARTIST_FETCHER']>;
    }
    
    /**
     * 根据艺术家名称搜索
     * 
     * @parameter {ArtistControllerOptions['getArtistByName']} options
     * - name 艺术家名称
     * @return List<@FetchBy("DEFAULT_ARTIST_FETCHER") Artist> 返回艺术家列表
     * 
     */
    readonly getArtistByName: (options: ArtistControllerOptions['getArtistByName']) => Promise<
        ReadonlyArray<ArtistDto['ArtistController/DEFAULT_ARTIST_FETCHER']>
    > = async(options) => {
        let _uri = '/api/artists/search-results';
        let _separator = _uri.indexOf('?') === -1 ? '?' : '&';
        let _value: any = undefined;
        _value = options.name;
        _uri += _separator
        _uri += 'name='
        _uri += encodeURIComponent(_value);
        _separator = '&';
        return (await this.executor({uri: _uri, method: 'GET'})) as Promise<ReadonlyArray<ArtistDto['ArtistController/DEFAULT_ARTIST_FETCHER']>>;
    }
    
    /**
     * 获取艺术家分页列表
     * 
     * @parameter {ArtistControllerOptions['listArtists']} options
     * - pageIndex 页码（从 0 开始）
     * - pageSize 每页条数
     * @return Page<Artist> 返回艺术家分页列表（默认 fetcher）
     * 
     */
    readonly listArtists: (options: ArtistControllerOptions['listArtists']) => Promise<
        Page<ArtistDto['ArtistController/DEFAULT_ARTIST_FETCHER']>
    > = async(options) => {
        let _uri = '/api/artists';
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
        return (await this.executor({uri: _uri, method: 'GET'})) as Promise<Page<ArtistDto['ArtistController/DEFAULT_ARTIST_FETCHER']>>;
    }
    
    /**
     * 合并艺术家
     * 
     * @parameter {ArtistControllerOptions['mergeArtists']} options
     * - input 合并请求体
     * 
     */
    readonly mergeArtists: (options: ArtistControllerOptions['mergeArtists']) => Promise<
        void
    > = async(options) => {
        let _uri = '/api/artists/merge-requests';
        return (await this.executor({uri: _uri, method: 'POST', body: options.body})) as Promise<void>;
    }
    
    /**
     * 更新艺术家
     * 
     * 按请求体内提供的字段更新指定艺术家的标量信息
     * 
     * @parameter {ArtistControllerOptions['updateArtist']} options
     * - id 艺术家 ID
     * - input 艺术家更新参数
     * @return Artist 返回更新后的艺术家（默认 fetcher）
     * 
     */
    readonly updateArtist: (options: ArtistControllerOptions['updateArtist']) => Promise<
        ArtistDto['ArtistController/DEFAULT_ARTIST_FETCHER']
    > = async(options) => {
        let _uri = '/api/artists/';
        _uri += encodeURIComponent(options.id);
        return (await this.executor({uri: _uri, method: 'PUT', body: options.body})) as Promise<ArtistDto['ArtistController/DEFAULT_ARTIST_FETCHER']>;
    }
}

export type ArtistControllerOptions = {
    'listArtists': {
        /**
         * 页码（从 0 开始）
         */
        readonly pageIndex?: number | undefined, 
        /**
         * 每页条数
         */
        readonly pageSize?: number | undefined
    }, 
    'getArtistByName': {
        /**
         * 艺术家名称
         */
        readonly name: string
    }, 
    'createArtist': {
        /**
         * 艺术家创建参数
         */
        readonly body: ArtistCreate, 
        readonly copyAssociationsFrom?: number | undefined
    }, 
    'updateArtist': {
        /**
         * 艺术家 ID
         */
        readonly id: number, 
        /**
         * 艺术家更新参数
         */
        readonly body: ArtistUpdate
    }, 
    'mergeArtists': {
        /**
         * 合并请求体
         * 
         */
        readonly body: ArtistMergeReq
    }
}
