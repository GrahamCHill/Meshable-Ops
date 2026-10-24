package com.starter.plugins.operations

import com.fasterxml.jackson.databind.JsonNode
import org.springframework.http.*
import org.springframework.web.bind.annotation.*
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.security.core.userdetails.UserDetails
import java.time.LocalDate
import java.util.UUID

@RestController @RequestMapping("/api/plugins/timesheets")
class TimesheetController(private val service:TimesheetService,private val visibility:TimeVisibility) {
    @GetMapping("/totals") fun totals(@AuthenticationPrincipal u:UserDetails,@RequestParam projectId:UUID,@RequestParam from:LocalDate,@RequestParam to:LocalDate)=visibility.totals(u.username,projectId,from,to)
    @GetMapping fun list(@AuthenticationPrincipal u:UserDetails,@RequestParam from:LocalDate,@RequestParam to:LocalDate,@RequestParam(required=false) projectId:UUID?,@RequestParam(defaultValue="false") team:Boolean)=service.list(u.username,from,to,projectId,team)
    @PostMapping fun create(@AuthenticationPrincipal u:UserDetails,@RequestBody i:TimeEntryInput)=service.save(u.username,null,i)
    @PutMapping("/{id}") fun save(@AuthenticationPrincipal u:UserDetails,@PathVariable id:UUID,@RequestBody i:TimeEntryInput)=service.save(u.username,id,i)
    @PostMapping("/{id}/action") fun action(@AuthenticationPrincipal u:UserDetails,@PathVariable id:UUID,@RequestBody i:TimeAction)=service.action(u.username,id,i)
    @PostMapping("/{id}/reconcile") fun reconcile(@AuthenticationPrincipal u:UserDetails,@PathVariable id:UUID,@RequestBody i:TimeEntryInput)=service.reconcile(u.username,id,i)
    @DeleteMapping("/{id}") fun remove(@AuthenticationPrincipal u:UserDetails,@PathVariable id:UUID,@RequestParam revision:Long)=service.remove(u.username,id,revision)
    @GetMapping("/timer") fun timer(@AuthenticationPrincipal u:UserDetails)=service.timer(u.username)
    @PostMapping("/timer") fun start(@AuthenticationPrincipal u:UserDetails,@RequestBody i:TimerInput)=service.start(u.username,i)
    @PostMapping("/timer/stop") fun stop(@AuthenticationPrincipal u:UserDetails)=service.stop(u.username)
}
@RestController @RequestMapping("/api/plugins/activitywatch")
class ActivityWatchController(private val service:ActivityWatchService) {
    @GetMapping fun list(@AuthenticationPrincipal u:UserDetails)=service.list(u.username)
    @PostMapping("/import") fun import(@AuthenticationPrincipal u:UserDetails,@RequestBody i:JsonNode)=service.import(u.username,i)
    @PostMapping("/{id}/allocate") fun allocate(@AuthenticationPrincipal u:UserDetails,@PathVariable id:UUID,@RequestBody i:ActivityAllocation)=service.allocate(u.username,id,i)
    @DeleteMapping("/{id}") fun remove(@AuthenticationPrincipal u:UserDetails,@PathVariable id:UUID,@RequestParam revision:Long)=service.remove(u.username,id,revision)
}
@RestController @RequestMapping("/api/plugins/timecamp")
class TimeCampController(private val service:TimeCampService) {
    @GetMapping("/mappings") fun list(@AuthenticationPrincipal u:UserDetails)=service.mappings(u.username)
    @PostMapping("/mappings") fun map(@AuthenticationPrincipal u:UserDetails,@RequestBody i:TimeCampMapping)=service.map(u.username,i)
    @DeleteMapping("/mappings/{id}") fun remove(@AuthenticationPrincipal u:UserDetails,@PathVariable id:UUID,@RequestParam revision:Long)=service.remove(u.username,id,revision)
    @PostMapping("/sync") fun sync(@AuthenticationPrincipal u:UserDetails,@RequestBody i:TimeCampSyncInput)=service.sync(u.username,i)
}
@RestController @RequestMapping("/api/plugins/cloud-costs")
class CloudCostController(private val service:CloudCosts) {
    @GetMapping fun list(@AuthenticationPrincipal u:UserDetails,@RequestParam projectId:UUID)=service.list(u.username,projectId)
    @GetMapping("/spend") fun spend(@AuthenticationPrincipal u:UserDetails,@RequestParam projectId:UUID,@RequestParam from:LocalDate,@RequestParam to:LocalDate)=service.spend(u.username,projectId,from,to)
    @PostMapping("/connections") fun create(@AuthenticationPrincipal u:UserDetails,@RequestBody i:CloudConnectionInput)=service.connect(u.username,null,i)
    @PutMapping("/connections/{id}") fun save(@AuthenticationPrincipal u:UserDetails,@PathVariable id:UUID,@RequestBody i:CloudConnectionInput)=service.connect(u.username,id,i)
    @PostMapping("/connections/{id}/refresh") fun refresh(@AuthenticationPrincipal u:UserDetails,@PathVariable id:UUID,@RequestBody i:CloudRefresh)=service.refresh(u.username,id,i)
}
@RestController @RequestMapping("/api/plugins/billing-items")
class BillingItemsController(private val items:BillingItems,private val rates:BillingRates) {
    @GetMapping fun list(@AuthenticationPrincipal u:UserDetails,@RequestParam projectId:UUID)=items.list(u.username,projectId)
    @PostMapping fun create(@AuthenticationPrincipal u:UserDetails,@RequestBody i:CatalogInput)=items.save(u.username,null,i)
    @PutMapping("/{id}") fun save(@AuthenticationPrincipal u:UserDetails,@PathVariable id:UUID,@RequestBody i:CatalogInput)=items.save(u.username,id,i)
    @GetMapping("/rates") fun rates(@AuthenticationPrincipal u:UserDetails)=rates.list(u.username)
    @PostMapping("/rates") fun rate(@AuthenticationPrincipal u:UserDetails,@RequestBody i:RateInput)=rates.rate(u.username,null,i)
    @PutMapping("/rates/{id}") fun rateSave(@AuthenticationPrincipal u:UserDetails,@PathVariable id:UUID,@RequestBody i:RateInput)=rates.rate(u.username,id,i)
    @PostMapping("/assignments") fun assign(@AuthenticationPrincipal u:UserDetails,@RequestBody i:AssignmentInput)=rates.assignment(u.username,null,i)
    @PutMapping("/assignments/{id}") fun assignSave(@AuthenticationPrincipal u:UserDetails,@PathVariable id:UUID,@RequestBody i:AssignmentInput)=rates.assignment(u.username,id,i)
}
@RestController @RequestMapping("/api/plugins/billing")
class BillingController(private val service:BillingService,private val profiles:BillingProfiles,private val docs:BillingDocuments,private val delivery:BillingDelivery,private val inbox:BillingInbox,private val schedules:BillingSchedules) {
    @GetMapping("/defaults") fun defaults(@AuthenticationPrincipal u:UserDetails)=profiles.defaults(u.username)
    @GetMapping("/clients/{id}") fun profile(@AuthenticationPrincipal u:UserDetails,@PathVariable id:UUID)=profiles.get(u.username,id)
    @PutMapping("/clients/{id}") fun profileSave(@AuthenticationPrincipal u:UserDetails,@PathVariable id:UUID,@RequestBody i:BillingProfileInput):Map<String,Any?> {if(id!=i.profile.clientId)throw com.starter.exception.BadRequestException("Client ID mismatch");return profiles.save(u.username,i)}
    @GetMapping fun list(@AuthenticationPrincipal u:UserDetails,@RequestParam projectId:UUID)=service.list(u.username,projectId)
    @PostMapping("/preview") fun preview(@AuthenticationPrincipal u:UserDetails,@RequestBody i:BillingGenerate)=service.preview(u.username,i)
    @PostMapping fun generate(@AuthenticationPrincipal u:UserDetails,@RequestBody i:BillingGenerate)=service.generate(u.username,i)
    @PostMapping("/{id}/send") fun send(@AuthenticationPrincipal u:UserDetails,@PathVariable id:UUID,@RequestBody i:BillingSend)=delivery.send(u.username,id,i)
    @PostMapping("/{id}/state") fun state(@AuthenticationPrincipal u:UserDetails,@PathVariable id:UUID,@RequestBody i:BillingState)=service.state(u.username,id,i)
    @GetMapping("/{id}/pdf") fun pdf(@AuthenticationPrincipal u:UserDetails,@PathVariable id:UUID):ResponseEntity<ByteArray> {val d=service.view(service.get(u.username,id));return ResponseEntity.ok().contentType(MediaType.APPLICATION_PDF).header("Content-Disposition","attachment; filename=\"${d.document.number}.pdf\"").header("Cache-Control","private, no-store").header("X-Content-Type-Options","nosniff").body(docs.bytes(u.username,d.document))}
    @GetMapping("/replies") fun replies(@AuthenticationPrincipal u:UserDetails,@RequestParam projectId:UUID)=inbox.list(u.username,projectId)
    @PostMapping("/inbox/poll") fun poll(@AuthenticationPrincipal u:UserDetails)=inbox.poll(u.username)
    @GetMapping("/schedules") fun schedules(@AuthenticationPrincipal u:UserDetails,@RequestParam projectId:UUID)=schedules.list(u.username,projectId)
    @PostMapping("/schedules") fun schedule(@AuthenticationPrincipal u:UserDetails,@RequestBody i:BillingScheduleInput)=schedules.save(u.username,null,i)
    @PutMapping("/schedules/{id}") fun scheduleSave(@AuthenticationPrincipal u:UserDetails,@PathVariable id:UUID,@RequestBody i:BillingScheduleInput)=schedules.save(u.username,id,i)
}
@RestController @RequestMapping("/api/plugins/on-call")
class OnCallController(private val service:OnCallService,private val callers:List<OnCallCallingProvider>) {
    @GetMapping fun list(@AuthenticationPrincipal u:UserDetails,@RequestParam projectId:UUID)=service.list(u.username,projectId)
    @GetMapping("/calling") fun calling()=mapOf("providers" to callers.map {mapOf("id" to it.id,"available" to it.available())},"note" to "Discord and Telegram support chat paging here. Voice calls require an installed calling provider; Teams calling also needs a registered calling bot and callback service.")
    @PostMapping("/rotations") fun rotation(@AuthenticationPrincipal u:UserDetails,@RequestBody i:RotationInput)=service.rotation(u.username,null,i)
    @PutMapping("/rotations/{id}") fun rotationSave(@AuthenticationPrincipal u:UserDetails,@PathVariable id:UUID,@RequestBody i:RotationInput)=service.rotation(u.username,id,i)
    @PostMapping("/alerts") fun report(@AuthenticationPrincipal u:UserDetails,@RequestBody i:AlertInput)=service.report(u.username,i)
    @PostMapping("/alerts/{id}/action") fun action(@AuthenticationPrincipal u:UserDetails,@PathVariable id:UUID,@RequestBody i:AlertAction)=service.action(u.username,id,i)
    @PostMapping("/sources") fun source(@AuthenticationPrincipal u:UserDetails,@RequestBody i:MonitorSourceInput)=service.source(u.username,i)
}
