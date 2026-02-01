import type {Executor} from '../';

/**
 * 媒体文件访问接口
 * 
 * 提供封面、音频等媒体文件的访问能力（当前仅支持本地文件系统存储）
 */
export class MediaFileController {
    
    constructor(private executor: Executor) {}
}

export type MediaFileControllerOptions = {
}
