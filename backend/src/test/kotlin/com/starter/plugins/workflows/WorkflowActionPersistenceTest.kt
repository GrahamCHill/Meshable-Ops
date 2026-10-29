package com.starter.plugins.workflows

import com.starter.exception.ConflictException
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
import java.util.Optional
import java.util.UUID
import java.util.concurrent.Callable
import java.util.concurrent.Executors

@EnabledIfEnvironmentVariable(named="DOCUMENT_TEST_JDBC_URL",matches=".+")
class WorkflowActionPersistenceTest {
    @Test fun `prerequisites survive rejected business transactions and authorizations cannot be replayed`() {
        val schema="workflow_actions_${UUID.randomUUID().toString().replace("-","")}"
        HikariDataSource(HikariConfig().apply { jdbcUrl=System.getenv("DOCUMENT_TEST_JDBC_URL");maximumPoolSize=6 }).use { source ->
            val jdbc=JdbcTemplate(source);jdbc.execute("CREATE SCHEMA $schema")
            try {
                Configuration().apply { properties["hibernate.connection.datasource"]=source }
                    .setPhysicalNamingStrategy(org.hibernate.boot.model.naming.CamelCaseToUnderscoresNamingStrategy())
                    .addAnnotatedClass(WorkflowDefinition::class.java).addAnnotatedClass(WorkflowRun::class.java)
                    .addAnnotatedClass(WorkflowActionBinding::class.java).addAnnotatedClass(WorkflowActionRequest::class.java)
                    .setProperty("hibernate.default_schema",schema).setProperty("hibernate.hbm2ddl.auto","create-drop")
                    .setProperty("hibernate.physical_naming_strategy","org.hibernate.boot.model.naming.CamelCaseToUnderscoresNamingStrategy")
                    .buildSessionFactory().use { factory ->
                        val em=SharedEntityManagerCreator.createSharedEntityManager(factory)
                        val manager=JpaTransactionManager(factory);val tx=TransactionTemplate(manager)
                        val repositories=JpaRepositoryFactory(em)
                        val bindings=repositories.getRepository(WorkflowActionBindingRepository::class.java)
                        val requests=repositories.getRepository(WorkflowActionRequestRepository::class.java)
                        val definitions=repositories.getRepository(WorkflowRepository::class.java)
                        val runs=repositories.getRepository(WorkflowRunRepository::class.java)
                        val users=mock(UserRepository::class.java);val workflows=mock(WorkflowRunService::class.java)
                        val actor=User(email="member@example.test")
                        `when`(users.findByEmail(actor.email)).thenReturn(Optional.of(actor))
                        val definition=WorkflowDefinition(name="Approval",state=WorkflowState.READY,published=true)
                        val binding=WorkflowActionBinding(selector="category:external",workflowId=definition.id)
                        tx.executeWithoutResult { em.persist(definition);em.persist(binding) }
                        `when`(workflows.start(eq(actor.email) ?: actor.email,eq(definition.id) ?: definition.id,anyMap(),anyString())).thenAnswer {
                            val row=WorkflowRun(ownerId=actor.id,workflowId=definition.id,name="Approval",workflowRevision=definition.revision)
                            em.persist(row)
                            WorkflowRunView(row.id,row.name,row.workflowId,row.workflowRevision,row.status,row.revision,null,emptyList(),emptyMap(),emptyList(),null,row.emailState,null)
                        }
                        val target=WorkflowActionStore(bindings,requests,definitions,runs,workflows,users,mock(AuditLogService::class.java))
                        val proxy=ProxyFactory(target).apply { addAdvice(TransactionInterceptor(manager,AnnotationTransactionAttributeSource())) }.proxy as WorkflowActionStore
                        lateinit var approval: WorkflowActionRequest
                        assertThatThrownBy { tx.executeWithoutResult {
                            approval=proxy.prepare(actor.email,binding.id,"fingerprint",mapOf("trigger.action" to "send"))
                            throw IllegalStateException("Original action blocked")
                        } }.isInstanceOf(IllegalStateException::class.java)
                        assertThat(tx.execute { requests.findById(approval.id).isPresent }).isTrue()
                        assertThat(proxy.prepare(actor.email,binding.id,"fingerprint",emptyMap()).id).isEqualTo(approval.id)
                        assertThatThrownBy { proxy.consume(actor.email,listOf(approval),"changed payload") }.isInstanceOf(ConflictException::class.java)
                        assertThatThrownBy { proxy.consume(actor.email,listOf(approval),"fingerprint") }.isInstanceOf(ConflictException::class.java)
                        tx.executeWithoutResult { runs.findById(approval.runId).get().apply { status=WorkflowRunStatus.COMPLETED;valuesJson="""{"export.format":"PDF"}""" } }
                        val pool=Executors.newFixedThreadPool(2)
                        try {
                            val results=pool.invokeAll(List(2) { Callable { runCatching { proxy.consume(actor.email,listOf(approval),"fingerprint") }.getOrNull() } }).map { it.get() }
                            assertThat(results.filterNotNull()).containsExactly(mapOf("export.format" to "PDF"))
                        } finally { pool.shutdownNow() }
                        assertThat(tx.execute { requests.findById(approval.id).get().state }).isEqualTo("CONSUMED")
                        // A later design edit must not introduce a send into a prerequisite.
                        tx.executeWithoutResult { definitions.findById(definition.id).get().graphJson="""{"nodes":[{"kind":"EMAIL"}]}""" }
                        assertThatThrownBy {proxy.prepare(actor.email,binding.id,"new payload",emptyMap())}.isInstanceOf(com.starter.exception.ForbiddenException::class.java)
                        tx.executeWithoutResult { definitions.findById(definition.id).get().graphJson="{}" }
                        // Disagreeing export policies roll back every consumed approval together.
                        val secondBinding=WorkflowActionBinding(selector="timesheets.export",workflowId=definition.id)
                        tx.executeWithoutResult {em.persist(secondBinding)}
                        val first=proxy.prepare(actor.email,binding.id,"conflict",emptyMap())
                        val second=proxy.prepare(actor.email,secondBinding.id,"conflict",emptyMap())
                        tx.executeWithoutResult {
                            runs.findById(first.runId).get().apply {status=WorkflowRunStatus.COMPLETED;valuesJson="""{"export.format":"PDF"}"""}
                            runs.findById(second.runId).get().apply {status=WorkflowRunStatus.COMPLETED;valuesJson="""{"export.format":"CSV"}"""}
                        }
                        assertThatThrownBy {proxy.consume(actor.email,listOf(first,second),"conflict")}.isInstanceOf(ConflictException::class.java)
                        assertThat(tx.execute {listOf(first,second).map {requests.findById(it.id).get().state}}).containsExactly("PENDING","PENDING")
                        // Exercise the actual PostgreSQL review inbox predicate against captured graph JSON.
                        val reviewer=UUID.randomUUID()
                        tx.executeWithoutResult {
                            em.persist(WorkflowRun(ownerId=actor.id,workflowId=definition.id,currentNodeId="review",graphJson="""{"nodes":[{"id":"review","kind":"REVIEW","reviewerIds":["$reviewer"]}],"edges":[]}"""))
                        }
                        assertThat(tx.execute { runs.awaitingReview(reviewer.toString(),org.springframework.data.domain.PageRequest.of(0,100)).size }).isEqualTo(1)
                        assertThat(tx.execute { runs.awaitingReview(actor.id.toString(),org.springframework.data.domain.PageRequest.of(0,100)).size }).isZero()
                        jdbc.execute("ALTER TABLE $schema.workflow_runs DROP CONSTRAINT IF EXISTS workflow_runs_status_check")
                        jdbc.execute("ALTER TABLE $schema.workflow_runs ADD CONSTRAINT workflow_runs_status_check CHECK (status IN ('IN_PROGRESS','COMPLETED','CANCELLED'))")
                        val migration=org.springframework.core.io.ClassPathResource("schema.sql").inputStream.bufferedReader().use {it.readText()}
                            .substringAfter("-- PostgreSQL's Hibernate-created enum check").substringAfter('\n').substringBefore("-- Browsing never waits")
                            .replace("TABLE IF EXISTS workflow_runs ","TABLE IF EXISTS $schema.workflow_runs ")
                        jdbc.execute(migration)
                        jdbc.update("UPDATE $schema.workflow_runs SET status='REJECTED' WHERE current_node_id='review'")
                        assertThat(tx.execute { runs.awaitingReview(reviewer.toString(),org.springframework.data.domain.PageRequest.of(0,100)).size }).isZero()
                    }
            } finally { jdbc.execute("DROP SCHEMA $schema CASCADE") }
        }
    }
}
