import type {Executor} from './';
import {
    AccountController, 
    AlbumController, 
    ArtistController, 
    FileSystemStorageController, 
    MediaFileController, 
    OssStorageController, 
    PlaybackQueueController, 
    PlaylistController, 
    PluginController, 
    RecordingController, 
    SystemConfigController, 
    TaskController, 
    TaskDefinitionController, 
    TaskStatisticsController, 
    TaskSubmissionController, 
    TokenController, 
    WorkController
} from './services/';

export class Api {
    
    readonly accountController: AccountController
    
    readonly albumController: AlbumController
    
    readonly artistController: ArtistController
    
    readonly mediaFileController: MediaFileController
    
    readonly playbackQueueController: PlaybackQueueController
    
    readonly playlistController: PlaylistController
    
    readonly pluginController: PluginController
    
    readonly recordingController: RecordingController
    
    readonly systemConfigController: SystemConfigController
    
    readonly taskController: TaskController
    
    readonly taskDefinitionController: TaskDefinitionController
    
    readonly taskStatisticsController: TaskStatisticsController
    
    readonly taskSubmissionController: TaskSubmissionController
    
    readonly tokenController: TokenController
    
    readonly workController: WorkController
    
    readonly fileSystemStorageController: FileSystemStorageController
    
    readonly ossStorageController: OssStorageController
    
    constructor(executor: Executor) {
        this.accountController = new AccountController(executor);
        this.albumController = new AlbumController(executor);
        this.artistController = new ArtistController(executor);
        this.mediaFileController = new MediaFileController(executor);
        this.playbackQueueController = new PlaybackQueueController(executor);
        this.playlistController = new PlaylistController(executor);
        this.pluginController = new PluginController(executor);
        this.recordingController = new RecordingController(executor);
        this.systemConfigController = new SystemConfigController(executor);
        this.taskController = new TaskController(executor);
        this.taskDefinitionController = new TaskDefinitionController(executor);
        this.taskStatisticsController = new TaskStatisticsController(executor);
        this.taskSubmissionController = new TaskSubmissionController(executor);
        this.tokenController = new TokenController(executor);
        this.workController = new WorkController(executor);
        this.fileSystemStorageController = new FileSystemStorageController(executor);
        this.ossStorageController = new OssStorageController(executor);
    }
}