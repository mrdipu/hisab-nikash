package com.example

import com.example.cloud.drive.DriveBackupRepository
import kotlinx.coroutines.runBlocking
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import okio.Buffer
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.net.URLDecoder

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [29])
class DriveBackupRepositoryTest {

    private fun jsonResponse(request: okhttp3.Request, code: Int, json: String): Response =
        Response.Builder()
            .request(request)
            .protocol(Protocol.HTTP_1_1)
            .code(code)
            .message(if (code in 200..299) "OK" else "ERR")
            .body(json.toResponseBody("application/json; charset=utf-8".toMediaType()))
            .build()

    @Test
    fun `listBackups returns empty list without creating folder when Hisab Nikash Backups does not exist`() = runBlocking {
        val requests = mutableListOf<Pair<String, String>>()
        val client = OkHttpClient.Builder()
            .addInterceptor { chain ->
                val req = chain.request()
                val decodedUrl = URLDecoder.decode(req.url.toString(), "UTF-8")
                requests.add(req.method to decodedUrl)
                assertFalse("Must not query appDataFolder", decodedUrl.contains("appDataFolder"))
                assertTrue(decodedUrl.contains("Hisab Nikash Backups"))
                assertTrue(decodedUrl.contains("spaces=drive"))
                jsonResponse(req, 200, """{"files":[]}""")
            }
            .build()

        val repo = DriveBackupRepository(client)
        val result = repo.listBackups("token-123")
        assertTrue(result.isSuccess)
        assertTrue(result.getOrThrow().isEmpty())
        assertEquals(1, requests.size)
        assertEquals("GET", requests[0].first)
    }

    @Test
    fun `uploadEncryptedBackup creates Hisab Nikash Backups folder in root, uploads inside it, and prunes older generations`() = runBlocking {
        var folderId: String? = null
        val uploadedFiles = mutableListOf(
            JSONObject().put("id", "old_1").put("name", "hisabnikash_1.hnkb").put("modifiedTime", "2026-11-01T00:00:00Z").put("size", "10"),
            JSONObject().put("id", "old_2").put("name", "hisabnikash_2.hnkb").put("modifiedTime", "2026-11-02T00:00:00Z").put("size", "10"),
            JSONObject().put("id", "old_3").put("name", "hisabnikash_3.hnkb").put("modifiedTime", "2026-11-03T00:00:00Z").put("size", "10")
        )
        val deletedIds = mutableListOf<String>()
        var createdFolderBody: String? = null
        var uploadedPartText: String? = null

        val client = OkHttpClient.Builder()
            .addInterceptor { chain ->
                val req = chain.request()
                val decodedUrl = URLDecoder.decode(req.url.toString(), "UTF-8")
                assertFalse("Must not reference appDataFolder", decodedUrl.contains("appDataFolder"))

                when {
                    req.method == "GET" && decodedUrl.contains("mimeType = 'application/vnd.google-apps.folder'") -> {
                        val arr = JSONArray()
                        folderId?.let { arr.put(JSONObject().put("id", it).put("name", DriveBackupRepository.BACKUP_FOLDER_NAME)) }
                        jsonResponse(req, 200, JSONObject().put("files", arr).toString())
                    }
                    req.method == "POST" && req.url.encodedPath == "/drive/v3/files" -> {
                        val buf = Buffer()
                        req.body!!.writeTo(buf)
                        createdFolderBody = buf.readUtf8()
                        val bodyJson = JSONObject(createdFolderBody!!)
                        assertEquals(DriveBackupRepository.BACKUP_FOLDER_NAME, bodyJson.getString("name"))
                        assertEquals("application/vnd.google-apps.folder", bodyJson.getString("mimeType"))
                        assertEquals("root", bodyJson.getJSONArray("parents").getString(0))
                        folderId = "folder_visible_1"
                        jsonResponse(req, 200, JSONObject().put("id", folderId).put("name", DriveBackupRepository.BACKUP_FOLDER_NAME).toString())
                    }
                    req.method == "POST" && req.url.encodedPath == "/upload/drive/v3/files" -> {
                        val buf = Buffer()
                        req.body!!.writeTo(buf)
                        uploadedPartText = buf.readUtf8()
                        assertTrue(uploadedPartText!!.contains(""""parents":["folder_visible_1"]"""))
                        val newEntry = JSONObject()
                            .put("id", "new_4")
                            .put("name", "hisabnikash_4.hnkb")
                            .put("modifiedTime", "2026-11-17T00:00:00Z")
                            .put("size", "5")
                        uploadedFiles.add(0, newEntry)
                        jsonResponse(req, 200, newEntry.toString())
                    }
                    req.method == "GET" && decodedUrl.contains("'folder_visible_1' in parents") -> {
                        val arr = JSONArray()
                        uploadedFiles.forEach { arr.put(it) }
                        jsonResponse(req, 200, JSONObject().put("files", arr).toString())
                    }
                    req.method == "DELETE" -> {
                        val id = req.url.pathSegments.last()
                        deletedIds.add(id)
                        uploadedFiles.removeAll { it.getString("id") == id }
                        Response.Builder()
                            .request(req)
                            .protocol(Protocol.HTTP_1_1)
                            .code(204)
                            .message("No Content")
                            .body(ByteArray(0).toResponseBody(null))
                            .build()
                    }
                    req.method == "GET" && decodedUrl.contains("alt=media") -> {
                        Response.Builder()
                            .request(req)
                            .protocol(Protocol.HTTP_1_1)
                            .code(200)
                            .message("OK")
                            .body(byteArrayOf(7, 8, 9).toResponseBody("application/octet-stream".toMediaType()))
                            .build()
                    }
                    else -> error("Unexpected Drive HTTP call: ${req.method} $decodedUrl")
                }
            }
            .build()

        val repo = DriveBackupRepository(client)
        val payload = byteArrayOf(7, 8, 9)
        val uploadRes = repo.uploadEncryptedBackup("token-abc", "hisabnikash_4.hnkb", payload)
        assertTrue(uploadRes.isSuccess)
        assertEquals("new_4", uploadRes.getOrThrow().id)
        assertTrue(createdFolderBody != null)
        assertEquals(listOf("old_3"), deletedIds)

        val listed = repo.listBackups("token-abc").getOrThrow()
        assertEquals(3, listed.size)
        assertEquals("new_4", listed.first().id)

        val downloaded = repo.downloadBackup("token-abc", "new_4").getOrThrow()
        assertArrayEquals(payload, downloaded)
    }
}
