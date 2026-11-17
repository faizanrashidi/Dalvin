package com.example.ui.auth

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.util.Log

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.VerifiedUser
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp

import androidx.credentials.ClearCredentialStateRequest
import androidx.credentials.CredentialManager
import androidx.credentials.CustomCredential
import androidx.credentials.GetCredentialRequest
import androidx.credentials.exceptions.GetCredentialCancellationException

import com.example.R
import com.google.android.libraries.identity.googleid.GetGoogleIdOption
import com.google.android.libraries.identity.googleid.GetSignInWithGoogleOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential.Companion.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL

import com.google.firebase.Firebase
import com.google.firebase.auth.FirebaseAuthException
import com.google.firebase.auth.GoogleAuthProvider
import com.google.firebase.auth.auth

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext

private const val AUTH_TAG = "NOVA_AUTH"

/* Find Activity from Context */
private tailrec fun Context.findActivity(): Activity? {
    return when (this) {
        is Activity -> this
        is ContextWrapper -> baseContext.findActivity()
        else -> null
    }
}

/* MAIN AUTH SCREEN */
@Composable
fun AuthScreen(
    onAuthSuccess: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    val credentialManager = remember(context) {
        CredentialManager.create(context)
    }

    var isLoading by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(Unit) {
        attemptAutoSignIn(
            context = context,
            credentialManager = credentialManager,
            onAuthSuccess = onAuthSuccess,
            onUnauthenticated = {},
            scope = scope
        )
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    listOf(
                        MaterialTheme.colorScheme.background,
                        MaterialTheme.colorScheme.surface
                    )
                )
            )
            .padding(24.dp),
        contentAlignment = Alignment.Center
    ) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .widthIn(max = 460.dp),
            shape = RoundedCornerShape(28.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(
                    alpha = 0.85f
                )
            )
        ) {
            Column(
                modifier = Modifier.padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                Image(
                    painter = painterResource(
                        id = R.drawable.img_nova_banner_1794852520758
                    ),
                    contentDescription = "NOVA neural voice banner",
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(140.dp)
                        .clip(RoundedCornerShape(18.dp)),
                    contentScale = ContentScale.Crop
                )

                Surface(
                    modifier = Modifier.size(64.dp),
                    shape = CircleShape,
                    color = MaterialTheme.colorScheme.primary.copy(alpha = 0.16f)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = Icons.Filled.GraphicEq,
                            contentDescription = "NOVA Voice emblem",
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(34.dp)
                        )
                    }
                }

                Text(
                    text = "NOVA VOICE AI",
                    style = MaterialTheme.typography.headlineMedium,
                    color = MaterialTheme.colorScheme.primary
                )

                Text(
                    text = "Hindi · Hinglish · English Voice Intelligence\n155 Features · 14 Categories · Zero-Trust Security",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center
                )

                RowBadge()

                if (!errorMessage.isNullOrBlank()) {
                    Text(
                        text = errorMessage!!,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.error,
                        textAlign = TextAlign.Center
                    )
                }

                Spacer(modifier = Modifier.height(4.dp))

                Button(
                    onClick = {
                        errorMessage = null
                        isLoading = true

                        onGoogleSignInClicked(
                            context = context,
                            credentialManager = credentialManager,
                            onAuthSuccess = {
                                isLoading = false
                                onAuthSuccess()
                            },
                            onAuthError = { message ->
                                isLoading = false
                                errorMessage = message
                            },
                            scope = scope,
                            onAuthCancelled = {
                                isLoading = false
                            }
                        )
                    },
                    enabled = !isLoading,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(54.dp)
                        .testTag("google_sign_in_button"),
                    shape = RoundedCornerShape(16.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.primary,
                        contentColor = MaterialTheme.colorScheme.onPrimary
                    )
                ) {
                    if (isLoading) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(22.dp),
                            color = MaterialTheme.colorScheme.onPrimary,
                            strokeWidth = 2.dp
                        )
                    } else {
                        Text(
                            "Sign in with Google",
                            style = MaterialTheme.typography.titleMedium
                        )
                    }
                }
            }
        }
    }
}

/* AUTH BADGE */
@Composable
private fun RowBadge() {
    Surface(
        shape = RoundedCornerShape(50),
        color = MaterialTheme.colorScheme.secondaryContainer
    ) {
        androidx.compose.foundation.layout.Row(
            modifier = Modifier.padding(
                horizontal = 12.dp,
                vertical = 6.dp
            ),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Icon(
                imageVector = Icons.Filled.VerifiedUser,
                contentDescription = "Protected sync",
                modifier = Modifier.size(16.dp),
                tint = MaterialTheme.colorScheme.onSecondaryContainer
            )

            Text(
                text = "Cloud Sync + Gemini AI Logic",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSecondaryContainer
            )
        }
    }
}

/* AUTO SIGN-IN */
fun attemptAutoSignIn(
    context: Context,
    credentialManager: CredentialManager,
    onAuthSuccess: () -> Unit,
    onUnauthenticated: () -> Unit,
    scope: CoroutineScope
) {
    if (Firebase.auth.currentUser != null) {
        Log.d(AUTH_TAG, "Existing Firebase session found")
        onAuthSuccess()
        return
    }

    val clientId = try {
        context.getString(R.string.default_web_client_id)
    } catch (e: Exception) {
        Log.e(AUTH_TAG, "Web Client ID missing", e)
        onUnauthenticated()
        return
    }

    if (clientId.isBlank()) {
        Log.e(AUTH_TAG, "Web Client ID is blank")
        onUnauthenticated()
        return
    }

    val option = GetGoogleIdOption.Builder()
        .setFilterByAuthorizedAccounts(true)
        .setServerClientId(clientId)
        .setAutoSelectEnabled(true)
        .build()

    val request = GetCredentialRequest.Builder()
        .addCredentialOption(option)
        .build()

    scope.launch {
        try {
            Log.d(AUTH_TAG, "Starting Auto Sign-In")

            val result = credentialManager.getCredential(
                context,
                request
            )

            val credential = result.credential

            if (
                credential is CustomCredential &&
                credential.type == TYPE_GOOGLE_ID_TOKEN_CREDENTIAL
            ) {
                val googleCredential =
                    GoogleIdTokenCredential.createFrom(credential.data)

                val authCredential = GoogleAuthProvider.getCredential(
                    googleCredential.idToken,
                    null
                )

                Log.d(AUTH_TAG, "Auto Sign-In: Starting Firebase")

                val authResult = Firebase.auth
                    .signInWithCredential(authCredential)
                    .await()

                Log.d(AUTH_TAG, "Auto Sign-In: Firebase completed")

                if (authResult.user != null) {
                    Log.d(AUTH_TAG, "Auto Sign-In successful")
                    onAuthSuccess()
                } else {
                    Log.e(AUTH_TAG, "Auto Sign-In returned null user")
                    onUnauthenticated()
                }
            } else {
                Log.w(AUTH_TAG, "Unexpected auto credential type")
                onUnauthenticated()
            }

        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.e(AUTH_TAG, "Auto Sign-In failed", e)
            onUnauthenticated()
        }
    }
}

/* MANUAL GOOGLE SIGN-IN */
fun onGoogleSignInClicked(
    context: Context,
    credentialManager: CredentialManager,
    onAuthSuccess: () -> Unit,
    onAuthError: (String) -> Unit,
    scope: CoroutineScope,
    onAuthCancelled: () -> Unit
) {
    val clientId = try {
        context.getString(R.string.default_web_client_id)
    } catch (e: Exception) {
        Log.e(AUTH_TAG, "default_web_client_id missing", e)
        onAuthError("Google Sign-In configuration missing: default_web_client_id")
        return
    }

    if (clientId.isBlank()) {
        onAuthError("Google Web Client ID is empty.")
        return
    }

    val activity = context.findActivity()

    if (activity == null) {
        onAuthError("Activity context required.")
        return
    }

    val signInOption = GetSignInWithGoogleOption.Builder(
        serverClientId = clientId
    ).build()

    val request = GetCredentialRequest.Builder()
        .addCredentialOption(signInOption)
        .build()

    scope.launch {
        try {
            Log.d(AUTH_TAG, "Starting Google Sign-In")

            val result = credentialManager.getCredential(
                activity,
                request
            )

            Log.d(AUTH_TAG, "Google Credential received")

            val credential = result.credential

            if (
                credential !is CustomCredential ||
                credential.type != TYPE_GOOGLE_ID_TOKEN_CREDENTIAL
            ) {
                Log.e(AUTH_TAG, "Unexpected credential type: ${credential.type}")
                onAuthError("Unexpected Google credential type.")
                return@launch
            }

            val googleCredential =
                GoogleIdTokenCredential.createFrom(credential.data)

            val idToken = googleCredential.idToken

            if (idToken.isBlank()) {
                onAuthError("Google returned an empty ID Token.")
                return@launch
            }

            Log.d(AUTH_TAG, "Google ID Token received")

            val authCredential = GoogleAuthProvider.getCredential(
                idToken,
                null
            )

            Log.d(AUTH_TAG, "Starting Firebase Authentication")

            val authResult = Firebase.auth
                .signInWithCredential(authCredential)
                .await()

            Log.d(AUTH_TAG, "Firebase Authentication completed")

            val user = authResult.user

            if (user != null) {
                Log.d(AUTH_TAG, "Firebase Google Sign-In successful")
                Log.d(AUTH_TAG, "UID: ${user.uid}")
                onAuthSuccess()
            } else {
                Log.e(AUTH_TAG, "Firebase returned null user")
                onAuthError("Firebase authentication returned no user.")
            }

        } catch (e: GetCredentialCancellationException) {
            Log.w(AUTH_TAG, "Google Sign-In cancelled", e)
            onAuthCancelled()

        } catch (e: CancellationException) {
            Log.w(AUTH_TAG, "Sign-In coroutine cancelled", e)
            throw e

        } catch (e: Exception) {
            Log.e(AUTH_TAG, "Google Sign-In failed", e)

            val error = buildString {
                append("Error: ${e.javaClass.simpleName}")
                append("\nMessage: ${e.localizedMessage ?: "Unknown error"}")

                if (e is FirebaseAuthException) {
                    append("\nFirebase Code: ${e.errorCode}")
                }
            }

            onAuthError(error)
        }
    }
}

/* SIGN OUT */
fun signOutUser(
    credentialManager: CredentialManager,
    scope: CoroutineScope,
    onComplete: () -> Unit = {}
) {
    scope.launch {
        withContext(NonCancellable) {
            try {
                credentialManager.clearCredentialState(
                    ClearCredentialStateRequest()
                )
            } catch (e: Exception) {
                Log.w(AUTH_TAG, "Failed to clear credential state", e)
            } finally {
                Firebase.auth.signOut()
                Log.d(AUTH_TAG, "Firebase Sign-Out completed")
                onComplete()
            }
        }
    }
}
