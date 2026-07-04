import type {Executor} from '../';
import type {PluginInfoResponse} from '../model/static/';

/**
 * 插件管理接口
 * 
 * 提供插件的上传、启停、删除、导出与插件任务提交能力
 */
export class PluginController {
    
    constructor(private executor: Executor) {}
    
    /**
     * 删除插件
     * 
     * 此接口用于删除指定 ID 的插件
     * 需要用户登录认证才能访问
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
     * 此接口用于将指定插件打包为 `.up` 文件并以附件形式下载
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
     * 此接口用于获取系统中已安装的全部插件信息，并标记插件是否已加载可用
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
     * 此接口用于切换指定插件的启用状态
     * 需要用户登录认证才能访问
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
     * 提交插件任务
     * 
     * 此接口用于按任务类型异步触发插件任务，参数以键值对方式透传给对应插件
     * 需要用户登录认证才能访问
     * 
     * @parameter {PluginControllerOptions['submitPluginTask']} options
     * - taskType 任务类型（对应 TaskType 枚举值）
     * - params 任务参数键值对
     * 
     */
    readonly submitPluginTask: (options: PluginControllerOptions['submitPluginTask']) => Promise<
        void
    > = async(options) => {
        let _uri = '/api/plugin-task-submissions/';
        _uri += encodeURIComponent(options.taskType);
        return (await this.executor({uri: _uri, method: 'POST', body: options.body})) as Promise<void>;
    }
    
    /**
     * 上传插件
     * 
     * 此接口用于上传 `.up` 插件包并完成安装
     * 需要用户登录认证才能访问
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
    }, 
    'submitPluginTask': {
        /**
         * 任务类型（对应 TaskType 枚举值）
         */
        readonly taskType: string, 
        /**
         * 任务参数键值对
         * 
         */
        readonly body: {readonly [key:string]: string}
    }
}
