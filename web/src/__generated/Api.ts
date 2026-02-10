import type {Executor} from './';
import {
    AccountController, 
    AlbumController, 
    FileSystemStorageController, 
    MediaFileController, 
    OssStorageController, 
    PlaylistController, 
    SystemConfigController, 
    TaskController, 
    TokenController, 
    WorkController
} from './services/';

export class Api {
    
    readonly accountController: AccountController
    
    readonly albumController: AlbumController
    
    readonly mediaFileController: MediaFileController
    
    readonly playlistController: PlaylistController
    
    readonly systemConfigController: SystemConfigController
    
    readonly taskController: TaskController
    
    readonly tokenController: TokenController
    
    readonly workController: WorkController
    
    readonly fileSystemStorageController: FileSystemStorageController
    
    readonly ossStorageController: OssStorageController
    
    constructor(executor: Executor) {
        this.accountController = new AccountController(executor);
        this.albumController = new AlbumController(executor);
        this.mediaFileController = new MediaFileController(executor);
        this.playlistController = new PlaylistController(executor);
        this.systemConfigController = new SystemConfigController(executor);
        this.taskController = new TaskController(executor);
        this.tokenController = new TokenController(executor);
        this.workController = new WorkController(executor);
        this.fileSystemStorageController = new FileSystemStorageController(executor);
        this.ossStorageController = new OssStorageController(executor);
    }
}