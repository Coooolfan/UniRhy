import type {Executor} from '../';
import type {SystemConfigDto} from '../model/dto/';
import type {SystemConfigCreate, SystemConfigUpdate} from '../model/static/';

/**
 * 系统设置管理接口
 * 
 * 提供系统配置的增删改查能力
 */
export class SystemConfigController {
    
    constructor(private executor: Executor) {}
    
    /**
     * 创建系统配置
     * 
     * 此接口用于创建系统配置（单例）
     * 需要用户登录认证才能访问
     * 
     * @parameter {SystemConfigControllerOptions['create']} options
     * - create 创建参数
     * @return SystemConfig 返回创建后的系统配置（默认 fetcher）
     * 
     */
    readonly create: (options: SystemConfigControllerOptions['create']) => Promise<
        SystemConfigDto['SystemConfigController/DEFAULT_SYSTEM_CONFIG_FETCHER']
    > = async(options) => {
        let _uri = '/api/system/config';
        let _separator = _uri.indexOf('?') === -1 ? '?' : '&';
        let _value: any = undefined;
        _value = options.create.ossProviderId;
        if (_value !== undefined && _value !== null) {
            _uri += _separator
            _uri += 'ossProviderId='
            _uri += encodeURIComponent(_value);
            _separator = '&';
        }
        _value = options.create.fsProviderId;
        if (_value !== undefined && _value !== null) {
            _uri += _separator
            _uri += 'fsProviderId='
            _uri += encodeURIComponent(_value);
            _separator = '&';
        }
        return (await this.executor({uri: _uri, method: 'POST'})) as Promise<SystemConfigDto['SystemConfigController/DEFAULT_SYSTEM_CONFIG_FETCHER']>;
    }
    
    /**
     * 获取系统配置
     * 
     * 此接口用于获取当前系统配置
     * 需要用户登录认证才能访问
     * 
     * @return SystemConfig 返回系统配置（默认 fetcher）
     * 
     */
    readonly get: () => Promise<
        SystemConfigDto['SystemConfigController/DEFAULT_SYSTEM_CONFIG_FETCHER']
    > = async() => {
        let _uri = '/api/system/config';
        return (await this.executor({uri: _uri, method: 'GET'})) as Promise<SystemConfigDto['SystemConfigController/DEFAULT_SYSTEM_CONFIG_FETCHER']>;
    }
    
    /**
     * 更新系统配置
     * 
     * 此接口用于更新系统配置（单例）
     * 需要用户登录认证才能访问
     * 
     * @parameter {SystemConfigControllerOptions['update']} options
     * - update 更新参数
     * @return SystemConfig 返回更新后的系统配置（默认 fetcher）
     * 
     */
    readonly update: (options: SystemConfigControllerOptions['update']) => Promise<
        SystemConfigDto['SystemConfigController/DEFAULT_SYSTEM_CONFIG_FETCHER']
    > = async(options) => {
        let _uri = '/api/system/config';
        let _separator = _uri.indexOf('?') === -1 ? '?' : '&';
        let _value: any = undefined;
        _value = options.update.ossProviderId;
        if (_value !== undefined && _value !== null) {
            _uri += _separator
            _uri += 'ossProviderId='
            _uri += encodeURIComponent(_value);
            _separator = '&';
        }
        _value = options.update.fsProviderId;
        if (_value !== undefined && _value !== null) {
            _uri += _separator
            _uri += 'fsProviderId='
            _uri += encodeURIComponent(_value);
            _separator = '&';
        }
        return (await this.executor({uri: _uri, method: 'PUT'})) as Promise<SystemConfigDto['SystemConfigController/DEFAULT_SYSTEM_CONFIG_FETCHER']>;
    }
}

export type SystemConfigControllerOptions = {
    'get': {}, 
    'create': {
        /**
         * 创建参数
         */
        readonly create: SystemConfigCreate
    }, 
    'update': {
        /**
         * 更新参数
         */
        readonly update: SystemConfigUpdate
    }
}
