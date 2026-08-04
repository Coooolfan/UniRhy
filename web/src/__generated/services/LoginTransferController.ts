import type {Executor} from '../';
import type {LoginTransferDto} from '../model/dto/';
import type {
    LoginTransferClaimRequest, 
    LoginTransferCreateResponse, 
    LoginTransferUpdateRequest, 
    LoginTransferUpdateResponse, 
    TokenLoginResponse
} from '../model/static/';

export class LoginTransferController {
    
    constructor(private executor: Executor) {}
    
    readonly cancel: (options: LoginTransferControllerOptions['cancel']) => Promise<
        void
    > = async(options) => {
        let _uri = '/api/login-transfers/';
        _uri += encodeURIComponent(options.id);
        return (await this.executor({uri: _uri, method: 'DELETE'})) as Promise<void>;
    }
    
    /**
     * 新设备认领登录交接
     * 
     * 二维码密钥同时是查询索引与凭据，因此不需要路径参数；成功后一次性下发认领访问令牌。
     */
    readonly claim: (options: LoginTransferControllerOptions['claim']) => Promise<
        LoginTransferUpdateResponse
    > = async(options) => {
        let _uri = '/api/login-transfers/claims';
        return (await this.executor({uri: _uri, method: 'POST', body: options.body})) as Promise<LoginTransferUpdateResponse>;
    }
    
    readonly create: () => Promise<
        LoginTransferCreateResponse
    > = async() => {
        let _uri = '/api/login-transfers';
        return (await this.executor({uri: _uri, method: 'POST'})) as Promise<LoginTransferCreateResponse>;
    }
    
    readonly createToken: (options: LoginTransferControllerOptions['createToken']) => Promise<
        TokenLoginResponse
    > = async(options) => {
        let _uri = '/api/login-transfers/';
        _uri += encodeURIComponent(options.id);
        _uri += '/tokens';
        const _headers: {[key:string]: string} = {};
        if (options.authorization) {
            _headers['Authorization'] = options.authorization
        }
        return (await this.executor({uri: _uri, method: 'POST', headers: _headers})) as Promise<TokenLoginResponse>;
    }
    
    /**
     * 查询登录交接状态
     * 
     * 原设备凭账号会话查询，新设备凭认领访问令牌查询，后者只获得完成流程所需的最小状态。
     * 已进入终态（含过期）的交接同样返回 `200`，由调用方读取 `status` 判断。
     */
    readonly get: (options: LoginTransferControllerOptions['get']) => Promise<
        LoginTransferDto['LoginTransferController/SOURCE_TRANSFER_FETCHER']
    > = async(options) => {
        let _uri = '/api/login-transfers/';
        _uri += encodeURIComponent(options.id);
        const _headers: {[key:string]: string} = {};
        if (options.authorization) {
            _headers['Authorization'] = options.authorization
        }
        return (await this.executor({uri: _uri, method: 'GET', headers: _headers})) as Promise<LoginTransferDto['LoginTransferController/SOURCE_TRANSFER_FETCHER']>;
    }
    
    /**
     * 原设备审批：把已认领的交接置为 AUTHORIZED 或 REJECTED。
     */
    readonly update: (options: LoginTransferControllerOptions['update']) => Promise<
        LoginTransferUpdateResponse
    > = async(options) => {
        let _uri = '/api/login-transfers/';
        _uri += encodeURIComponent(options.id);
        const _headers: {[key:string]: string} = {};
        if (options.authorization) {
            _headers['Authorization'] = options.authorization
        }
        return (await this.executor({uri: _uri, method: 'PATCH', headers: _headers, body: options.body})) as Promise<LoginTransferUpdateResponse>;
    }
}

export type LoginTransferControllerOptions = {
    'create': {}, 
    'get': {
        readonly id: string, 
        readonly authorization?: string | undefined
    }, 
    'claim': {
        readonly body: LoginTransferClaimRequest
    }, 
    'update': {
        readonly id: string, 
        readonly body: LoginTransferUpdateRequest, 
        readonly authorization?: string | undefined
    }, 
    'cancel': {
        readonly id: string
    }, 
    'createToken': {
        readonly id: string, 
        readonly authorization?: string | undefined
    }
}
