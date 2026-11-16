package com.example.data

import android.content.Context
import com.example.R
import com.example.core.AutomationRoutine
import com.example.core.CommandHistoryItem
import com.example.core.NovaMemoryItem
import com.example.core.NovaNote
import com.example.core.NovaTask
import com.example.core.OperationType
import com.example.core.SecurityAuditEntry
import com.example.core.handleFirestoreError
import com.google.firebase.Timestamp
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreException
import com.google.firebase.firestore.Query
import com.google.firebase.firestore.snapshots
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.tasks.await
import java.util.UUID

class NovaRepository(private val db: FirebaseFirestore) {

    constructor(context: Context) : this(
        FirebaseFirestore.getInstance(
            context.applicationContext.getString(R.string.firestore_database_id)
        )
    )

    private fun userRoot(userId: String) = db.collection("users").document(userId)

    private fun newDocId(): String = UUID.randomUUID().toString()

    // --- NOTES ---
    fun observeNotes(userId: String): Flow<List<NovaNote>> =
        userRoot(userId).collection("notes")
            .orderBy("createdAt", Query.Direction.DESCENDING)
            .snapshots()
            .map { snapshot ->
                snapshot.toObjects(NovaNote::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
            }
            .catch { error ->
                unwrapAndLog(error, OperationType.LIST, "users/$userId/notes")
                throw error
            }

    suspend fun addNote(
        userId: String,
        title: String,
        content: String,
        language: String = "hinglish",
        pinned: Boolean = false
    ): String {
        val docId = newDocId()
        val ref = userRoot(userId).collection("notes").document(docId)
        val payload = mapOf(
            "userId" to userId,
            "title" to title.trim().take(200).ifEmpty { "Voice Note" },
            "content" to content.trim().take(5000),
            "language" to normalizeLanguage(language),
            "pinned" to pinned,
            "createdAt" to FieldValue.serverTimestamp(),
            "updatedAt" to FieldValue.serverTimestamp()
        )
        return executeWrite(OperationType.CREATE, ref.path) {
            ref.set(payload).await()
            docId
        }
    }

    suspend fun toggleNotePin(userId: String, note: NovaNote) {
        val ref = userRoot(userId).collection("notes").document(note.id)
        executeWrite(OperationType.UPDATE, ref.path) {
            ref.update(
                mapOf(
                    "pinned" to !note.pinned,
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            ).await()
        }
    }

    suspend fun deleteNote(userId: String, noteId: String) {
        val ref = userRoot(userId).collection("notes").document(noteId)
        executeWrite(OperationType.DELETE, ref.path) { ref.delete().await() }
    }

    // --- TASKS ---
    fun observeTasks(userId: String): Flow<List<NovaTask>> =
        userRoot(userId).collection("tasks")
            .orderBy("createdAt", Query.Direction.DESCENDING)
            .snapshots()
            .map { snapshot ->
                snapshot.toObjects(NovaTask::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
            }
            .catch { error ->
                unwrapAndLog(error, OperationType.LIST, "users/$userId/tasks")
                throw error
            }

    suspend fun addTask(
        userId: String,
        title: String,
        priority: String = "normal",
        dueAtMillis: Long = 0L
    ): String {
        val docId = newDocId()
        val ref = userRoot(userId).collection("tasks").document(docId)
        val safePriority = if (priority in listOf("low", "normal", "high")) priority else "normal"
        val payload = mapOf(
            "userId" to userId,
            "title" to title.trim().take(200).ifEmpty { "Task" },
            "completed" to false,
            "dueAtMillis" to dueAtMillis.coerceAtLeast(0L),
            "priority" to safePriority,
            "createdAt" to FieldValue.serverTimestamp(),
            "updatedAt" to FieldValue.serverTimestamp()
        )
        return executeWrite(OperationType.CREATE, ref.path) {
            ref.set(payload).await()
            docId
        }
    }

    suspend fun toggleTaskCompleted(userId: String, task: NovaTask) {
        val ref = userRoot(userId).collection("tasks").document(task.id)
        executeWrite(OperationType.UPDATE, ref.path) {
            ref.update(
                mapOf(
                    "completed" to !task.completed,
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            ).await()
        }
    }

    suspend fun deleteTask(userId: String, taskId: String) {
        val ref = userRoot(userId).collection("tasks").document(taskId)
        executeWrite(OperationType.DELETE, ref.path) { ref.delete().await() }
    }

    // --- COMMAND HISTORY ---
    fun observeHistory(userId: String): Flow<List<CommandHistoryItem>> =
        userRoot(userId).collection("history")
            .orderBy("createdAt", Query.Direction.DESCENDING)
            .limit(80)
            .snapshots()
            .map { snapshot ->
                snapshot.toObjects(CommandHistoryItem::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
            }
            .catch { error ->
                unwrapAndLog(error, OperationType.LIST, "users/$userId/history")
                throw error
            }

    suspend fun addHistory(
        userId: String,
        utterance: String,
        response: String,
        intent: String,
        language: String,
        success: Boolean
    ): String {
        val docId = newDocId()
        val ref = userRoot(userId).collection("history").document(docId)
        val payload = mapOf(
            "userId" to userId,
            "utterance" to utterance.trim().take(1000).ifEmpty { "command" },
            "response" to response.trim().take(4000),
            "intent" to intent.trim().take(100).ifEmpty { "general" },
            "language" to normalizeLanguage(language),
            "success" to success,
            "createdAt" to FieldValue.serverTimestamp()
        )
        return executeWrite(OperationType.CREATE, ref.path) {
            ref.set(payload).await()
            docId
        }
    }

    // --- AI MEMORIES ---
    fun observeMemories(userId: String): Flow<List<NovaMemoryItem>> =
        userRoot(userId).collection("memories")
            .orderBy("createdAt", Query.Direction.DESCENDING)
            .snapshots()
            .map { snapshot ->
                snapshot.toObjects(NovaMemoryItem::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
            }
            .catch { error ->
                unwrapAndLog(error, OperationType.LIST, "users/$userId/memories")
                throw error
            }

    suspend fun addMemory(userId: String, key: String, value: String): String {
        val docId = newDocId()
        val ref = userRoot(userId).collection("memories").document(docId)
        val payload = mapOf(
            "userId" to userId,
            "key" to key.trim().take(100).ifEmpty { "fact" },
            "value" to value.trim().take(500).ifEmpty { "-" },
            "createdAt" to FieldValue.serverTimestamp(),
            "updatedAt" to FieldValue.serverTimestamp()
        )
        return executeWrite(OperationType.CREATE, ref.path) {
            ref.set(payload).await()
            docId
        }
    }

    suspend fun deleteMemory(userId: String, memoryId: String) {
        val ref = userRoot(userId).collection("memories").document(memoryId)
        executeWrite(OperationType.DELETE, ref.path) { ref.delete().await() }
    }

    // --- AUTOMATION ROUTINES ---
    fun observeRoutines(userId: String): Flow<List<AutomationRoutine>> =
        userRoot(userId).collection("routines")
            .orderBy("createdAt", Query.Direction.DESCENDING)
            .snapshots()
            .map { snapshot ->
                snapshot.toObjects(AutomationRoutine::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
            }
            .catch { error ->
                unwrapAndLog(error, OperationType.LIST, "users/$userId/routines")
                throw error
            }

    suspend fun addRoutine(
        userId: String,
        name: String,
        triggerPhrase: String,
        steps: List<String>
    ): String {
        val docId = newDocId()
        val ref = userRoot(userId).collection("routines").document(docId)
        val safeSteps = steps.map { it.trim().take(300) }.filter { it.isNotEmpty() }.take(10)
            .ifEmpty { listOf("battery status") }
        val payload = mapOf(
            "userId" to userId,
            "name" to name.trim().take(100).ifEmpty { "Routine" },
            "triggerPhrase" to triggerPhrase.trim().take(200).ifEmpty { "run routine" },
            "steps" to safeSteps,
            "enabled" to true,
            "createdAt" to FieldValue.serverTimestamp(),
            "updatedAt" to FieldValue.serverTimestamp()
        )
        return executeWrite(OperationType.CREATE, ref.path) {
            ref.set(payload).await()
            docId
        }
    }

    suspend fun toggleRoutineEnabled(userId: String, routine: AutomationRoutine) {
        val ref = userRoot(userId).collection("routines").document(routine.id)
        executeWrite(OperationType.UPDATE, ref.path) {
            ref.update(
                mapOf(
                    "enabled" to !routine.enabled,
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            ).await()
        }
    }

    suspend fun deleteRoutine(userId: String, routineId: String) {
        val ref = userRoot(userId).collection("routines").document(routineId)
        executeWrite(OperationType.DELETE, ref.path) { ref.delete().await() }
    }

    // --- SECURITY AUDIT LOGS ---
    fun observeAuditLogs(userId: String): Flow<List<SecurityAuditEntry>> =
        userRoot(userId).collection("auditLogs")
            .orderBy("createdAt", Query.Direction.DESCENDING)
            .limit(100)
            .snapshots()
            .map { snapshot ->
                snapshot.toObjects(SecurityAuditEntry::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
            }
            .catch { error ->
                unwrapAndLog(error, OperationType.LIST, "users/$userId/auditLogs")
                throw error
            }

    suspend fun logAudit(userId: String, action: String, detail: String, approved: Boolean): String {
        val docId = newDocId()
        val ref = userRoot(userId).collection("auditLogs").document(docId)
        val payload = mapOf(
            "userId" to userId,
            "action" to action.trim().take(150).ifEmpty { "ACTION" },
            "detail" to detail.trim().take(1000),
            "approved" to approved,
            "createdAt" to FieldValue.serverTimestamp()
        )
        return executeWrite(OperationType.CREATE, ref.path) {
            ref.set(payload).await()
            docId
        }
    }

    suspend fun wipeHistoryAndMemory(userId: String) {
        val root = userRoot(userId)
        val collections = listOf("history", "memories")
        for (col in collections) {
            val snap = try {
                root.collection(col).get().await()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                handleFirestoreError(e, OperationType.LIST, "users/$userId/$col")
                throw e
            }
            for (doc in snap.documents) {
                executeWrite(OperationType.DELETE, doc.reference.path) {
                    doc.reference.delete().await()
                }
            }
        }
    }

    private suspend inline fun <T> executeWrite(
        op: OperationType,
        path: String,
        crossinline block: suspend () -> T
    ): T {
        return try {
            block()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, op, path)
            throw e
        }
    }

    private fun unwrapAndLog(error: Throwable, op: OperationType, path: String) {
        val fsError = generateSequence(error) { it.cause }
            .filterIsInstance<FirebaseFirestoreException>()
            .firstOrNull()
        if (fsError == null && error is CancellationException) throw error
        handleFirestoreError(fsError ?: error, op, path)
    }

    private fun normalizeLanguage(lang: String): String =
        if (lang in listOf("en", "hi", "hinglish")) lang else "hinglish"
}
