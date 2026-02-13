import type {Executor} from '../';
import type {FileProviderOssDto} from '../model/dto/';
import type {FileProviderOssCreate, FileProviderOssUpdate} from '../model/static/';

/**
 * OSS存储管理接口
 * 
 * 提供OSS存储配置的增删改查能力
 */
export class OssStorageController {
    
    constructor(private executor: Executor) {}
    
    /**
     * 创建OSS存储配置
     * 
     * 此接口用于创建新的OSS存储配置
     * 需要用户登录认证才能访问
     * 
     * @parameter {OssStorageControllerOptions['create']} options
     * - create 创建参数
     * @return FileProviderOss 返回创建后的OSS存储配置（默认 fetcher）
     * 
     */
    readonly create: (options: OssStorageControllerOptions['create']) => Promise<
        FileProviderOssDto['OssStorageController/DEFAULT_OSS_FETCHER']
    > = async(options) => {
        let _uri = '/api/storage/oss';
        return (await this.executor({uri: _uri, method: 'POST', body: options.body})) as Promise<FileProviderOssDto['OssStorageController/DEFAULT_OSS_FETCHER']>;
    }
    
    /**
     * 删除指定OSS存储配置
     * 
     * 此接口用于删除指定ID的OSS存储配置
     * 需要用户登录认证才能访问
     * 
     * @parameter {OssStorageControllerOptions['delete']} options
     * - id 存储配置 ID
     * 
     */
    readonly delete: (options: OssStorageControllerOptions['delete']) => Promise<
        void
    > = async(options) => {
        let _uri = '/api/storage/oss/';
        _uri += encodeURIComponent(options.id);
        return (await this.executor({uri: _uri, method: 'DELETE'})) as Promise<void>;
    }
    
    /**
     * 获取指定OSS存储配置
     * 
     * 此接口用于获取指定ID的OSS存储配置详情
     * 需要用户登录认证才能访问
     * 
     * @parameter {OssStorageControllerOptions['get']} options
     * - id 存储配置 ID
     * @return FileProviderOss 返回OSS存储配置（默认 fetcher）
     * 
     */
    readonly get: (options: OssStorageControllerOptions['get']) => Promise<
        FileProviderOssDto['OssStorageController/DEFAULT_OSS_FETCHER']
    > = async(options) => {
        let _uri = '/api/storage/oss/';
        _uri += encodeURIComponent(options.id);
        return (await this.executor({uri: _uri, method: 'GET'})) as Promise<FileProviderOssDto['OssStorageController/DEFAULT_OSS_FETCHER']>;
    }
    
    /**
     * 获取OSS存储列表
     * 
     * 此接口用于获取所有OSS存储配置
     * 需要用户登录认证才能访问
     * 
     * @return List<FileProviderOss> 返回OSS存储列表（默认 fetcher）
     * 
     */
    readonly list: () => Promise<
        ReadonlyArray<FileProviderOssDto['OssStorageController/DEFAULT_OSS_FETCHER']>
    > = async() => {
        let _uri = '/api/storage/oss';
        return (await this.executor({uri: _uri, method: 'GET'})) as Promise<ReadonlyArray<FileProviderOssDto['OssStorageController/DEFAULT_OSS_FETCHER']>>;
    }
    
    /**
     * 更新指定OSS存储配置
     * 
     * 此接口用于更新指定ID的OSS存储配置信息
     * 需要用户登录认证才能访问
     * 
     * @parameter {OssStorageControllerOptions['update']} options
     * - id 存储配置 ID
     * - update 更新参数
     * @return FileProviderOss 返回更新后的OSS存储配置（默认 fetcher）
     * 
     */
    readonly update: (options: OssStorageControllerOptions['update']) => Promise<
        FileProviderOssDto['OssStorageController/DEFAULT_OSS_FETCHER']
    > = async(options) => {
        let _uri = '/api/storage/oss/';
        _uri += encodeURIComponent(options.id);
        return (await this.executor({uri: _uri, method: 'PUT', body: options.body})) as Promise<FileProviderOssDto['OssStorageController/DEFAULT_OSS_FETCHER']>;
    }
}

export type OssStorageControllerOptions = {
    'list': {}, 
    'get': {
        /**
         * 存储配置 ID
         */
        readonly id: number
    }, 
    'create': {
        /**
         * 创建参数
         */
        readonly body: FileProviderOssCreate
    }, 
    'update': {
        /**
         * 存储配置 ID
         */
        readonly id: number, 
        /**
         * 更新参数
         */
        readonly body: FileProviderOssUpdate
    }, 
    'delete': {
        /**
         * 存储配置 ID
         * 
         */
        readonly id: number
    }
}
