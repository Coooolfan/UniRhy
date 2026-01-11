import type {Executor} from '../';
import type {AccountDto} from '../model/dto/';
import type {AccountCreate, AccountUpdate} from '../model/static/';

export class AccountController {
    
    constructor(private executor: Executor) {}
    
    readonly create: (options: AccountControllerOptions['create']) => Promise<
        AccountDto['AccountController/DEFAULT_ACCOUNT_FETCHER']
    > = async(options) => {
        let _uri = '/api/account';
        let _separator = _uri.indexOf('?') === -1 ? '?' : '&';
        let _value: any = undefined;
        _value = options.create.name;
        _uri += _separator
        _uri += 'name='
        _uri += encodeURIComponent(_value);
        _separator = '&';
        _value = options.create.password;
        _uri += _separator
        _uri += 'password='
        _uri += encodeURIComponent(_value);
        _separator = '&';
        _value = options.create.email;
        _uri += _separator
        _uri += 'email='
        _uri += encodeURIComponent(_value);
        _separator = '&';
        return (await this.executor({uri: _uri, method: 'POST'})) as Promise<AccountDto['AccountController/DEFAULT_ACCOUNT_FETCHER']>;
    }
    
    readonly createFirst: (options: AccountControllerOptions['createFirst']) => Promise<
        AccountDto['AccountController/DEFAULT_ACCOUNT_FETCHER']
    > = async(options) => {
        let _uri = '/api/account/first';
        let _separator = _uri.indexOf('?') === -1 ? '?' : '&';
        let _value: any = undefined;
        _value = options.create.name;
        _uri += _separator
        _uri += 'name='
        _uri += encodeURIComponent(_value);
        _separator = '&';
        _value = options.create.password;
        _uri += _separator
        _uri += 'password='
        _uri += encodeURIComponent(_value);
        _separator = '&';
        _value = options.create.email;
        _uri += _separator
        _uri += 'email='
        _uri += encodeURIComponent(_value);
        _separator = '&';
        return (await this.executor({uri: _uri, method: 'POST'})) as Promise<AccountDto['AccountController/DEFAULT_ACCOUNT_FETCHER']>;
    }
    
    readonly delete: (options: AccountControllerOptions['delete']) => Promise<
        void
    > = async(options) => {
        let _uri = '/api/account/';
        _uri += encodeURIComponent(options.id);
        return (await this.executor({uri: _uri, method: 'DELETE'})) as Promise<void>;
    }
    
    readonly list: () => Promise<
        ReadonlyArray<AccountDto['AccountController/DEFAULT_ACCOUNT_FETCHER']>
    > = async() => {
        let _uri = '/api/account';
        return (await this.executor({uri: _uri, method: 'GET'})) as Promise<ReadonlyArray<AccountDto['AccountController/DEFAULT_ACCOUNT_FETCHER']>>;
    }
    
    readonly me: () => Promise<
        AccountDto['AccountController/DEFAULT_ACCOUNT_FETCHER']
    > = async() => {
        let _uri = '/api/account/me';
        return (await this.executor({uri: _uri, method: 'GET'})) as Promise<AccountDto['AccountController/DEFAULT_ACCOUNT_FETCHER']>;
    }
    
    readonly update: (options: AccountControllerOptions['update']) => Promise<
        AccountDto['AccountController/DEFAULT_ACCOUNT_FETCHER']
    > = async(options) => {
        let _uri = '/api/account/';
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
        _value = options.update.password;
        if (_value !== undefined && _value !== null) {
            _uri += _separator
            _uri += 'password='
            _uri += encodeURIComponent(_value);
            _separator = '&';
        }
        _value = options.update.email;
        if (_value !== undefined && _value !== null) {
            _uri += _separator
            _uri += 'email='
            _uri += encodeURIComponent(_value);
            _separator = '&';
        }
        return (await this.executor({uri: _uri, method: 'PUT'})) as Promise<AccountDto['AccountController/DEFAULT_ACCOUNT_FETCHER']>;
    }
}

export type AccountControllerOptions = {
    'list': {}, 
    'me': {}, 
    'delete': {
        readonly id: number
    }, 
    'create': {
        readonly create: AccountCreate
    }, 
    'createFirst': {
        readonly create: AccountCreate
    }, 
    'update': {
        readonly id: number, 
        readonly update: AccountUpdate
    }
}
