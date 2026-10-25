package com.starter.service

import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.starter.service.pdfstorage.*
import com.starter.security.testEncryption
import org.assertj.core.api.Assertions.*
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable
import org.mockito.Mockito.*
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider
import software.amazon.awssdk.http.urlconnection.UrlConnectionHttpClient
import software.amazon.awssdk.regions.Region
import software.amazon.awssdk.services.s3.S3Client
import software.amazon.awssdk.services.s3.S3Configuration
import software.amazon.awssdk.services.s3.model.S3Exception
import java.net.URI
import java.sql.Timestamp
import java.util.UUID

@EnabledIfEnvironmentVariable(named="PDF_STORAGE_TEST_S3_ENDPOINT",matches=".+")
@EnabledIfEnvironmentVariable(named="PDF_CLEANUP_TEST_JDBC_URL",matches=".+")
class GeneratedPdfObjectStorageIntegrationTest : GeneratedPdfDatabaseTest() {
    @Test fun `real S3 exports keep no database PDF copy read back correctly and cleanup only deletes eligible files`() {
        val endpoint=System.getenv("PDF_STORAGE_TEST_S3_ENDPOINT")
        val access=System.getenv("PDF_STORAGE_TEST_S3_ACCESS_KEY")
        val secret=System.getenv("PDF_STORAGE_TEST_S3_SECRET_KEY")
        val bucket="pdf-export-test-${UUID.randomUUID()}"
        val client=S3Client.builder().endpointOverride(URI(endpoint)).region(Region.US_EAST_1)
            .credentialsProvider(StaticCredentialsProvider.create(AwsBasicCredentials.create(access,secret)))
            .serviceConfiguration(S3Configuration.builder().pathStyleAccessEnabled(true).build())
            .httpClientBuilder(UrlConnectionHttpClient.builder()).build()
        client.use { s3 ->
            s3.createBucket {it.bucket(bucket)}
            try {
                val location=PdfStorageLocation("S3","DEFAULT",mapOf("bucket" to bucket,"region" to "us-east-1","endpoint" to endpoint,"prefix" to "app/__generated_pdfs__/"),mapOf("accessKey" to access,"secretKey" to secret))
                val destinations=mock(GeneratedPdfDestinations::class.java)
                `when`(destinations.resolve()).thenReturn(location)
                `when`(destinations.credentials(any(GeneratedPdfFile::class.java) ?: GeneratedPdfFile(UUID.randomUUID(),GeneratedPdfKind.DOCUMENT,location,emptyMap(),0,""))).thenAnswer {it.arguments[0]}
                val files=GeneratedPdfFiles(jdbc,jacksonObjectMapper(),testEncryption())
                val backend=GeneratedPdfObjectBackend()
                val storage=GeneratedPdfStorage(destinations,files,listOf(backend))
                val id=UUID.randomUUID();val billingId=UUID.randomUUID()
                val bytes="real-object-storage-export".toByteArray()
                assertThat(storage.persist(id,GeneratedPdfKind.DOCUMENT,bytes)).isEmpty()
                assertThat(storage.persist(billingId,GeneratedPdfKind.DOCUMENT,bytes)).isEmpty()
                for ((document,rebuildable) in listOf(id to true,billingId to false)) {
                    jdbc.update("INSERT INTO generated_documents(id,created_at,pdf_bytes,pdf_regenerable,template_snapshot,values_json) VALUES (?,?,?,?,?,?)",document,Timestamp.from(cutoff.minusSeconds(1)),byteArrayOf(),rebuildable,"saved-template","saved-values")
                }
                assertThat(storage.read(id,byteArrayOf())).isEqualTo(bytes)
                val parent=com.starter.model.postgres.Item(storagePrefix="",storageConnection=com.starter.model.postgres.StorageConnection(
                    provider="S3",bucket=bucket,region="us-east-1",endpoint=endpoint,accessKey=access,secretKey=secret,basePath=""))
                val ordinary=FolderStorageService(mock(AdminSettingsService::class.java))
                assertThat(ordinary.browse(parent,"app/").entries).isEmpty()
                assertThat(ordinary.listDocuments(parent)).isEmpty()
                assertThatThrownBy {ordinary.download(parent,"app/__generated_pdfs__/$id.pdf",false)}.isInstanceOf(com.starter.exception.ForbiddenException::class.java)
                assertThat(jdbc.queryForObject("SELECT credentials_json FROM generated_pdf_files WHERE id=?",String::class.java,id)).startsWith("enc:v1:").doesNotContain(access,secret)
                assertThat(jdbc.queryForObject("SELECT octet_length(pdf_bytes) FROM generated_documents WHERE id=?",Int::class.java,id)).isZero()
                assertThat(store.eligible(cutoff)).isEqualTo(PdfCleanupCounts(0,1,bytes.size.toLong()))
                assertThat(store.clear(cutoff)).isEqualTo(PdfCleanupCounts(0,1,bytes.size.toLong()))
                assertThat(storage.read(id,byteArrayOf())).isNull()
                val deletion=GeneratedPdfDeletion(files,destinations,listOf(backend))
                assertThat(deletion.next()).isTrue()
                assertThat(deletion.next()).isFalse()
                assertThatThrownBy {s3.headObject {it.bucket(bucket).key("app/__generated_pdfs__/$id.pdf")}}.isInstanceOfSatisfying(S3Exception::class.java) {assertThat(it.statusCode()).isEqualTo(404)}
                assertThat(storage.read(billingId,byteArrayOf())).isEqualTo(bytes)
                assertThat(jdbc.queryForObject("SELECT template_snapshot FROM generated_documents WHERE id=?",String::class.java,id)).isEqualTo("saved-template")
            } finally {
                // This test created this unique bucket; it cannot name an application bucket.
                require(bucket.startsWith("pdf-export-test-"))
                s3.listObjectsV2 {it.bucket(bucket)}.contents().forEach {entry -> s3.deleteObject {it.bucket(bucket).key(entry.key())}}
                s3.deleteBucket {it.bucket(bucket)}
            }
        }
    }
}
