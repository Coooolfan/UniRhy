package com.coooolfan.unirhy.sync.service

import org.springframework.jdbc.core.namedparam.MapSqlParameterSource
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate
import org.springframework.stereotype.Service

enum class PlaybackSyncJobType {
    PENDING_PLAY_TIMEOUT,
    AUTO_ADVANCE,
}

enum class PlaybackSyncJobStatus {
    PENDING,
    RUNNING,
    COMPLETED,
    FAILED,
    CANCELED,
}

data class PlaybackSyncJob(
    val id: Long,
    val jobType: PlaybackSyncJobType,
    val accountId: Long,
    val dedupeKey: String,
    val payload: String,
    val executeAtMs: Long,
    val status: PlaybackSyncJobStatus,
    val retryCount: Int,
)

interface PlaybackSyncJobStore {
    fun schedule(
        jobType: PlaybackSyncJobType,
        accountId: Long,
        dedupeKey: String,
        payload: String,
        executeAtMs: Long,
        nowMs: Long,
    )

    fun cancel(dedupeKey: String)

    fun claimDueJobs(
        nowMs: Long,
        limit: Int,
    ): List<PlaybackSyncJob>

    fun markCompleted(jobId: Long)

    fun markFailed(jobId: Long)
}

class InMemoryPlaybackSyncJobStore : PlaybackSyncJobStore {
    override fun schedule(
        jobType: PlaybackSyncJobType,
        accountId: Long,
        dedupeKey: String,
        payload: String,
        executeAtMs: Long,
        nowMs: Long,
    ) {
    }

    override fun cancel(dedupeKey: String) {
    }

    override fun claimDueJobs(
        nowMs: Long,
        limit: Int,
    ): List<PlaybackSyncJob> = emptyList()

    override fun markCompleted(jobId: Long) {
    }

    override fun markFailed(jobId: Long) {
    }
}

@Service
class JdbcPlaybackSyncJobStore(
    private val jdbc: NamedParameterJdbcTemplate,
) : PlaybackSyncJobStore {
    override fun schedule(
        jobType: PlaybackSyncJobType,
        accountId: Long,
        dedupeKey: String,
        payload: String,
        executeAtMs: Long,
        nowMs: Long,
    ) {
        val params = MapSqlParameterSource()
            .addValue("jobType", jobType.name)
            .addValue("accountId", accountId)
            .addValue("dedupeKey", dedupeKey)
            .addValue("payload", payload)
            .addValue("executeAtMs", executeAtMs)
            .addValue("status", PlaybackSyncJobStatus.PENDING.name)
            .addValue("nowMs", nowMs)
        jdbc.update(
            """
                INSERT INTO public.playback_sync_job (
                    job_type,
                    account_id,
                    dedupe_key,
                    payload,
                    execute_at_ms,
                    status,
                    retry_count,
                    created_at_ms,
                    updated_at_ms
                ) VALUES (
                    :jobType,
                    :accountId,
                    :dedupeKey,
                    CAST(:payload AS jsonb),
                    :executeAtMs,
                    :status,
                    0,
                    :nowMs,
                    :nowMs
                )
                ON CONFLICT (dedupe_key) DO UPDATE
                SET job_type = EXCLUDED.job_type,
                    account_id = EXCLUDED.account_id,
                    payload = EXCLUDED.payload,
                    execute_at_ms = EXCLUDED.execute_at_ms,
                    status = EXCLUDED.status,
                    retry_count = 0,
                    updated_at_ms = EXCLUDED.updated_at_ms
            """.trimIndent(),
            params,
        )
    }

    override fun cancel(dedupeKey: String) {
        val params = MapSqlParameterSource().addValue("dedupeKey", dedupeKey)
        jdbc.update(
            """
                UPDATE public.playback_sync_job
                SET status = :status
                WHERE dedupe_key = :dedupeKey
            """.trimIndent(),
            params.addValue("status", PlaybackSyncJobStatus.CANCELED.name),
        )
    }

    override fun claimDueJobs(
        nowMs: Long,
        limit: Int,
    ): List<PlaybackSyncJob> {
        val params = MapSqlParameterSource()
            .addValue("nowMs", nowMs)
            .addValue("limit", limit)
            .addValue("pendingStatus", PlaybackSyncJobStatus.PENDING.name)
            .addValue("runningStatus", PlaybackSyncJobStatus.RUNNING.name)
        return jdbc.query(
            """
                WITH claimed AS (
                    SELECT id
                    FROM public.playback_sync_job
                    WHERE status = :pendingStatus
                      AND execute_at_ms <= :nowMs
                    ORDER BY execute_at_ms, id
                    LIMIT :limit
                    FOR UPDATE SKIP LOCKED
                )
                UPDATE public.playback_sync_job job
                SET status = :runningStatus,
                    updated_at_ms = :nowMs
                WHERE job.id IN (SELECT id FROM claimed)
                RETURNING job.id,
                          job.job_type,
                          job.account_id,
                          job.dedupe_key,
                          job.payload::text AS payload,
                          job.execute_at_ms,
                          job.status,
                          job.retry_count
            """.trimIndent(),
            params,
        ) { rs, _ ->
            PlaybackSyncJob(
                id = rs.getLong("id"),
                jobType = PlaybackSyncJobType.valueOf(rs.getString("job_type")),
                accountId = rs.getLong("account_id"),
                dedupeKey = rs.getString("dedupe_key"),
                payload = rs.getString("payload"),
                executeAtMs = rs.getLong("execute_at_ms"),
                status = PlaybackSyncJobStatus.valueOf(rs.getString("status")),
                retryCount = rs.getInt("retry_count"),
            )
        }
    }

    override fun markCompleted(jobId: Long) {
        val params = MapSqlParameterSource()
            .addValue("jobId", jobId)
            .addValue("status", PlaybackSyncJobStatus.COMPLETED.name)
        jdbc.update(
            """
                UPDATE public.playback_sync_job
                SET status = :status
                WHERE id = :jobId
            """.trimIndent(),
            params,
        )
    }

    override fun markFailed(jobId: Long) {
        val params = MapSqlParameterSource()
            .addValue("jobId", jobId)
            .addValue("status", PlaybackSyncJobStatus.FAILED.name)
        jdbc.update(
            """
                UPDATE public.playback_sync_job
                SET status = :status
                WHERE id = :jobId
            """.trimIndent(),
            params,
        )
    }
}
