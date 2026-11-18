package com.example

import android.app.Activity
import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.cloud.drive.DriveConnectionStatus
import com.example.cloud.drive.GoogleAccountManager
import com.example.cloud.drive.GoogleAuthorizationManager
import com.google.android.gms.common.api.ApiException
import com.google.android.gms.common.api.CommonStatusCodes
import com.google.android.gms.common.api.Status
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [29])
class GoogleAuthorizationManagerTest {

    private lateinit var context: Context
    private lateinit var accountManager: GoogleAccountManager
    private lateinit var authManager: GoogleAuthorizationManager

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        accountManager = GoogleAccountManager(context)
        authManager = GoogleAuthorizationManager(context, accountManager)
    }

    @Test
    fun `OAuth scope targets drive file for visible Drive backups`() {
        assertEquals("https://www.googleapis.com/auth/drive.file", GoogleAuthorizationManager.DRIVE_FILE_SCOPE)
    }

    @Test
    fun `handleResolutionActivityResult returns ConsentCancelled on RESULT_CANCELED with null data`() {
        val status = authManager.handleResolutionActivityResult(Activity.RESULT_CANCELED, null)
        assertTrue(status is DriveConnectionStatus.ConsentCancelled)
    }

    @Test
    fun `handleResolutionActivityResult returns Error on unexpected non-OK result code with null data`() {
        val status = authManager.handleResolutionActivityResult(Activity.RESULT_FIRST_USER, null)
        assertTrue(status is DriveConnectionStatus.Error)
    }

    @Test
    fun `mapThrowableToStatus distinguishes CANCELED and 12501 from DEVELOPER_ERROR and 12500`() {
        val canceled = authManager.mapThrowableToStatus(ApiException(Status(CommonStatusCodes.CANCELED, "Canceled by user")))
        assertTrue(canceled is DriveConnectionStatus.ConsentCancelled)

        val signInCanceled = authManager.mapThrowableToStatus(ApiException(Status(12501, "12501: User cancelled")))
        assertTrue(signInCanceled is DriveConnectionStatus.ConsentCancelled)

        val devError = authManager.mapThrowableToStatus(ApiException(Status(CommonStatusCodes.DEVELOPER_ERROR, "10: Unknown calling package")))
        assertTrue(devError is DriveConnectionStatus.SetupRequired)
        assertTrue((devError as DriveConnectionStatus.SetupRequired).reason.contains(context.packageName))
        assertEquals(context.packageName, devError.packageName)

        val unregisteredError = authManager.mapThrowableToStatus(
            ApiException(Status(CommonStatusCodes.INTERNAL_ERROR, "8: [8] unknown error [status=UNREGISTERED_ON_API_CONSOLE]"))
        )
        assertTrue(unregisteredError is DriveConnectionStatus.SetupRequired)
        assertEquals(context.packageName, (unregisteredError as DriveConnectionStatus.SetupRequired).packageName)

        val signInFailed = authManager.mapThrowableToStatus(ApiException(Status(12500, "12500: Sign in failed")))
        assertTrue(signInFailed is DriveConnectionStatus.SetupRequired)

        val networkError = authManager.mapThrowableToStatus(ApiException(Status(CommonStatusCodes.NETWORK_ERROR, "Network error")))
        assertTrue(networkError is DriveConnectionStatus.Error)

        val apiNotConnected = authManager.mapThrowableToStatus(ApiException(Status(CommonStatusCodes.API_NOT_CONNECTED, "Play Services disconnected")))
        assertTrue(apiNotConnected is DriveConnectionStatus.PlayServicesUnavailable)

        val signInRequired = authManager.mapThrowableToStatus(ApiException(Status(CommonStatusCodes.SIGN_IN_REQUIRED, "Sign in required")))
        assertTrue(signInRequired is DriveConnectionStatus.Error)
        assertTrue((signInRequired as DriveConnectionStatus.Error).message.contains("signed in"))
    }

    @Test
    fun `checkOrRequestAuthorization returns PlayServicesUnavailable when Play Services is absent in JVM environment`() = runBlocking {
        assertFalse(accountManager.isPlayServicesAvailable())
        val status = authManager.checkOrRequestAuthorization(savedEmail = "user@example.com", interactive = false)
        assertEquals(DriveConnectionStatus.PlayServicesUnavailable, status)
    }
}
