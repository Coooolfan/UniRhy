import type {Executor} from '../';
import type {AsyncTaskLogCountRow, ScanTaskRequest, TranscodeTaskRequest} from '../model/static/';

/**
 * 任务管理接口
 * 
 * 提供内置任务触发能力（扫描、转码）；插件任务通过 PluginController 提交
 */
export class TaskController {
    
    constructor(private executor: Executor) {}
    
    readonly executeScanTask: (options: TaskControllerOptions['executeScanTask']) => Promise<
        void
    > = async(options) => {
        let _uri = '/api/task/scan';
        return (await this.executor({uri: _uri, method: 'POST', body: options.body})) as Promise<void>;
    }
    
    readonly executeTranscodeTask: (options: TaskControllerOptions['executeTranscodeTask']) => Promise<
        void
    > = async(options) => {
        let _uri = '/api/task/transcode';
        return (await this.executor({uri: _uri, method: 'POST', body: options.body})) as Promise<void>;
    }
    
    readonly listTaskLogs: () => Promise<
        ReadonlyArray<AsyncTaskLogCountRow>
    > = async() => {
        let _uri = '/api/task/logs';
        return (await this.executor({uri: _uri, method: 'GET'})) as Promise<ReadonlyArray<AsyncTaskLogCountRow>>;
    }
}

export type TaskControllerOptions = {
    'executeScanTask': {
        readonly body: ScanTaskRequest
    }, 
    'executeTranscodeTask': {
        readonly body: TranscodeTaskRequest
    }, 
    'listTaskLogs': {}
}
