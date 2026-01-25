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
        let _separator = _uri.indexOf('?') === -1 ? '?' : '&';
        let _value: any = undefined;
        _value = options.create.name;
        _uri += _separator
        _uri += 'name='
        _uri += encodeURIComponent(_value);
        _separator = '&';
        _value = options.create.host;
        _uri += _separator
        _uri += 'host='
        _uri += encodeURIComponent(_value);
        _separator = '&';
        _value = options.create.bucket;
        _uri += _separator
        _uri += 'bucket='
        _uri += encodeURIComponent(_value);
        _separator = '&';
        _value = options.create.accessKey;
        _uri += _separator
        _uri += 'accessKey='
        _uri += encodeURIComponent(_value);
        _separator = '&';
        _value = options.create.secretKey;
        _uri += _separator
        _uri += 'secretKey='
        _uri += encodeURIComponent(_value);
        _separator = '&';
        _value = options.create.parentPath;
        if (_value !== undefined && _value !== null) {
            _uri += _separator
            _uri += 'parentPath='
            _uri += encodeURIComponent(_value);
            _separator = '&';
        }
        _value = options.create.readonly;
        _uri += _separator
        _uri += 'readonly='
        _uri += encodeURIComponent(_value);
        _separator = '&';
        return (await this.executor({uri: _uri, method: 'POST'})) as Promise<FileProviderOssDto['OssStorageController/DEFAULT_OSS_FETCHER']>;
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
        let _separator = _uri.indexOf('?') === -1 ? '?' : '&';
        let _value: any = undefined;
        _value = options.update.name;
        if (_value !== undefined && _value !== null) {
            _uri += _separator
            _uri += 'name='
            _uri += encodeURIComponent(_value);
            _separator = '&';
        }
        _value = options.update.host;
        if (_value !== undefined && _value !== null) {
            _uri += _separator
            _uri += 'host='
            _uri += encodeURIComponent(_value);
            _separator = '&';
        }
        _value = options.update.bucket;
        if (_value !== undefined && _value !== null) {
            _uri += _separator
            _uri += 'bucket='
            _uri += encodeURIComponent(_value);
            _separator = '&';
        }
        _value = options.update.accessKey;
        if (_value !== undefined && _value !== null) {
            _uri += _separator
            _uri += 'accessKey='
            _uri += encodeURIComponent(_value);
            _separator = '&';
        }
        _value = options.update.secretKey;
        if (_value !== undefined && _value !== null) {
            _uri += _separator
            _uri += 'secretKey='
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
        return (await this.executor({uri: _uri, method: 'PUT'})) as Promise<FileProviderOssDto['OssStorageController/DEFAULT_OSS_FETCHER']>;
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
        readonly create: FileProviderOssCreate
    }, 
    'update': {
        /**
         * 存储配置 ID
         */
        readonly id: number, 
        /**
         * 更新参数
         */
        readonly update: FileProviderOssUpdate
    }, 
    'delete': {
        /**
         * 存储配置 ID
         * 
         */
        readonly id: number
    }
}
