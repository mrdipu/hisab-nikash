package com.example.cloud.drive

import android.accounts.Account
import android.app.Activity
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import com.google.android.gms.auth.api.identity.AuthorizationRequest
import com.google.android.gms.auth.api.identity.AuthorizationResult
import com.google.android.gms.auth.api.identity.Identity
import com.google.android.gms.common.ConnectionResult
import com.google.android.gms.common.GoogleApiAvailability
import com.google.android.gms.common.api.ApiException
import com.google.android.gms.common.api.CommonStatusCodes
import com.google.android.gms.common.api.Scope
import kotlinx.coroutines.suspendCancellableCoroutine
import java.security.MessageDigest
import kotlin.coroutines.resume

data class DriveBackupFile(
    val id: String,
    val name: String,
    val modifiedTimeIso: String,
    val sizeBytes: Long
)

sealed class DriveConnectionStatus {
    data object PlayServicesUnavailable : DriveConnectionStatus()
    data class SetupRequired(
        val reason: String,
        val packageName: String = "",
        val sha1Fingerprint: String? = null
    ) : DriveConnectionStatus()
    data object NotAuthorized : DriveConnectionStatus()
    data class ConsentCancelled(val detail: String? = null) : DriveConnectionStatus()
    data class ResolutionRequired(
        val pendingIntent: PendingIntent,
        val requestId: Long = System.nanoTime()
    ) : DriveConnectionStatus()
    data class Authorized(val accessToken: String, val accountEmail: String) : DriveConnectionStatus()
    data class Error(val message: String) : DriveConnectionStatus()
}

class GoogleAccountManager(
    private val context: Context
) {
    fun isPlayServicesAvailable(): Boolean {
        val status = GoogleApiAvailability.getInstance().isGooglePlayServicesAvailable(context)
        return status == ConnectionResult.SUCCESS
    }
}

class GoogleAuthorizationManager(
    private val context: Context,
    private val accountManager: GoogleAccountManager
) {
    companion object {
        const val DRIVE_FILE_SCOPE = "https://www.googleapis.com/auth/drive.file"
        private const val GOOGLE_ACCOUNT_TYPE = "com.google"
        private const val SIGN_IN_FAILED_CODE = 12500
        private const val SIGN_IN_CANCELLED_CODE = 12501
    }

    /**
     * Checks or requests Google Drive authorization (`drive.file` scope for visible backups).
     *
     * When [interactive] is false (screen open / background WorkManager), a pending consent
     * resolution returns [DriveConnectionStatus.NotAuthorized] instead of auto-launching
     * an interactive consent prompt.
     */
    suspend fun checkOrRequestAuthorization(
        savedEmail: String = "",
        interactive: Boolean = false
    ): DriveConnectionStatus {
        if (!accountManager.isPlayServicesAvailable()) {
            return DriveConnectionStatus.PlayServicesUnavailable
        }

        val firstAttempt = authorizeInternal(savedEmail = savedEmail.trim(), interactive = interactive)
        // If a previously saved email is no longer present or valid on the device, retry without account hint during interactive login.
        if (interactive && savedEmail.isNotBlank() && firstAttempt is DriveConnectionStatus.Error) {
            return authorizeInternal(savedEmail = "", interactive = true)
        }
        return firstAttempt
    }

    private suspend fun authorizeInternal(
        savedEmail: String,
        interactive: Boolean
    ): DriveConnectionStatus {
        val requestBuilder = AuthorizationRequest.builder()
            .setRequestedScopes(listOf(Scope(DRIVE_FILE_SCOPE)))
        if (savedEmail.isNotBlank()) {
            requestBuilder.setAccount(Account(savedEmail, GOOGLE_ACCOUNT_TYPE))
        }

        return suspendCancellableCoroutine { cont ->
            val client = Identity.getAuthorizationClient(context)
            client.authorize(requestBuilder.build())
                .addOnSuccessListener { result: AuthorizationResult ->
                    if (!cont.isActive) return@addOnSuccessListener
                    when {
                        !result.accessToken.isNullOrBlank() -> {
                            val email = result.toGoogleSignInAccount()?.email.orEmpty().ifBlank { savedEmail }
                            cont.resume(DriveConnectionStatus.Authorized(result.accessToken!!, email))
                        }
                        result.hasResolution() && result.pendingIntent != null -> {
                            if (interactive) {
                                cont.resume(DriveConnectionStatus.ResolutionRequired(result.pendingIntent!!))
                            } else {
                                cont.resume(DriveConnectionStatus.NotAuthorized)
                            }
                        }
                        else -> {
                            cont.resume(
                                DriveConnectionStatus.SetupRequired(
                                    reason = "OAuth 2.0 Android client + SHA-1 must be configured in Google Cloud Console for ${context.packageName}.",
                                    packageName = context.packageName,
                                    sha1Fingerprint = getAppSigningSha1()
                                )
                            )
                        }
                    }
                }
                .addOnFailureListener { e ->
                    if (!cont.isActive) return@addOnFailureListener
                    cont.resume(mapThrowableToStatus(e))
                }
        }
    }

    fun handleResolutionActivityResult(
        resultCode: Int,
        data: Intent?,
        savedEmail: String = ""
    ): DriveConnectionStatus {
        if (data != null) {
            try {
                val result = Identity.getAuthorizationClient(context).getAuthorizationResultFromIntent(data)
                val token = result.accessToken
                if (resultCode == Activity.RESULT_OK) {
                    if (!token.isNullOrBlank()) {
                        val email = result.toGoogleSignInAccount()?.email.orEmpty().ifBlank { savedEmail }
                        return DriveConnectionStatus.Authorized(token, email)
                    }
                    return DriveConnectionStatus.Error("Google Drive authorization completed without an access token.")
                }
            } catch (t: Throwable) {
                return mapThrowableToStatus(t)
            }
        }

        return if (resultCode == Activity.RESULT_CANCELED) {
            DriveConnectionStatus.ConsentCancelled()
        } else {
            DriveConnectionStatus.Error("Google Drive authorization returned result code $resultCode.")
        }
    }

    fun mapThrowableToStatus(t: Throwable): DriveConnectionStatus {
        val apiException = t as? ApiException
        val statusCode = apiException?.statusCode
        val msg = t.message.orEmpty()

        return when {
            statusCode == CommonStatusCodes.CANCELED ||
                statusCode == SIGN_IN_CANCELLED_CODE -> {
                DriveConnectionStatus.ConsentCancelled(msg.takeIf { it.isNotBlank() })
            }
            isUnregisteredOrConfigError(statusCode, msg) -> {
                val sha1 = getAppSigningSha1()
                val sha1Note = if (sha1 != null) " | SHA-1: $sha1" else ""
                DriveConnectionStatus.SetupRequired(
                    reason = "Google Cloud Console Android OAuth 2.0 Client (${context.packageName}$sha1Note) is not registered or Drive API / Consent Screen is not configured: ${msg.ifBlank { "code $statusCode" }}",
                    packageName = context.packageName,
                    sha1Fingerprint = sha1
                )
            }
            statusCode == CommonStatusCodes.API_NOT_CONNECTED ||
                msg.contains("Play Services", ignoreCase = true) -> {
                DriveConnectionStatus.PlayServicesUnavailable
            }
            statusCode == CommonStatusCodes.SIGN_IN_REQUIRED -> {
                DriveConnectionStatus.Error("No Google account is signed in on this device, or Google sign-in is required.")
            }
            statusCode == CommonStatusCodes.NETWORK_ERROR -> {
                DriveConnectionStatus.Error("Network unavailable while connecting to Google Drive.")
            }
            else -> {
                DriveConnectionStatus.Error(msg.ifBlank { "Google Drive authorization failed (${statusCode ?: "unknown"})." })
            }
        }
    }

    private fun isUnregisteredOrConfigError(statusCode: Int?, msg: String): Boolean {
        return statusCode == CommonStatusCodes.DEVELOPER_ERROR ||
            statusCode == SIGN_IN_FAILED_CODE ||
            (statusCode == CommonStatusCodes.INTERNAL_ERROR &&
                (msg.contains("UNREGISTERED", ignoreCase = true) ||
                    msg.contains("API_CONSOLE", ignoreCase = true))) ||
            msg.contains("UNREGISTERED_ON_API_CONSOLE", ignoreCase = true) ||
            msg.contains("DEVELOPER_ERROR", ignoreCase = true) ||
            msg.contains("unknown calling package", ignoreCase = true) ||
            msg.contains("SHA-1", ignoreCase = true) ||
            msg.contains("SHA1", ignoreCase = true) ||
            msg.contains("10:")
    }

    fun getAppSigningSha1(): String? {
        return runCatching {
            val pm = context.packageManager
            val signatures = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                val info = pm.getPackageInfo(context.packageName, PackageManager.GET_SIGNING_CERTIFICATES)
                val signingInfo = info.signingInfo ?: return null
                if (signingInfo.hasMultipleSigners()) {
                    signingInfo.apkContentsSigners
                } else {
                    signingInfo.signingCertificateHistory
                }
            } else {
                @Suppress("DEPRECATION")
                pm.getPackageInfo(context.packageName, PackageManager.GET_SIGNATURES).signatures
            }
            val certBytes = signatures?.firstOrNull()?.toByteArray() ?: return null
            val digest = MessageDigest.getInstance("SHA-1").digest(certBytes)
            digest.joinToString(":") { byte -> "%02X".format(byte) }
        }.getOrNull()
    }
}
