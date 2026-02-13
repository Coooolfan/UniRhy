import type {Executor} from '../';
import type {SystemConfigDto} from '../model/dto/';
import type {SystemConfigUpdate, SystemInitReq, SystemStatus} from '../model/static/';

/**
 * 系统设置管理接口
 * 
 * 提供系统配置的增删改查能力
 */
export class SystemConfigController {
    
    constructor(private executor: Executor) {}
    
    /**
     * 初始化系统
     * 
     * 此接口用于初始化系统
     * 
     * @parameter {SystemConfigControllerOptions['create']} options
     * - create 创建参数
     * 
     */
    readonly create: (options: SystemConfigControllerOptions['create']) => Promise<
        void
    > = async(options) => {
        let _uri = '/api/system/config';
        return (await this.executor({uri: _uri, method: 'POST', body: options.body})) as Promise<void>;
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
     * 获取系统初始化状态
     * 
     * 此接口用于获取系统是否已完成初始化
     * 无需登录认证即可访问
     * 
     * @return SystemStatus 返回系统初始化状态
     * 
     */
    readonly isInitialized: () => Promise<
        SystemStatus
    > = async() => {
        let _uri = '/api/system/config/status';
        return (await this.executor({uri: _uri, method: 'GET'})) as Promise<SystemStatus>;
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
        return (await this.executor({uri: _uri, method: 'PUT', body: options.body})) as Promise<SystemConfigDto['SystemConfigController/DEFAULT_SYSTEM_CONFIG_FETCHER']>;
    }
}

export type SystemConfigControllerOptions = {
    'isInitialized': {}, 
    'get': {}, 
    'create': {
        /**
         * 创建参数
         * 
         */
        readonly body: SystemInitReq
    }, 
    'update': {
        /**
         * 更新参数
         */
        readonly body: SystemConfigUpdate
    }
}
