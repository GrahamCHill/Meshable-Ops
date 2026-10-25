package com.starter.service

import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.starter.security.testEncryption
import com.starter.service.pdfstorage.*
import org.assertj.core.api.Assertions.*
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable
import java.util.UUID

@EnabledIfEnvironmentVariable(named="PDF_CLEANUP_TEST_JDBC_URL",matches=".+")
class GeneratedPdfFilesTest : GeneratedPdfDatabaseTest() {
    @Test fun `saved locators encrypt credentials and only unfinished orphaned or duplicate exports are reclaimed`() {
        val files=GeneratedPdfFiles(jdbc,jacksonObjectMapper(),testEncryption())
        val location=PdfStorageLocation("S3","DEFAULT",mapOf("bucket" to "test-bucket","prefix" to "app/__generated_pdfs__/"),mapOf("secretKey" to "test-storage-secret"))
        fun file(active:Boolean,sourceBytes:ByteArray?,old:Boolean=true):GeneratedPdfFile {
            val id=UUID.randomUUID()
            val file=GeneratedPdfFile(id,GeneratedPdfKind.DOCUMENT,location,mapOf("path" to "$id.pdf"),3,"checksum")
            files.begin(file)
            if(active)files.activate(id)
            if(old)jdbc.update("UPDATE generated_pdf_files SET created_at=NOW()-INTERVAL '2 days' WHERE id=?",id)
            if(sourceBytes!=null)jdbc.update("INSERT INTO generated_documents(id,created_at,pdf_bytes) VALUES (?,NOW(),?)",id,sourceBytes)
            return file
        }
        val liveBilling=file(true,byteArrayOf())
        val unfinished=file(false,"database-copy".toByteArray())
        val orphan=file(true,null)
        val duplicate=file(true,"database-copy".toByteArray())
        val recent=file(false,null,false)
        assertThat(files.active(liveBilling.id)).isEqualTo(liveBilling)
        assertThat(jdbc.queryForObject("SELECT credentials_json FROM generated_pdf_files WHERE id=?",String::class.java,liveBilling.id)).startsWith("enc:v1:").doesNotContain("test-storage-secret")
        assertThat(jacksonObjectMapper().writeValueAsString(location)).doesNotContain("test-storage-secret","credentials")
        files.queueOrphans()
        for (entry in listOf(unfinished,orphan,duplicate)) assertThat(jdbc.queryForObject("SELECT state FROM generated_pdf_files WHERE id=?",String::class.java,entry.id)).isEqualTo("DELETE_PENDING")
        assertThat(files.active(liveBilling.id)).isNotNull()
        assertThat(jdbc.queryForObject("SELECT state FROM generated_pdf_files WHERE id=?",String::class.java,recent.id)).isEqualTo("UPLOADING")
        assertThat(files.pendingCount()).isEqualTo(3)
        assertThat(jdbc.queryForObject("SELECT octet_length(pdf_bytes) FROM generated_documents WHERE id=?",Int::class.java,unfinished.id)).isPositive()
    }
}
