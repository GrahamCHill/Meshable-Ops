package com.starter.service.pdfstorage

import com.starter.model.postgres.AppSettings
import com.starter.service.PdfService
import com.starter.service.storage.GeneratedPdfNamespace
import com.starter.service.storage.objectStorageAdapter
import org.springframework.stereotype.Component
import java.util.UUID

@Component
class GeneratedPdfObjectBackend : GeneratedPdfBackend {
    override fun supports(provider: String) = provider in setOf("S3", "AZURE_BLOB", "GCS")
    override fun prepare(location: PdfStorageLocation, id: UUID) = mapOf("path" to "$id.pdf")
    private fun adapter(file: GeneratedPdfFile): com.starter.service.storage.ObjectStorageAdapter {
        val values = file.location.values
        val prefix = values.getValue("prefix")
        require(prefix.endsWith("/${GeneratedPdfNamespace.FOLDER}/"))
        require(file.locator.getValue("path") == "${file.id}.pdf")
        return objectStorageAdapter(AppSettings(storageProvider = file.location.provider, storageBucket = values.getValue("bucket"),
            storageRegion = values["region"]?.ifBlank { null }, storageEndpoint = values["endpoint"]?.ifBlank { null },
            storageAccessKey = file.location.credentials["accessKey"], storageSecretKey = file.location.credentials["secretKey"]), prefix, boundedTransfers = true)
    }
    override fun upload(file: GeneratedPdfFile, bytes: ByteArray) = adapter(file).upload(file.location.values.getValue("prefix"), file.locator.getValue("path"), "application/pdf", bytes)
    override fun download(file: GeneratedPdfFile) = adapter(file).download(file.location.values.getValue("prefix"), file.locator.getValue("path"), PdfService.MAX_BYTES).bytes
    override fun delete(file: GeneratedPdfFile) {
        try { adapter(file).delete(file.location.values.getValue("prefix"), file.locator.getValue("path")) }
        catch (error: software.amazon.awssdk.services.s3.model.S3Exception) { if (error.statusCode() != 404) throw error }
        catch (error: com.azure.storage.blob.models.BlobStorageException) { if (error.statusCode != 404) throw error }
        catch (error: com.google.cloud.storage.StorageException) { if (error.code != 404) throw error }
    }
}
