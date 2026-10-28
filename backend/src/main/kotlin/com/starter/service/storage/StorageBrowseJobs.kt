package com.starter.service.storage

import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.stereotype.Service
import java.util.UUID

data class StorageBrowseJob(val itemId: UUID, val path: String, val revision: Long, val token: UUID)

/** Coalesce changed directory listings, retaining work across restarts and fencing expired workers. */
@Service
class StorageBrowseJobs(private val jdbc: JdbcTemplate) {
    fun request(itemId: UUID, path: String) {
        jdbc.update("""INSERT INTO storage_browse_jobs(item_id, path) VALUES (?, ?)
            ON CONFLICT(item_id, path) DO UPDATE SET revision=storage_browse_jobs.revision+1""", itemId, path)
    }

    fun claim(): StorageBrowseJob? {
        val token = UUID.randomUUID()
        return jdbc.query("""WITH candidate AS (
            SELECT item_id, path FROM storage_browse_jobs WHERE next_attempt_at <= NOW()
              AND (lease_until IS NULL OR lease_until < NOW())
            ORDER BY next_attempt_at FOR UPDATE SKIP LOCKED LIMIT 1
            ) UPDATE storage_browse_jobs j SET claim_token=?, lease_until=NOW()+INTERVAL '30 minutes'
            FROM candidate c WHERE j.item_id=c.item_id AND j.path=c.path
            RETURNING j.item_id, j.path, j.revision""", { rs, _ ->
            StorageBrowseJob(rs.getObject("item_id", UUID::class.java), rs.getString("path"), rs.getLong("revision"), token)
        }, token).firstOrNull()
    }

    fun complete(job: StorageBrowseJob) {
        jdbc.update("DELETE FROM storage_browse_jobs WHERE item_id=? AND path=? AND revision=? AND claim_token=?",
            job.itemId, job.path, job.revision, job.token)
        // A read/upload requested a newer revision during processing. Keep it ready for another pass.
        jdbc.update("""UPDATE storage_browse_jobs SET lease_until=NULL, claim_token=NULL, next_attempt_at=NOW()
            WHERE item_id=? AND path=? AND claim_token=?""", job.itemId, job.path, job.token)
    }

    fun failed(job: StorageBrowseJob) {
        jdbc.update("""UPDATE storage_browse_jobs SET lease_until=NULL, claim_token=NULL,
            next_attempt_at=NOW()+INTERVAL '1 minute' WHERE item_id=? AND path=? AND claim_token=?""",
            job.itemId, job.path, job.token)
    }
}
