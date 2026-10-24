package com.starter.projects

import com.starter.access.Roles
import com.starter.exception.ForbiddenException
import com.starter.model.postgres.User
import org.springframework.jdbc.core.JdbcTemplate

/** Creation authority does not grant visibility or management of unrelated projects. */
object ProjectCreationRules {
    fun allowed(actor: User, jdbc: JdbcTemplate): Boolean {
        if (!actor.active || actor.role !in Roles.all || actor.role == "EXTERNAL") return false
        if (Roles.admin(actor.role) || actor.role == Roles.BUSINESS_MANAGER) return true
        return jdbc.queryForObject("""SELECT EXISTS (SELECT 1 FROM workspace_projects p WHERE p.lead_id = ?
            OR EXISTS (SELECT 1 FROM jsonb_array_elements(COALESCE(CAST(p.details_json AS jsonb)->'members','[]'::jsonb)) member
                WHERE member->>'userId' = ? AND member->>'role' IN ('TEAM_LEAD','MANAGER'))
            OR EXISTS (SELECT 1 FROM workspace_work_groups g WHERE g.kind = 'TEAM' AND g.project_role IN ('TEAM_LEAD','MANAGER')
                AND jsonb_exists(COALESCE(CAST(p.details_json AS jsonb)->'groupIds','[]'::jsonb),CAST(g.id AS text))
                AND jsonb_exists(COALESCE(CAST(g.details_json AS jsonb)->'memberIds','[]'::jsonb),?)))""",
            Boolean::class.java, actor.id, actor.id.toString(), actor.id.toString()) == true
    }
    fun require(actor: User, jdbc: JdbcTemplate) {
        if (!allowed(actor, jdbc)) throw ForbiddenException("An administrator, Business Manager, project manager or team lead must create projects, clients and boards")
    }
}
