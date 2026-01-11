import type {Executor} from '../';

export class TokenController {
    
    constructor(private executor: Executor) {}
    
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
    
    readonly logout: () => Promise<
        void
    > = async() => {
        const _uri = '/api/token';
        return (await this.executor({uri: _uri, method: 'DELETE'})) as Promise<void>;
    }
}

export type TokenControllerOptions = {
    'login': {
        readonly email: string, 
        readonly password: string
    }, 
    'logout': {}
}
