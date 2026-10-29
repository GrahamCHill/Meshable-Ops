package com.starter.plugins.documents

import com.starter.plugins.workflows.WorkflowDefinition
import com.starter.plugins.workflows.WorkflowRepository
import org.hibernate.cfg.Configuration
import org.junit.jupiter.api.Test
import org.springframework.data.jpa.repository.Query

class DomainQueryTest {
    @Test fun `new domain mappings and repository queries compile with the PostgreSQL dialect`() {
        // Compile real HQL, including the metadata-only DTO projection, without requiring a database server.
        val beans=org.springframework.context.annotation.AnnotationConfigApplicationContext()
        beans.beanFactory.registerSingleton("secretEncryption",com.starter.security.testEncryption())
        beans.register(com.starter.security.EncryptedSecretConverter::class.java)
        beans.refresh()
        beans.use { context ->
        Configuration().apply { properties["hibernate.resource.beans.container"]=org.springframework.orm.hibernate5.SpringBeanContainer(context.beanFactory) }
            .addAnnotatedClass(DocumentTemplateRecord::class.java).addAnnotatedClass(GeneratedDocument::class.java)
            .addAnnotatedClass(WorkflowDefinition::class.java)
            .addAnnotatedClass(com.starter.plugins.workflows.WorkflowRun::class.java)
            .addAnnotatedClass(com.starter.notification.NotificationSettingsRecord::class.java)
            .addAnnotatedClass(com.starter.notification.NotificationPreferenceRecord::class.java)
            .addAnnotatedClass(com.starter.notification.NotificationDelivery::class.java)
            .addAnnotatedClass(com.starter.model.postgres.BrandingAsset::class.java)
            .addAnnotatedClass(com.starter.model.postgres.BrandingPreferencesRecord::class.java)
            .addAnnotatedClass(com.starter.model.postgres.BrandingLoginImage::class.java)
            .addAnnotatedClass(com.starter.model.postgres.PluginSettings::class.java)
            .addAnnotatedClass(com.starter.plugins.kanban.KanbanBoard::class.java)
            .addAnnotatedClass(com.starter.plugins.kanban.KanbanDevelopmentLink::class.java)
            .addAnnotatedClass(com.starter.plugins.calendar.CalendarEvent::class.java)
            .addAnnotatedClass(com.starter.plugins.calendar.PersonalCalendarConnection::class.java)
            .addAnnotatedClass(com.starter.plugins.calendar.CalendarSyncLink::class.java)
            .setProperty("hibernate.dialect", "org.hibernate.dialect.PostgreSQLDialect")
            .setProperty("hibernate.boot.allow_jdbc_metadata_access", "false")
            .setProperty("hibernate.connection.provider_class", "org.hibernate.engine.jdbc.connections.internal.UserSuppliedConnectionProviderImpl")
            .setProperty("hibernate.hbm2ddl.auto", "none").buildSessionFactory().use { factory ->
                // A valid zero slot must be an assigned identity; otherwise Spring Data silently skips its deletion.
                val imageInfo = org.springframework.data.jpa.repository.support.JpaMetamodelEntityInformation<com.starter.model.postgres.BrandingLoginImage, Int>(
                    com.starter.model.postgres.BrandingLoginImage::class.java, factory.metamodel, factory.persistenceUnitUtil)
                org.assertj.core.api.Assertions.assertThat(imageInfo.isNew(com.starter.model.postgres.BrandingLoginImage(slot = 0))).isFalse()
                factory.openSession().use { session ->
                    listOf(DocumentTemplateRepository::class.java, GeneratedDocumentRepository::class.java, WorkflowRepository::class.java, com.starter.plugins.workflows.WorkflowRunRepository::class.java, com.starter.repository.postgres.BrandingAssetRepository::class.java, com.starter.repository.postgres.BrandingLoginImageRepository::class.java, com.starter.plugins.kanban.KanbanRepository::class.java, com.starter.plugins.calendar.CalendarEventRepository::class.java,com.starter.plugins.calendar.PersonalCalendarRepository::class.java).forEach { repository ->
                        repository.methods.mapNotNull { it.getAnnotation(Query::class.java) }.filterNot { it.nativeQuery }.forEach { query -> session.createQuery(query.value) }
                    }
                }
            }
        }
    }
}
