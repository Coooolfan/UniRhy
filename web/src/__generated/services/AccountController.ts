import type {Executor} from '../';
import type {AccountDto} from '../model/dto/';
import type {AccountCreate, AccountUpdate} from '../model/static/';

/**
 * 账户管理接口
 * 
 * 提供账户的增删改查与当前登录账户信息获取能力
 */
export class AccountController {
    
    constructor(private executor: Executor) {}
    
    /**
     * 创建账户
     * 
     * 此接口用于创建新账户
     * 需要用户登录认证才能访问
     * 
     * @parameter {AccountControllerOptions['create']} options
     * - create 创建参数
     * @return Account 返回创建后的账户（默认 fetcher）
     * 
     */
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
    
    /**
     * 删除指定账户
     * 
     * 此接口用于删除指定ID的账户
     * 需要用户登录认证才能访问
     * 
     * @parameter {AccountControllerOptions['delete']} options
     * - id 账户 ID
     * 
     */
    readonly delete: (options: AccountControllerOptions['delete']) => Promise<
        void
    > = async(options) => {
        let _uri = '/api/account/';
        _uri += encodeURIComponent(options.id);
        return (await this.executor({uri: _uri, method: 'DELETE'})) as Promise<void>;
    }
    
    /**
     * 获取账户列表
     * 
     * 此接口用于获取系统中所有账户信息
     * 需要用户登录认证才能访问
     * 
     * @return List<Account> 返回账户列表（默认 fetcher）
     * 
     */
    readonly list: () => Promise<
        ReadonlyArray<AccountDto['AccountController/DEFAULT_ACCOUNT_FETCHER']>
    > = async() => {
        let _uri = '/api/account';
        return (await this.executor({uri: _uri, method: 'GET'})) as Promise<ReadonlyArray<AccountDto['AccountController/DEFAULT_ACCOUNT_FETCHER']>>;
    }
    
    /**
     * 获取当前登录账户信息
     * 
     * 此接口用于获取当前会话对应的账户详情
     * 需要用户登录认证才能访问
     * 
     * @return Account 返回当前账户信息（默认 fetcher）
     * 
     */
    readonly me: () => Promise<
        AccountDto['AccountController/DEFAULT_ACCOUNT_FETCHER']
    > = async() => {
        let _uri = '/api/account/me';
        return (await this.executor({uri: _uri, method: 'GET'})) as Promise<AccountDto['AccountController/DEFAULT_ACCOUNT_FETCHER']>;
    }
    
    /**
     * 更新指定账户
     * 
     * 此接口用于更新指定ID的账户信息
     * 需要用户登录认证才能访问
     * 
     * @parameter {AccountControllerOptions['update']} options
     * - id 账户 ID
     * - update 更新参数
     * @return Account 返回更新后的账户（默认 fetcher）
     * 
     */
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
        /**
         * 账户 ID
         * 
         */
        readonly id: number
    }, 
    'create': {
        /**
         * 创建参数
         */
        readonly create: AccountCreate
    }, 
    'update': {
        /**
         * 账户 ID
         */
        readonly id: number, 
        /**
         * 更新参数
         */
        readonly update: AccountUpdate
    }
}
