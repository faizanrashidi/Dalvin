package com.example.core

import android.util.Log
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestoreException
import kotlinx.coroutines.CancellationException
import org.json.JSONArray
import org.json.JSONObject

enum class OperationType(val value: String) {
    CREATE("create"),
    UPDATE("update"),
    DELETE("delete"),
    LIST("list"),
    GET("get"),
    WRITE("write")
}

fun handleFirestoreError(
    exception: Throwable,
    operationType: OperationType,
    path: String?
): String {
    val causes = generateSequence(exception) { it.cause }
    val rootError = causes.firstOrNull { it is FirebaseFirestoreException }
        ?: causes.firstOrNull { it !is CancellationException }
        ?: exception

    val auth = try {
        FirebaseAuth.getInstance()
    } catch (_: Exception) {
        null
    }
    val currentUser = auth?.currentUser

    val providerInfoList = currentUser?.providerData?.map { provider ->
        JSONObject().apply {
            put("providerId", provider.providerId)
            put("email", provider.email)
        }
    } ?: emptyList()

    val authInfoJson = JSONObject().apply {
        put("userId", currentUser?.uid)
        put("email", currentUser?.email)
        put("emailVerified", currentUser?.isEmailVerified)
        put("tenantId", currentUser?.tenantId)
        put("providerInfo", JSONArray(providerInfoList))
    }

    val errorInfoJson = JSONObject().apply {
        put("error", rootError.message ?: rootError.toString())
        put("operationType", operationType.value)
        put("path", path)
        put("authInfo", authInfoJson)
    }

    val jsonString = errorInfoJson.toString()
    Log.e("FirestoreError", "Firestore Error: $jsonString")
    return jsonString
}

sealed interface NovaError {
    val message: String
    val recoveryHint: String

    data class PermissionMissing(
        val permission: String,
        override val message: String,
        override val recoveryHint: String = "Grant permission in NOVA Permission Dashboard"
    ) : NovaError

    data class NetworkFailure(
        override val message: String,
        override val recoveryHint: String = "Check Wi-Fi/Mobile Data or switch to offline command"
    ) : NovaError

    data class AndroidRestriction(
        override val message: String,
        override val recoveryHint: String = "Android 16 restricts direct silent execution; using legitimate system panel/intent"
    ) : NovaError

    data class GeneralFailure(
        override val message: String,
        override val recoveryHint: String = "Try rephrasing your command or run Self-Diagnostics"
    ) : NovaError
}

object ErrorHandler {
    private const val TAG = "NovaErrorHandler"

    fun classify(throwable: Throwable, contextHint: String = ""): NovaError {
        if (throwable is CancellationException) throw throwable
        Log.e(TAG, "Error in $contextHint: ${throwable.message}", throwable)
        val msg = throwable.localizedMessage ?: "Unexpected error"
        return when {
            throwable is SecurityException -> NovaError.PermissionMissing(
                permission = contextHint,
                message = "Permission required: $msg"
            )
            msg.contains("network", ignoreCase = true) ||
                msg.contains("timeout", ignoreCase = true) ||
                msg.contains("Unable to resolve host", ignoreCase = true) ->
                NovaError.NetworkFailure(message = msg)
            else -> NovaError.GeneralFailure(message = msg)
        }
    }
}
