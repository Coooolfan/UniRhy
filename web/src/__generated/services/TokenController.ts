import type {Executor} from '../';

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
     * - email 登录邮箱
     * - password 登录密码
     * 
     */
    readonly login: (options: TokenControllerOptions['login']) => Promise<
        void
    > = async(options) => {
        let _uri = '/api/token';
        let _separator = _uri.indexOf('?') === -1 ? '?' : '&';
        let _value: any = undefined;
        _value = options.email;
        _uri += _separator
        _uri += 'email='
        _uri += encodeURIComponent(_value);
        _separator = '&';
        _value = options.password;
        _uri += _separator
        _uri += 'password='
        _uri += encodeURIComponent(_value);
        _separator = '&';
        return (await this.executor({uri: _uri, method: 'GET'})) as Promise<void>;
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
        let _uri = '/api/token';
        return (await this.executor({uri: _uri, method: 'DELETE'})) as Promise<void>;
    }
}

export type TokenControllerOptions = {
    'login': {
        /**
         * 登录邮箱
         */
        readonly email: string, 
        /**
         * 登录密码
         * 
         */
        readonly password: string
    }, 
    'logout': {}
}
