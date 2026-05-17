import type {Executor} from '../';
import type {PluginInfoResponse} from '../model/static/';

export class PluginController {
    
    constructor(private executor: Executor) {}
    
    readonly delete: (options: PluginControllerOptions['delete']) => Promise<
        void
    > = async(options) => {
        let _uri = '/api/plugins/';
        _uri += encodeURIComponent(options.id);
        return (await this.executor({uri: _uri, method: 'DELETE'})) as Promise<void>;
    }
    
    readonly download: (options: PluginControllerOptions['download']) => Promise<
        void
    > = async(options) => {
        let _uri = '/api/plugins/';
        _uri += encodeURIComponent(options.id);
        _uri += '/download';
        return (await this.executor({uri: _uri, method: 'GET'})) as Promise<void>;
    }
    
    readonly listPlugins: () => Promise<
        ReadonlyArray<PluginInfoResponse>
    > = async() => {
        let _uri = '/api/plugins';
        return (await this.executor({uri: _uri, method: 'GET'})) as Promise<ReadonlyArray<PluginInfoResponse>>;
    }
    
    readonly setEnabled: (options: PluginControllerOptions['setEnabled']) => Promise<
        void
    > = async(options) => {
        let _uri = '/api/plugins/';
        _uri += encodeURIComponent(options.id);
        _uri += '/enabled';
        let _separator = _uri.indexOf('?') === -1 ? '?' : '&';
        let _value: any = undefined;
        _value = options.enabled;
        _uri += _separator
        _uri += 'enabled='
        _uri += encodeURIComponent(_value);
        _separator = '&';
        return (await this.executor({uri: _uri, method: 'PUT'})) as Promise<void>;
    }
    
    readonly submitPluginTask: (options: PluginControllerOptions['submitPluginTask']) => Promise<
        void
    > = async(options) => {
        let _uri = '/api/plugins/';
        _uri += encodeURIComponent(options.taskType);
        _uri += '/submit';
        return (await this.executor({uri: _uri, method: 'POST', body: options.body})) as Promise<void>;
    }
    
    readonly upload: (options: PluginControllerOptions['upload']) => Promise<
        void
    > = async(options) => {
        let _uri = '/api/plugins/upload';
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
        readonly id: string, 
        readonly enabled: boolean
    }, 
    'delete': {
        readonly id: string
    }, 
    'download': {
        readonly id: string
    }, 
    'submitPluginTask': {
        readonly taskType: string, 
        readonly body: {readonly [key:string]: string}
    }
}
