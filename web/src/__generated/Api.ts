import type {Executor} from './';
import {AccountController, TokenController} from './services/';

export class Api {
    
    readonly accountController: AccountController
    
    readonly tokenController: TokenController
    
    constructor(executor: Executor) {
        this.accountController = new AccountController(executor);
        this.tokenController = new TokenController(executor);
    }
}