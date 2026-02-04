import type {Executor} from '../';
import type {FileProviderFileSystemDto} from '../model/dto/';
import type {FileProviderFileSystemCreate, FileProviderFileSystemUpdate} from '../model/static/';

/**
 * 文件系统存储管理接口
 * 
 * 提供文件系统存储配置的增删改查能力
 */
export class FileSystemStorageController {
    
    constructor(private executor: Executor) {}
    
    /**
     * 创建文件系统存储配置
     * 
     * 此接口用于创建新的文件系统存储配置
     * 需要用户登录认证才能访问
     * 
     * @parameter {FileSystemStorageControllerOptions['create']} options
     * - create 创建参数
     * @return FileProviderFileSystem 返回创建后的文件系统存储配置（默认 fetcher）
     * 
     */
    readonly create: (options: FileSystemStorageControllerOptions['create']) => Promise<
        FileProviderFileSystemDto['FileSystemStorageController/DEFAULT_FILE_SYSTEM_FETCHER']
    > = async(options) => {
        let _uri = '/api/storage/fs';
        return (await this.executor({uri: _uri, method: 'POST', body: options.body})) as Promise<FileProviderFileSystemDto['FileSystemStorageController/DEFAULT_FILE_SYSTEM_FETCHER']>;
    }
    
    /**
     * 删除指定文件系统存储配置
     * 
     * 此接口用于删除指定ID的文件系统存储配置
     * 需要用户登录认证才能访问
     * 
     * @parameter {FileSystemStorageControllerOptions['delete']} options
     * - id 存储配置 ID
     * 
     */
    readonly delete: (options: FileSystemStorageControllerOptions['delete']) => Promise<
        void
    > = async(options) => {
        let _uri = '/api/storage/fs/';
        _uri += encodeURIComponent(options.id);
        return (await this.executor({uri: _uri, method: 'DELETE'})) as Promise<void>;
    }
    
    /**
     * 获取指定文件系统存储配置
     * 
     * 此接口用于获取指定ID的文件系统存储配置详情
     * 需要用户登录认证才能访问
     * 
     * @parameter {FileSystemStorageControllerOptions['get']} options
     * - id 存储配置 ID
     * @return FileProviderFileSystem 返回文件系统存储配置（默认 fetcher）
     * 
     */
    readonly get: (options: FileSystemStorageControllerOptions['get']) => Promise<
        FileProviderFileSystemDto['FileSystemStorageController/DEFAULT_FILE_SYSTEM_FETCHER']
    > = async(options) => {
        let _uri = '/api/storage/fs/';
        _uri += encodeURIComponent(options.id);
        return (await this.executor({uri: _uri, method: 'GET'})) as Promise<FileProviderFileSystemDto['FileSystemStorageController/DEFAULT_FILE_SYSTEM_FETCHER']>;
    }
    
    /**
     * 获取文件系统存储列表
     * 
     * 此接口用于获取所有文件系统存储配置
     * 需要用户登录认证才能访问
     * 
     * @return List<FileProviderFileSystem> 返回文件系统存储列表（默认 fetcher）
     * 
     */
    readonly list: () => Promise<
        ReadonlyArray<FileProviderFileSystemDto['FileSystemStorageController/DEFAULT_FILE_SYSTEM_FETCHER']>
    > = async() => {
        let _uri = '/api/storage/fs';
        return (await this.executor({uri: _uri, method: 'GET'})) as Promise<ReadonlyArray<FileProviderFileSystemDto['FileSystemStorageController/DEFAULT_FILE_SYSTEM_FETCHER']>>;
    }
    
    /**
     * 更新指定文件系统存储配置
     * 
     * 此接口用于更新指定ID的文件系统存储配置信息
     * 需要用户登录认证才能访问
     * 
     * @parameter {FileSystemStorageControllerOptions['update']} options
     * - id 存储配置 ID
     * - update 更新参数
     * @return FileProviderFileSystem 返回更新后的文件系统存储配置（默认 fetcher）
     * 
     */
    readonly update: (options: FileSystemStorageControllerOptions['update']) => Promise<
        FileProviderFileSystemDto['FileSystemStorageController/DEFAULT_FILE_SYSTEM_FETCHER']
    > = async(options) => {
        let _uri = '/api/storage/fs/';
        _uri += encodeURIComponent(options.id);
        let _separator = _uri.indexOf('?') === -1 ? '?' : '&';
        let _value: any = undefined;
        _value = options.update.name;
        if (_value !== undefined && _value !== null) {
            _uri += _separator
            _uri += 'name='
            _uri += encodeURIComponent(_value);
            _separator = '&';
        }
        _value = options.update.parentPath;
        if (_value !== undefined && _value !== null) {
            _uri += _separator
            _uri += 'parentPath='
            _uri += encodeURIComponent(_value);
            _separator = '&';
        }
        _value = options.update.readonly;
        _uri += _separator
        _uri += 'readonly='
        _uri += encodeURIComponent(_value);
        _separator = '&';
        return (await this.executor({uri: _uri, method: 'PUT'})) as Promise<FileProviderFileSystemDto['FileSystemStorageController/DEFAULT_FILE_SYSTEM_FETCHER']>;
    }
}

export type FileSystemStorageControllerOptions = {
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
        readonly body: FileProviderFileSystemCreate
    }, 
    'update': {
        /**
         * 存储配置 ID
         */
        readonly id: number, 
        /**
         * 更新参数
         */
        readonly update: FileProviderFileSystemUpdate
    }, 
    'delete': {
        /**
         * 存储配置 ID
         * 
         */
        readonly id: number
    }
}
