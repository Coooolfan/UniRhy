import type {Executor} from '../';
import type {Dynamic_Album} from '../model/dynamic/';

/**
 * 专辑管理接口
 * 
 * 提供专辑列表查询能力
 */
export class AlbumController {
    
    constructor(private executor: Executor) {}
    
    /**
     * 获取专辑列表
     * 
     * 此接口用于获取系统中所有专辑信息
     * 需要用户登录认证才能访问
     * 
     * @return List<Album> 返回专辑列表（默认 fetcher）
     * 
     */
    readonly listAlbums: () => Promise<
        ReadonlyArray<Dynamic_Album>
    > = async() => {
        let _uri = '/api/album';
        return (await this.executor({uri: _uri, method: 'GET'})) as Promise<ReadonlyArray<Dynamic_Album>>;
    }
}

export type AlbumControllerOptions = {
    'listAlbums': {}
}
