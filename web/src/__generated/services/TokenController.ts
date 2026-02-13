import type {Executor} from '../';
import type {TokenLoginRequest} from '../model/static/';

/**
 * 登录与登出接口
 * 
 * 提供账户登录与退出能力
 */
export class TokenController {
    
    constructor(private executor: Executor) {}
    
    /**
     * 登录并创建会话
     * 
     * 此接口用于校验账户凭据并创建登录会话
     * 无需登录认证即可访问
     * 
     * @parameter {TokenControllerOptions['login']} options
     * - request 登录请求参数
     * 
     */
    readonly login: (options: TokenControllerOptions['login']) => Promise<
        void
    > = async(options) => {
        let _uri = '/api/tokens';
        return (await this.executor({uri: _uri, method: 'POST', body: options.body})) as Promise<void>;
    }
    
    /**
     * 退出当前会话
     * 
     * 此接口用于注销当前登录会话
     * 需要用户登录认证才能访问
     * 
     */
    readonly logout: () => Promise<
        void
    > = async() => {
        let _uri = '/api/tokens/current';
        return (await this.executor({uri: _uri, method: 'DELETE'})) as Promise<void>;
    }
}

export type TokenControllerOptions = {
    'login': {
        /**
         * 登录请求参数
         * 
         */
        readonly body: TokenLoginRequest
    }, 
    'logout': {}
}
