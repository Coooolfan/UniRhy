import type {Executor} from '../';
import type {ArtistDto} from '../model/dto/';

export class ArtistController {
    
    constructor(private executor: Executor) {}
    
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
        let _uri = '/api/artists/search';
        let _separator = _uri.indexOf('?') === -1 ? '?' : '&';
        let _value: any = undefined;
        _value = options.name;
        _uri += _separator
        _uri += 'name='
        _uri += encodeURIComponent(_value);
        _separator = '&';
        return (await this.executor({uri: _uri, method: 'GET'})) as Promise<ReadonlyArray<ArtistDto['ArtistController/DEFAULT_ARTIST_FETCHER']>>;
    }
}

export type ArtistControllerOptions = {
    'getArtistByName': {
        /**
         * 艺术家名称
         */
        readonly name: string
    }
}
