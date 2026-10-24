package com.starter.workflow

import com.starter.documents.GeneratedDocumentDelivery
import jakarta.validation.Valid
import org.springframework.core.io.ByteArrayResource
import org.springframework.http.*
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.security.core.userdetails.UserDetails
import org.springframework.web.bind.annotation.*
import java.nio.charset.StandardCharsets
import java.util.UUID

@RestController @RequestMapping("/api/workflows")
class WorkflowRunController(private val runs: WorkflowRunService,private val emails: WorkflowEmailService) {
    @GetMapping fun available(@AuthenticationPrincipal user: UserDetails) = runs.available(user.username)
    @GetMapping("/runs") fun list(@AuthenticationPrincipal user: UserDetails) = runs.list(user.username)
    @PostMapping("/runs") fun start(@AuthenticationPrincipal user: UserDetails,@RequestBody request: WorkflowStartRequest) = runs.start(user.username,request.workflowId)
    @GetMapping("/runs/{id}") fun get(@AuthenticationPrincipal user: UserDetails,@PathVariable id: UUID) = runs.get(user.username,id)
    @PostMapping("/runs/{id}/advance") fun advance(@AuthenticationPrincipal user: UserDetails,@PathVariable id: UUID,@Valid @RequestBody request: WorkflowAdvanceRequest) = runs.advance(user.username,id,request)
    @PutMapping("/runs/{id}/document") fun document(@AuthenticationPrincipal user: UserDetails,@PathVariable id: UUID,@Valid @RequestBody request: WorkflowDocumentRequest) = runs.useDocument(user.username,id,request)
    @PostMapping("/runs/{id}/email") fun email(@AuthenticationPrincipal user: UserDetails,@PathVariable id: UUID,@Valid @RequestBody request: WorkflowSendRequest) = emails.send(user.username,id,request)
    @PostMapping("/runs/{id}/cancel") fun cancel(@AuthenticationPrincipal user: UserDetails,@PathVariable id: UUID,@Valid @RequestBody request: WorkflowRevisionRequest) = runs.cancel(user.username,id,request.revision)
}

@RestController @RequestMapping("/api/workflows/runs/{runId}/documents/{documentId}")
class WorkflowRunDocumentController(private val guard: WorkflowRunAccess,private val delivery: GeneratedDocumentDelivery) {
    @GetMapping("/pages/{page}",produces=[MediaType.IMAGE_PNG_VALUE])
    fun page(@AuthenticationPrincipal user: UserDetails,@PathVariable runId: UUID,@PathVariable documentId: UUID,@PathVariable page: Int) = ResponseEntity.ok().cacheControl(CacheControl.noStore()).contentType(MediaType.IMAGE_PNG)
        .body(delivery.page(user.username,page) {guard.document(user.username,runId,documentId)})
    @GetMapping("/download") fun download(@AuthenticationPrincipal user: UserDetails,@PathVariable runId: UUID,@PathVariable documentId: UUID): ResponseEntity<ByteArrayResource> {
        val file=delivery.download(user.username) {guard.document(user.username,runId,documentId)}
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).header(HttpHeaders.CONTENT_DISPOSITION,ContentDisposition.attachment().filename(file.filename,StandardCharsets.UTF_8).build().toString())
            .contentType(MediaType.APPLICATION_PDF).body(ByteArrayResource(file.bytes))
    }
}
