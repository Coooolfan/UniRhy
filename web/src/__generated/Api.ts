import type {Executor} from './';
import {
    AccountController, 
    FileSystemStorageController, 
    OssStorageController, 
    SystemConfigController, 
    TaskManagerController, 
    TokenController, 
    WorkController
} from './services/';

export class Api {
    
    readonly accountController: AccountController
    
    readonly systemConfigController: SystemConfigController
    
    readonly taskManagerController: TaskManagerController
    
    readonly tokenController: TokenController
    
    readonly workController: WorkController
    
    readonly fileSystemStorageController: FileSystemStorageController
    
    readonly ossStorageController: OssStorageController
    
    constructor(executor: Executor) {
        this.accountController = new AccountController(executor);
        this.systemConfigController = new SystemConfigController(executor);
        this.taskManagerController = new TaskManagerController(executor);
        this.tokenController = new TokenController(executor);
        this.workController = new WorkController(executor);
        this.fileSystemStorageController = new FileSystemStorageController(executor);
        this.ossStorageController = new OssStorageController(executor);
    }
}