package app.unirhy.playback.queue

import app.unirhy.playback.sync.CurrentQueueDto

/**
 * 原生侧权威队列态：保存最近一次 SNAPSHOT / ROOM_EVENT_QUEUE_CHANGE 下发的队列，
 * 供播放执行（按下标解析 mediaFileId）、MediaSession 元数据与控制命令（version）使用。
 * 仅接受更高 version 的队列，防止事件乱序回退。
 */
class QueueState {
    @Volatile
    var queue: CurrentQueueDto? = null
        private set

    /** 应用新队列；version 不高于当前值时忽略并返回 false。 */
    fun apply(next: CurrentQueueDto): Boolean {
        synchronized(this) {
            val current = queue
            if (current != null && next.version <= current.version) {
                return false
            }
            queue = next
            return true
        }
    }

    fun clear() {
        synchronized(this) {
            queue = null
        }
    }

    fun version(): Long? = queue?.version

    fun itemAt(index: Int) = queue?.items?.getOrNull(index)
}
