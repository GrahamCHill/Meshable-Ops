package com.starter.service

import com.starter.ai.*
import com.starter.exception.ForbiddenException
import com.starter.model.postgres.*
import com.starter.repository.postgres.StorageDocumentRepository
import com.starter.service.storage.*
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import org.assertj.core.api.Assertions.*
import org.junit.jupiter.api.Test
import org.mockito.Mockito.*
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.transaction.PlatformTransactionManager
import java.util.UUID

class GeneratedPdfNamespaceTest {
    @Test fun `reserved paths cannot be downloaded indexed or uploaded through ordinary storage APIs`() {
        val settings=mock(AdminSettingsService::class.java)
        `when`(settings.currentEntity()).thenReturn(AppSettings())
        val storage=FolderStorageService(settings)
        val item=Item()
        val path="__generated_pdfs__/report.pdf"
        assertThat(GeneratedPdfNamespace.excluded("folder\\__GENERATED_PDFS__\\file.pdf")).isTrue()
        assertThat(GeneratedPdfNamespace.excluded("folder/not__generated_pdfs__/file.pdf")).isFalse()
        assertThatThrownBy {storage.downloadForIndex(item,path,1000)}.isInstanceOf(ForbiddenException::class.java)
        assertThatThrownBy {storage.download(item,path,false)}.isInstanceOf(ForbiddenException::class.java)
        assertThatThrownBy {storage.browse(item,"__generated_pdfs__")}.isInstanceOf(ForbiddenException::class.java)
        assertThatThrownBy {storage.upload(item,"",listOf(StorageObjectUpload(path,"application/pdf",byteArrayOf(1))))}.isInstanceOf(ForbiddenException::class.java)
    }

    @Test fun `even forced indexing cannot embed a root inside the generated PDF namespace`() {
        val documents=mock(StorageDocumentRepository::class.java)
        val embeddings=mock(EmbeddingService::class.java)
        val vectors=mock(VectorService::class.java)
        val queue=mock(DocumentProcessingQueue::class.java)
        val item=Item(storagePrefix="shared/__generated_pdfs__/")
        val stale=StorageDocument(item=item,storagePath="report.pdf")
        `when`(documents.findAllByItemId(item.id)).thenReturn(listOf(stale))
        val index=DocumentIndexService(documents,mock(FolderStorageService::class.java),vectors,embeddings,mock(AiSettingsService::class.java),mock(DocumentTextService::class.java),mock(AdminSettingsService::class.java),queue)
        assertThat(index.sync(item,listOf(StorageEntry("report.pdf","report.pdf","OBJECT")),force=true)).isEmpty()
        verify(queue).cancel(stale.id)
        verify(vectors).deleteDocument(stale.id)
        verify(documents).deleteAll(listOf(stale))
        verifyNoInteractions(embeddings)
    }

    @Test fun `already queued generated files are discarded before download extraction or embedding`() {
        val documents=mock(StorageDocumentRepository::class.java)
        val storage=mock(FolderStorageService::class.java)
        val text=mock(DocumentTextService::class.java)
        val embeddings=mock(EmbeddingService::class.java)
        val settings=mock(AiSettingsService::class.java)
        val vectors=mock(VectorService::class.java)
        val queue=mock(DocumentProcessingQueue::class.java)
        val document=StorageDocument(item=Item(storagePrefix="shared/"),storagePath="__generated_pdfs__/report.pdf")
        val job=DocumentJob(document.id,"checksum","model",UUID.randomUUID(),1)
        `when`(documents.findForProcessing(document.id)).thenReturn(document)
        `when`(queue.lockDocument(document.id)).thenReturn(true)
        `when`(queue.owns(job)).thenReturn(true)
        val manager=mock(PlatformTransactionManager::class.java)
        `when`(manager.getTransaction(any(org.springframework.transaction.TransactionDefinition::class.java))).thenReturn(org.springframework.transaction.support.SimpleTransactionStatus())
        val worker=DocumentProcessingWorker(queue,documents,storage,text,embeddings,settings,vectors,mock(JdbcTemplate::class.java),jacksonObjectMapper(),manager,1000)
        worker.process(job)
        verify(queue).complete(job)
        verifyNoInteractions(storage,text,embeddings,settings)
    }
}
