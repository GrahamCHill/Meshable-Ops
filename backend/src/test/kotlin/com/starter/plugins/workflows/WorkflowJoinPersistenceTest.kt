package com.starter.plugins.workflows

import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.starter.model.postgres.User
import com.starter.repository.postgres.UserRepository
import com.starter.service.AuditLogService
import com.zaxxer.hikari.*
import org.assertj.core.api.Assertions.*
import org.hibernate.cfg.Configuration
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable
import org.mockito.Mockito.*
import org.springframework.aop.framework.ProxyFactory
import org.springframework.data.jpa.repository.support.JpaRepositoryFactory
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.orm.jpa.*
import org.springframework.transaction.annotation.AnnotationTransactionAttributeSource
import org.springframework.transaction.interceptor.TransactionInterceptor
import org.springframework.transaction.support.TransactionTemplate
import java.util.*
import java.util.concurrent.*

@EnabledIfEnvironmentVariable(named="DOCUMENT_TEST_JDBC_URL",matches=".+")
class WorkflowJoinPersistenceTest {
    @Test fun `persisted manual and webhook arrivals join once under concurrent delivery and references remain owner scoped`() {
        val mapper=jacksonObjectMapper().findAndRegisterModules();val runtime=WorkflowRuntime(mapper)
        val schema="workflow_join_${UUID.randomUUID().toString().replace("-","")}"
        HikariDataSource(HikariConfig().apply {jdbcUrl=System.getenv("DOCUMENT_TEST_JDBC_URL");maximumPoolSize=6}).use {source ->
            val jdbc=JdbcTemplate(source);jdbc.execute("CREATE SCHEMA $schema")
            try {
                Configuration().apply {properties["hibernate.connection.datasource"]=source}
                    .setPhysicalNamingStrategy(org.hibernate.boot.model.naming.CamelCaseToUnderscoresNamingStrategy())
                    .addAnnotatedClass(WorkflowDefinition::class.java).addAnnotatedClass(WorkflowRun::class.java).addAnnotatedClass(WorkflowWebhookReceipt::class.java)
                    .setProperty("hibernate.default_schema",schema).setProperty("hibernate.hbm2ddl.auto","create-drop")
                    .buildSessionFactory().use {factory ->
                        val em=SharedEntityManagerCreator.createSharedEntityManager(factory);val manager=JpaTransactionManager(factory);val tx=TransactionTemplate(manager)
                        val repositories=JpaRepositoryFactory(em);val definitions=repositories.getRepository(WorkflowRepository::class.java)
                        val rows=repositories.getRepository(WorkflowRunRepository::class.java);val receipts=repositories.getRepository(WorkflowWebhookReceiptRepository::class.java)
                        val owner=User(email="owner@example.test");val other=UUID.randomUUID();val reviewer=UUID.randomUUID();val endpoint=UUID.randomUUID()
                        val graph=WorkflowGraph(listOf(WorkflowBlock("manual",BlockKind.START,"Manual"),
                            WorkflowBlock("hook",BlockKind.WEBHOOK,"Webhook",webhookEndpointId=endpoint,webhookOwnerId=owner.id,webhookEvent="order.ready",webhookBooleanField="success"),
                            WorkflowBlock("join",BlockKind.JOIN,"Join"),WorkflowBlock("a",BlockKind.REVIEW,"Review A"),
                            WorkflowBlock("b",BlockKind.REVIEW,"Review B",reviewerIds=setOf(reviewer)),WorkflowBlock("end",BlockKind.END,"End")),
                            listOf(WorkflowEdge("m-j","manual","join"),WorkflowEdge("h-j","hook","join"),WorkflowEdge("j-a","join","a"),WorkflowEdge("j-b","join","b"),WorkflowEdge("a-e","a","end"),WorkflowEdge("b-e","b","end")))
                        val definition=WorkflowDefinition(state=WorkflowState.READY,published=true,graphJson=mapper.writeValueAsString(graph))
                        val run=WorkflowRun(ownerId=owner.id,workflowId=definition.id,graphJson=definition.graphJson,correlationKey="ORDER-123")
                        val otherRun=WorkflowRun(ownerId=other,workflowId=definition.id,graphJson=definition.graphJson,correlationKey="ORDER-123")
                        runtime.initialize(run);runtime.initialize(otherRun);runtime.advance(run,WorkflowAdvanceRequest(0,"manual"),owner.id)
                        tx.executeWithoutResult {em.persist(definition);em.persist(otherRun);em.persist(run)}
                        val guard=mock(WorkflowRunAccess::class.java);`when`(guard.actor(owner.email)).thenReturn(owner)
                        val users=mock(UserRepository::class.java);`when`(users.findById(owner.id)).thenReturn(Optional.of(owner))
                        val receiver=WorkflowWebhookTriggers(definitions,rows,mock(WorkflowRunService::class.java),runtime,guard,users,receipts,mock(AuditLogService::class.java),mock(WorkflowNotificationHooks::class.java),mapper)
                        val proxy=ProxyFactory(receiver).apply {isProxyTargetClass=true;addAdvice(TransactionInterceptor(manager,AnnotationTransactionAttributeSource()))}.proxy as WorkflowWebhookTriggers
                        val payload=mapper.readTree("""{"event":"order.ready","data":{"workflowId":"${definition.id}","reference":"ORDER-123","success":true}}""")
                        val pool=Executors.newFixedThreadPool(2)
                        try {pool.invokeAll(List(2) {Callable {proxy.receive(endpoint,"delivery-1",payload)}}).forEach {it.get()}}
                        finally {pool.shutdownNow()}
                        tx.executeWithoutResult {
                            val saved=rows.findById(run.id).get()
                            assertThat(runtime.execution(saved).active).containsExactlyInAnyOrder("a","b")
                            assertThat(runtime.history(saved).count {it.kind==BlockKind.WEBHOOK}).isEqualTo(1)
                            assertThat(runtime.history(saved).count {it.kind==BlockKind.JOIN}).isEqualTo(1)
                            assertThat(runtime.history(rows.findById(otherRun.id).get())).isEmpty()
                            assertThat(receipts.count()).isEqualTo(1)
                            assertThat(rows.awaitingReview(reviewer.toString(),org.springframework.data.domain.PageRequest.of(0,100)).map {it.id}).containsExactly(run.id)
                            runtime.advance(saved,WorkflowAdvanceRequest(saved.revision,"a",WorkflowAction.REVIEWED),owner.id)
                            runtime.advance(saved,WorkflowAdvanceRequest(saved.revision,"end"),owner.id)
                            assertThat(saved.status).isEqualTo(WorkflowRunStatus.IN_PROGRESS)
                            runtime.advance(saved,WorkflowAdvanceRequest(saved.revision,"b",WorkflowAction.REVIEWED),reviewer)
                            assertThat(saved.status).isEqualTo(WorkflowRunStatus.COMPLETED)
                        }
                    }
            } finally {jdbc.execute("DROP SCHEMA $schema CASCADE")}
        }
    }
}
