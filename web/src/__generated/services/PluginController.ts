import type {Executor} from '../';
import type {PluginInfoResponse} from '../model/static/';

/**
 * 插件管理接口
 * 
 * 提供插件的上传、启停、并发调整、删除与导出能力；
 * 任务提交经由统一的 `/api/task-submissions`
 */
export class PluginController {
    
    constructor(private executor: Executor) {}
    
    /**
     * 删除插件
     * 
     * 只允许删除已禁用的插件；存在活动 submission / task 时返回 409
     * 需要管理员角色才能访问
     * 
     * @parameter {PluginControllerOptions['delete']} options
     * - id 插件 ID
     * 
     */
    readonly delete: (options: PluginControllerOptions['delete']) => Promise<
        void
    > = async(options) => {
        let _uri = '/api/plugins/';
        _uri += encodeURIComponent(options.id);
        return (await this.executor({uri: _uri, method: 'DELETE'})) as Promise<void>;
    }
    
    /**
     * 导出（下载）插件包
     * 
     * 此接口用于将指定插件打包为 `.up` 文件并以附件形式下载；
     * manifest 中的 `task.concurrency` 为当前并发值
     * 需要管理员角色才能访问
     * 
     * @parameter {PluginControllerOptions['download']} options
     * - id 插件 ID
     * - response Servlet 响应对象，用于写入二进制内容
     * 
     */
    readonly download: (options: PluginControllerOptions['download']) => Promise<
        void
    > = async(options) => {
        let _uri = '/api/plugins/';
        _uri += encodeURIComponent(options.id);
        _uri += '/package';
        return (await this.executor({uri: _uri, method: 'GET'})) as Promise<void>;
    }
    
    /**
     * 获取插件列表
     * 
     * 此接口用于获取系统中已安装的全部插件信息，并标记插件在本节点是否已加载可用
     * 需要用户登录认证才能访问
     * 
     * @return List<PluginInfoResponse> 返回插件信息列表
     * 
     */
    readonly listPlugins: () => Promise<
        ReadonlyArray<PluginInfoResponse>
    > = async() => {
        let _uri = '/api/plugins';
        return (await this.executor({uri: _uri, method: 'GET'})) as Promise<ReadonlyArray<PluginInfoResponse>>;
    }
    
    /**
     * 启用或禁用插件
     * 
     * 启用前完成 WASM 解析、实例化与导出函数校验；校验失败时插件保持禁用
     * 需要管理员角色才能访问
     * 
     * @parameter {PluginControllerOptions['setEnabled']} options
     * - id 插件 ID
     * - enabled 是否启用
     * 
     */
    readonly setEnabled: (options: PluginControllerOptions['setEnabled']) => Promise<
        void
    > = async(options) => {
        let _uri = '/api/plugins/';
        _uri += encodeURIComponent(options.id);
        _uri += '/enabled-state';
        let _separator = _uri.indexOf('?') === -1 ? '?' : '&';
        let _value: any = undefined;
        _value = options.enabled;
        _uri += _separator
        _uri += 'enabled='
        _uri += encodeURIComponent(_value);
        _separator = '&';
        return (await this.executor({uri: _uri, method: 'PUT'})) as Promise<void>;
    }
    
    /**
     * 修改插件任务执行并发值
     * 
     * 修改后无需重启服务，各节点在下一轮对账时生效
     * 需要管理员角色才能访问
     * 
     * @parameter {PluginControllerOptions['updateConcurrency']} options
     * - id 插件 ID
     * - concurrency 正整数并发值
     * 
     */
    readonly updateConcurrency: (options: PluginControllerOptions['updateConcurrency']) => Promise<
        void
    > = async(options) => {
        let _uri = '/api/plugins/';
        _uri += encodeURIComponent(options.id);
        _uri += '/concurrency';
        let _separator = _uri.indexOf('?') === -1 ? '?' : '&';
        let _value: any = undefined;
        _value = options.concurrency;
        _uri += _separator
        _uri += 'concurrency='
        _uri += encodeURIComponent(_value);
        _separator = '&';
        return (await this.executor({uri: _uri, method: 'PUT'})) as Promise<void>;
    }
    
    /**
     * 上传插件
     * 
     * 此接口用于上传 `.up` 插件包并完成安装；同 id 上传即覆盖升级，上传后保持禁用
     * 需要管理员角色才能访问
     * 
     * @parameter {PluginControllerOptions['upload']} options
     * - file 插件包文件
     * 
     */
    readonly upload: (options: PluginControllerOptions['upload']) => Promise<
        void
    > = async(options) => {
        let _uri = '/api/plugins';
        const _formData = new FormData();
        const _body = options.body;
        _formData.append("file", _body.file);
        return (await this.executor({uri: _uri, method: 'POST', body: _formData})) as Promise<void>;
    }
}

export type PluginControllerOptions = {
    'listPlugins': {}, 
    'upload': {
        readonly body: {
            readonly file: File
        }
    }, 
    'setEnabled': {
        /**
         * 插件 ID
         */
        readonly id: string, 
        /**
         * 是否启用
         * 
         */
        readonly enabled: boolean
    }, 
    'updateConcurrency': {
        /**
         * 插件 ID
         */
        readonly id: string, 
        /**
         * 正整数并发值
         * 
         */
        readonly concurrency: number
    }, 
    'delete': {
        /**
         * 插件 ID
         * 
         */
        readonly id: string
    }, 
    'download': {
        /**
         * 插件 ID
         */
        readonly id: string
    }
}
