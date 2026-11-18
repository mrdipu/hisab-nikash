package com.example.cloud.drive

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.net.URLEncoder
import java.util.concurrent.TimeUnit

/**
 * Google Drive REST API v3 adapter storing encrypted `.hnkb` backups inside a visible
 * `'Hisab Nikash Backups'` folder in the user's root Google Drive (`drive.file` scope).
 * Never stores fake local files as Drive backups; performs real HTTP requests when authorized.
 */
class DriveBackupRepository(
    private val httpClient: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(20, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .build()
) {
    companion object {
        const val BACKUP_FOLDER_NAME = "Hisab Nikash Backups"
        private const val FOLDER_MIME_TYPE = "application/vnd.google-apps.folder"
        private const val FILES_ENDPOINT = "https://www.googleapis.com/drive/v3/files"
        private const val UPLOAD_ENDPOINT = "https://www.googleapis.com/upload/drive/v3/files"
        private const val MAX_GENERATIONS = 3
    }

    private fun encodeQuery(q: String): String =
        URLEncoder.encode(q, Charsets.UTF_8.name()).replace("+", "%20")

    private fun findBackupFolderId(accessToken: String): Result<String?> {
        val escapedName = BACKUP_FOLDER_NAME.replace("'", "\\'")
        val query = "name = '$escapedName' and mimeType = '$FOLDER_MIME_TYPE' and 'root' in parents and trashed = false"
        val url = "$FILES_ENDPOINT?q=${encodeQuery(query)}&spaces=drive&fields=files(id,name)&orderBy=createdTime%20asc&pageSize=10"
        val req = Request.Builder()
            .url(url)
            .header("Authorization", "Bearer $accessToken")
            .get()
            .build()

        return httpClient.newCall(req).execute().use { resp ->
            val bodyStr = resp.body?.string().orEmpty()
            if (!resp.isSuccessful) {
                return Result.failure(mapHttpError(resp.code, bodyStr))
            }
            val filesArray = JSONObject(bodyStr).optJSONArray("files") ?: JSONArray()
            if (filesArray.length() == 0) {
                Result.success(null)
            } else {
                val id = filesArray.getJSONObject(0).optString("id")
                Result.success(id.takeIf { it.isNotBlank() })
            }
        }
    }

    private fun findOrCreateBackupFolderId(accessToken: String): Result<String> {
        val existing = findBackupFolderId(accessToken)
        if (existing.isFailure) {
            return Result.failure(existing.exceptionOrNull()!!)
        }
        val existingId = existing.getOrNull()
        if (!existingId.isNullOrBlank()) {
            return Result.success(existingId)
        }

        val folderMetadata = JSONObject().apply {
            put("name", BACKUP_FOLDER_NAME)
            put("mimeType", FOLDER_MIME_TYPE)
            put("parents", JSONArray().put("root"))
        }.toString()

        val req = Request.Builder()
            .url("$FILES_ENDPOINT?fields=id,name")
            .header("Authorization", "Bearer $accessToken")
            .post(folderMetadata.toRequestBody("application/json; charset=utf-8".toMediaType()))
            .build()

        return httpClient.newCall(req).execute().use { resp ->
            val bodyStr = resp.body?.string().orEmpty()
            if (!resp.isSuccessful) {
                return Result.failure(mapHttpError(resp.code, bodyStr))
            }
            val folderId = JSONObject(bodyStr).optString("id")
            if (folderId.isBlank()) {
                return Result.failure(IllegalStateException("Drive folder creation returned empty folder ID"))
            }
            Result.success(folderId)
        }
    }

    suspend fun listBackups(accessToken: String): Result<List<DriveBackupFile>> = withContext(Dispatchers.IO) {
        try {
            val folderRes = findBackupFolderId(accessToken)
            if (folderRes.isFailure) {
                return@withContext Result.failure(folderRes.exceptionOrNull()!!)
            }
            val folderId = folderRes.getOrNull()
                ?: return@withContext Result.success(emptyList())

            val escapedFolderId = folderId.replace("'", "\\'")
            val query = "'$escapedFolderId' in parents and trashed = false"
            val url = "$FILES_ENDPOINT?q=${encodeQuery(query)}&spaces=drive&fields=files(id,name,modifiedTime,size)&orderBy=modifiedTime%20desc&pageSize=20"
            val req = Request.Builder()
                .url(url)
                .header("Authorization", "Bearer $accessToken")
                .get()
                .build()

            httpClient.newCall(req).execute().use { resp ->
                val bodyStr = resp.body?.string().orEmpty()
                if (!resp.isSuccessful) {
                    return@withContext Result.failure(mapHttpError(resp.code, bodyStr))
                }
                val json = JSONObject(bodyStr)
                val filesArray = json.optJSONArray("files") ?: JSONArray()
                val out = mutableListOf<DriveBackupFile>()
                for (i in 0 until filesArray.length()) {
                    val obj = filesArray.getJSONObject(i)
                    val name = obj.optString("name")
                    if (name.endsWith(".hnkb")) {
                        out.add(
                            DriveBackupFile(
                                id = obj.getString("id"),
                                name = name,
                                modifiedTimeIso = obj.optString("modifiedTime"),
                                sizeBytes = obj.optString("size", "0").toLongOrNull() ?: 0L
                            )
                        )
                    }
                }
                Result.success(out)
            }
        } catch (t: Throwable) {
            Result.failure(t)
        }
    }

    /**
     * Finds or creates the visible `'Hisab Nikash Backups'` folder in root Drive,
     * uploads the encrypted `.hnkb` backup inside it, verifies metadata & ID returned by Drive,
     * and only AFTER verification prunes older generations beyond 3.
     */
    suspend fun uploadEncryptedBackup(
        accessToken: String,
        fileName: String,
        encryptedBytes: ByteArray
    ): Result<DriveBackupFile> = withContext(Dispatchers.IO) {
        try {
            val folderRes = findOrCreateBackupFolderId(accessToken)
            if (folderRes.isFailure) {
                return@withContext Result.failure(folderRes.exceptionOrNull()!!)
            }
            val folderId = folderRes.getOrThrow()

            val metadataJson = JSONObject().apply {
                put("name", fileName)
                put("parents", JSONArray().put(folderId))
            }.toString()

            val multipart = MultipartBody.Builder()
                .setType("multipart/related".toMediaType())
                .addPart(metadataJson.toRequestBody("application/json; charset=utf-8".toMediaType()))
                .addPart(encryptedBytes.toRequestBody("application/octet-stream".toMediaType()))
                .build()

            val req = Request.Builder()
                .url("$UPLOAD_ENDPOINT?uploadType=multipart&fields=id,name,modifiedTime,size")
                .header("Authorization", "Bearer $accessToken")
                .post(multipart)
                .build()

            val uploaded = httpClient.newCall(req).execute().use { resp ->
                val bodyStr = resp.body?.string().orEmpty()
                if (!resp.isSuccessful) {
                    return@withContext Result.failure(mapHttpError(resp.code, bodyStr))
                }
                val obj = JSONObject(bodyStr)
                val fileId = obj.optString("id")
                if (fileId.isBlank()) {
                    return@withContext Result.failure(IllegalStateException("Drive upload returned empty file ID"))
                }
                DriveBackupFile(
                    id = fileId,
                    name = obj.optString("name", fileName),
                    modifiedTimeIso = obj.optString("modifiedTime"),
                    sizeBytes = obj.optString("size", encryptedBytes.size.toString()).toLongOrNull()
                        ?: encryptedBytes.size.toLong()
                )
            }

            // Verify retrievability inside 'Hisab Nikash Backups' before pruning older generations
            val currentList = listBackups(accessToken).getOrNull().orEmpty()
            val verifiedPresent = currentList.any { it.id == uploaded.id }
            if (!verifiedPresent) {
                return@withContext Result.failure(
                    IllegalStateException("Uploaded file could not be verified in '$BACKUP_FOLDER_NAME' folder")
                )
            }

            if (currentList.size > MAX_GENERATIONS) {
                for (old in currentList.drop(MAX_GENERATIONS)) {
                    deleteFile(accessToken, old.id)
                }
            }
            Result.success(uploaded)
        } catch (t: Throwable) {
            Result.failure(t)
        }
    }

    suspend fun downloadBackup(accessToken: String, fileId: String): Result<ByteArray> = withContext(Dispatchers.IO) {
        try {
            val req = Request.Builder()
                .url("$FILES_ENDPOINT/$fileId?alt=media")
                .header("Authorization", "Bearer $accessToken")
                .get()
                .build()
            httpClient.newCall(req).execute().use { resp ->
                if (!resp.isSuccessful) {
                    return@withContext Result.failure(mapHttpError(resp.code, resp.body?.string().orEmpty()))
                }
                val bytes = resp.body?.bytes()
                    ?: return@withContext Result.failure(IllegalStateException("Empty download body"))
                Result.success(bytes)
            }
        } catch (t: Throwable) {
            Result.failure(t)
        }
    }

    private fun deleteFile(accessToken: String, fileId: String) {
        runCatching {
            val req = Request.Builder()
                .url("$FILES_ENDPOINT/$fileId")
                .header("Authorization", "Bearer $accessToken")
                .delete()
                .build()
            httpClient.newCall(req).execute().close()
        }
    }

    private fun mapHttpError(code: Int, body: String): Exception {
        val label = when (code) {
            401 -> "Authorization expired (401). Please re-authorize Google Drive."
            403 -> "Drive access denied or quota exceeded (403)."
            404 -> "Backup file not found in Google Drive (404)."
            429 -> "Too many requests to Google Drive (429). Retry later."
            in 500..599 -> "Google Drive server error ($code)."
            else -> "Drive HTTP $code"
        }
        return IllegalStateException("$label ${body.take(180)}")
    }
}
