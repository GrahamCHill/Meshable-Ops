package com.starter.service.pdfstorage

import com.fasterxml.jackson.core.type.TypeReference
import com.fasterxml.jackson.databind.ObjectMapper
import com.starter.security.SecretEncryption
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.stereotype.Repository
import org.springframework.transaction.annotation.Propagation
import org.springframework.transaction.annotation.Transactional
import java.sql.ResultSet
import java.util.UUID

enum class GeneratedPdfKind { DOCUMENT, REPORT }
enum class PdfStorageDestination { AUTO, DATABASE, DEFAULT_STORAGE, CONNECTION, GOOGLE_DRIVE, ONEDRIVE, SHAREPOINT }

data class PdfStorageLocation(val provider: String, val source: String, val values: Map<String, String>, @get:com.fasterxml.jackson.annotation.JsonIgnore val credentials: Map<String, String> = emptyMap()) {
    override fun toString() = "PdfStorageLocation(provider=$provider)"
}
data class GeneratedPdfFile(val id: UUID, val kind: GeneratedPdfKind, val location: PdfStorageLocation,
    val locator: Map<String, String>, val size: Int, val checksum: String) {
    override fun toString() = "GeneratedPdfFile(id=$id, kind=$kind)"
}
interface GeneratedPdfBackend {
    fun supports(provider: String): Boolean
    fun prepare(location: PdfStorageLocation, id: UUID): Map<String, String>
    fun upload(file: GeneratedPdfFile, bytes: ByteArray)
    fun download(file: GeneratedPdfFile): ByteArray
    fun delete(file: GeneratedPdfFile)
}

/** Each external object has a durable locator and encrypted credentials, including after connection edits. */
@Repository
class GeneratedPdfFiles(private val jdbc: JdbcTemplate, private val json: ObjectMapper, private val encryption: SecretEncryption) {
    private fun decode(value: String): Map<String, String> = json.readValue(value, object : TypeReference<Map<String, String>>() {})
    private fun file(row: ResultSet) = GeneratedPdfFile(row.getObject("id", UUID::class.java), GeneratedPdfKind.valueOf(row.getString("kind")),
        PdfStorageLocation(row.getString("provider"), row.getString("source"), decode(row.getString("location_json")),
            encryption.decrypt(row.getString("credentials_json"))?.let(::decode) ?: emptyMap()),
        decode(row.getString("locator_json")), row.getInt("size_bytes"), row.getString("checksum"))

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    fun begin(file: GeneratedPdfFile) {
        val credentials = file.location.credentials.takeIf { it.isNotEmpty() }?.let { encryption.encrypt(json.writeValueAsString(it)) }
        jdbc.update("""INSERT INTO generated_pdf_files(id,kind,provider,source,location_json,credentials_json,locator_json,size_bytes,checksum,state)
            VALUES (?,?,?,?,?,?,?,?,?,'UPLOADING')""", file.id, file.kind.name, file.location.provider, file.location.source,
            json.writeValueAsString(file.location.values), credentials, json.writeValueAsString(file.locator), file.size, file.checksum)
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    fun activate(id: UUID) { check(jdbc.update("UPDATE generated_pdf_files SET state='ACTIVE' WHERE id=? AND state='UPLOADING'", id) == 1) }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    fun abandon(id: UUID) { jdbc.update("UPDATE generated_pdf_files SET state='DELETE_PENDING',next_delete_at=NOW() WHERE id=?", id) }

    fun active(id: UUID): GeneratedPdfFile? = jdbc.query("SELECT * FROM generated_pdf_files WHERE id=? AND state='ACTIVE'", { row, _ -> file(row) }, id).singleOrNull()
    fun pendingCount(): Long = jdbc.queryForObject("SELECT count(*) FROM generated_pdf_files WHERE state='DELETE_PENDING'", Long::class.java) ?: 0

    fun pending(): GeneratedPdfFile? = jdbc.query("""SELECT * FROM generated_pdf_files
        WHERE state='DELETE_PENDING' AND next_delete_at<=NOW() ORDER BY next_delete_at,id FOR UPDATE SKIP LOCKED LIMIT 1""",
        { row, _ -> file(row) }).singleOrNull()

    fun deleted(id: UUID) { jdbc.update("DELETE FROM generated_pdf_files WHERE id=? AND state='DELETE_PENDING'", id) }
    fun retry(id: UUID) { jdbc.update("UPDATE generated_pdf_files SET next_delete_at=NOW()+INTERVAL '5 minutes' WHERE id=? AND state='DELETE_PENDING'", id) }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    fun status(message: String) {
        jdbc.update("UPDATE generated_pdf_cleanup_settings SET last_storage_status=?,last_storage_at=NOW() WHERE id=1", message)
    }

    @Transactional
    fun queueOrphans() {
        // Failed uploads and rolled-back source saves never leave untracked blobs indefinitely. Live PDFs are untouched.
        jdbc.update("""UPDATE generated_pdf_files f SET state='DELETE_PENDING',next_delete_at=NOW()
            WHERE state IN ('UPLOADING','ACTIVE') AND created_at<NOW()-INTERVAL '24 hours'
            AND (state='UPLOADING' OR
                (kind='REPORT' AND NOT EXISTS(SELECT 1 FROM workspace_project_reports r WHERE r.id=f.id AND octet_length(r.pdf)=0))
              OR (kind='DOCUMENT' AND NOT EXISTS(SELECT 1 FROM generated_documents d WHERE d.id=f.id AND octet_length(d.pdf_bytes)=0)))""")
    }
}
