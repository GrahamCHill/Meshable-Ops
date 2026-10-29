package com.starter.plugins.workflows

import com.starter.exception.BadRequestException
import org.assertj.core.api.Assertions.*
import org.junit.jupiter.api.Test

class WorkflowCodeTest {
    @Test fun `restricted module computes values and chooses branches without a Python runtime`() {
        val code="""def run(context):
    amount = number(context["trigger.input.amount"])
    total = amount * 1.15
    if total > 100:
        return {"total": total, "review": True}
    else:
        return {"total": total, "review": False}
"""
        WorkflowCode.validate(code)
        assertThat(WorkflowCode.execute(code,mapOf("trigger.input.amount" to "100"))).containsEntry("total","115").containsEntry("review","True")
        assertThat(WorkflowCode.execute(code,mapOf("trigger.input.amount" to "10"))).containsEntry("total","11.5").containsEntry("review","False")
        assertThat(WorkflowCode.execute("def run(context):\n    return {\"value\": min(2, max(1, 0)), \"length\": len(\"abc\")}",emptyMap()))
            .containsEntry("value","1").containsEntry("length","3")
    }
    @Test fun `imports reflection loops recursion attributes and arbitrary calls are rejected`() {
        listOf("import os", "while True:", "for x in context:", "return __import__('os')", "return context.__class__", "return open('/etc/passwd')", "return run(context)", "return [x for x in context]").forEach { statement ->
            assertThatThrownBy { WorkflowCode.validate("def run(context):\n    $statement") }.isInstanceOf(BadRequestException::class.java)
        }
    }
    @Test fun `code cannot replace trigger context or emit unbounded data`() {
        listOf("return {\"trigger.action\": \"spoofed\"}", "return {\"__proto__\": \"spoofed\"}", "return {\"x\": 1 / 0}", "return {\"x\": number(\"NaN\")}", "return {\"x\": \""+"a".repeat(501)+"\"}").forEach { statement ->
            assertThatThrownBy { WorkflowCode.execute("def run(context):\n    $statement",emptyMap()) }.isInstanceOf(BadRequestException::class.java)
        }
        assertThatThrownBy { WorkflowCode.validate("def run(context):\n"+"    x = 1\n".repeat(66)+"    return {}") }.isInstanceOf(BadRequestException::class.java)
    }
}
