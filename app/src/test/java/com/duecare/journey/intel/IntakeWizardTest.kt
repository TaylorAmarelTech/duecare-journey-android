package com.duecare.journey.intel

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class IntakeWizardTest {

    @Test
    fun all_questions_have_unique_ids() {
        val ids = IntakeWizard.ALL.map { it.id }
        assertEquals("Duplicate question id detected", ids.size, ids.toSet().size)
    }

    @Test
    fun all_questions_have_nonblank_required_fields() {
        IntakeWizard.ALL.forEach {
            assertTrue("Question ${it.id} has blank prompt", it.prompt.isNotBlank())
            assertTrue("Question ${it.id} has blank category", it.category.isNotBlank())
            assertTrue("Question ${it.id} has blank helperText", it.helperText.isNotBlank())
            assertTrue("Question ${it.id} has blank journalTitlePrefix", it.journalTitlePrefix.isNotBlank())
        }
    }

    @Test
    fun toDraft_returns_null_for_blank_answer() {
        val q = IntakeWizard.ALL.first()
        assertNull(IntakeWizard.toDraft(q, ""))
        assertNull(IntakeWizard.toDraft(q, "   "))
    }

    @Test
    fun toDraft_packs_answer_into_journal_entry_body() {
        val q = IntakeWizard.ALL.first { it.id == "passport-status" }
        val draft = IntakeWizard.toDraft(q, "with the recruiter for safekeeping")
        assertNotNull(draft)
        assertTrue(draft!!.title.startsWith("Passport status:"))
        assertTrue(draft.body.contains("Q: ${q.prompt}"))
        assertTrue(draft.body.contains("A: with the recruiter for safekeeping"))
    }

    @Test
    fun questions_cover_critical_categories() {
        val cats = IntakeWizard.ALL.map { it.category }.toSet()
        assertTrue("Recruiter missing", "Recruiter" in cats)
        assertTrue("Fees missing", "Fees" in cats)
        assertTrue("Documents missing", "Documents" in cats)
        assertTrue("Conditions missing", "Conditions" in cats)
    }
}
