package com.example.data

import com.example.base.FirestoreEmulatorTestBase
import com.google.firebase.firestore.FirebaseFirestoreException
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

class NovaRepositoryRuleTest : FirestoreEmulatorTestBase() {

    @Test
    fun createNote_andObserve_authenticatedOwner_succeeds() = runBlocking {
        val uid = signInTestUser(ALICE_EMAIL)
        val repo = NovaRepository(firestore)

        val noteId = withTimeout(DEFAULT_TIMEOUT_MS) {
            repo.addNote(uid, "Meeting", "Kal subah 10 baje", "hinglish", true)
        }
        assertTrue(noteId.isNotEmpty())

        val notes = withTimeout(FLOW_TIMEOUT_MS) {
            repo.observeNotes(uid).first { list -> list.any { it.id == noteId } }
        }
        assertTrue(notes.any { it.id == noteId && it.title == "Meeting" })
    }

    @Test
    fun createTask_andAudit_authenticatedOwner_succeeds() = runBlocking {
        val uid = signInTestUser(ALICE_EMAIL)
        val repo = NovaRepository(firestore)

        val taskId = withTimeout(DEFAULT_TIMEOUT_MS) {
            repo.addTask(uid, "Call Mom", "high")
        }
        val auditId = withTimeout(DEFAULT_TIMEOUT_MS) {
            repo.logAudit(uid, "TASK_CREATED", "Added Call Mom", true)
        }
        assertTrue(taskId.isNotEmpty() && auditId.isNotEmpty())
    }

    @Test
    fun crossUserAccess_notes_failsWithPermissionDenied() = runBlocking {
        val aliceUid = signInTestUser(ALICE_EMAIL)
        val repo = NovaRepository(firestore)
        withTimeout(DEFAULT_TIMEOUT_MS) {
            repo.addNote(aliceUid, "Alice Private", "Secret", "en")
        }

        signInTestUser(BOB_EMAIL)
        try {
            withTimeout(FLOW_TIMEOUT_MS) {
                repo.observeNotes(aliceUid).first()
            }
            fail("Expected PERMISSION_DENIED for cross-user access")
        } catch (e: Exception) {
            val fsError = e.unwrapFirestoreException()
            assertEquals(FirebaseFirestoreException.Code.PERMISSION_DENIED, fsError?.code)
        }
    }

    @Test
    fun unauthenticatedUser_observeTasks_failsWithPermissionDenied() = runBlocking {
        auth.signOut()
        val repo = NovaRepository(firestore)
        try {
            withTimeout(FLOW_TIMEOUT_MS) {
                repo.observeTasks("alice_fake_uid").first()
            }
            fail("Expected PERMISSION_DENIED for unauthenticated user")
        } catch (e: Exception) {
            val fsError = e.unwrapFirestoreException()
            assertEquals(FirebaseFirestoreException.Code.PERMISSION_DENIED, fsError?.code)
        }
    }

    private companion object {
        const val ALICE_EMAIL = "nova_alice@test.com"
        const val BOB_EMAIL = "nova_bob@test.com"
        const val DEFAULT_TIMEOUT_MS = 5000L
        const val FLOW_TIMEOUT_MS = 3000L
    }
}
