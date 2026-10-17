package com.mahirlabib.youtube

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.widget.Toast
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import java.net.URL

private data class AvailableAppUpdate(
    val version: String,
    val downloadUrl: String
)

private object AppUpdateManager {
    private const val RELEASE_API =
        "https://api.github.com/repos/mahirlabib478-del/youtube/releases/latest"

    suspend fun checkForUpdate(): AvailableAppUpdate? = withContext(Dispatchers.IO) {
        val connection = (URL(RELEASE_API).openConnection() as HttpURLConnection).apply {
            requestMethod = "GET"
            connectTimeout = 8000
            readTimeout = 8000
            setRequestProperty("Accept", "application/vnd.github+json")
            setRequestProperty("User-Agent", "YouTubeAndroidApp")
        }
        try {
            if (connection.responseCode !in 200..299) return@withContext null
            val payload = connection.inputStream.bufferedReader().use { it.readText() }
            val release = JSONObject(payload)
            if (release.optBoolean("draft", false) || release.optBoolean("prerelease", false)) {
                return@withContext null
            }
            val remoteVersion = release.optString("tag_name").removePrefix("v").trim()
            val assets = release.optJSONArray("assets") ?: return@withContext null
            var apkUrl: String? = null
            for (index in 0 until assets.length()) {
                val asset = assets.optJSONObject(index) ?: continue
                if (asset.optString("name").endsWith(".apk", ignoreCase = true)) {
                    apkUrl = asset.optString("browser_download_url").takeIf { it.startsWith("https://") }
                    if (apkUrl != null) break
                }
            }
            if (apkUrl.isNullOrBlank() || compareVersions(remoteVersion, BuildConfig.VERSION_NAME) <= 0) {
                null
            } else {
                AvailableAppUpdate(remoteVersion, apkUrl)
            }
        } finally {
            connection.disconnect()
        }
    }

    private fun compareVersions(first: String, second: String): Int {
        val a = first.split('.').map { it.toIntOrNull() ?: 0 }
        val b = second.split('.').map { it.toIntOrNull() ?: 0 }
        for (index in 0 until maxOf(a.size, b.size)) {
            val comparison = (a.getOrElse(index) { 0 }).compareTo(b.getOrElse(index) { 0 })
            if (comparison != 0) return comparison
        }
        return 0
    }

    suspend fun download(context: Context, url: String, onProgress: (Int) -> Unit): File =
        withContext(Dispatchers.IO) {
            val directory = File(context.cacheDir, "app-updates").apply { mkdirs() }
            val outputFile = File(directory, "youtube-update.apk")
            val connection = (URL(url).openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"
                connectTimeout = 15000
                readTimeout = 30000
                instanceFollowRedirects = true
                setRequestProperty("User-Agent", "YouTubeAndroidApp")
            }
            try {
                if (connection.responseCode !in 200..299) {
                    throw IllegalStateException("Download failed (HTTP ${connection.responseCode})")
                }
                val totalBytes = connection.contentLengthLong
                connection.inputStream.use { input ->
                    outputFile.outputStream().use { output ->
                        val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
                        var downloaded = 0L
                        while (true) {
                            val count = input.read(buffer)
                            if (count < 0) break
                            output.write(buffer, 0, count)
                            downloaded += count
                            if (totalBytes > 0) {
                                val percent = ((downloaded * 100) / totalBytes).toInt().coerceIn(0, 100)
                                Handler(Looper.getMainLooper()).post { onProgress(percent) }
                            }
                        }
                    }
                }
                if (outputFile.length() <= 0L) {
                    outputFile.delete()
                    throw IllegalStateException("The downloaded APK is empty.")
                }
                outputFile
            } catch (error: Exception) {
                outputFile.delete()
                throw error
            } finally {
                connection.disconnect()
            }
        }

    fun openInstaller(context: Context, apk: File): Boolean {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && !context.packageManager.canRequestPackageInstalls()) {
            val settingsIntent = Intent(
                Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
                Uri.parse("package:${context.packageName}")
            ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(settingsIntent)
            Toast.makeText(
                context,
                "Allow installs for this app, return here, then tap Download & Install again.",
                Toast.LENGTH_LONG
            ).show()
            return false
        }

        val apkUri = FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            apk
        )
        val installIntent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(apkUri, "application/vnd.android.package-archive")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(installIntent)
        return true
    }
}

@Composable
internal fun AppUpdatePrompt() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var update by remember { mutableStateOf<AvailableAppUpdate?>(null) }
    var showDialog by remember { mutableStateOf(false) }
    var isDownloading by remember { mutableStateOf(false) }
    var progress by remember { mutableIntStateOf(0) }
    var statusMessage by remember { mutableStateOf<String?>(null) }

    // Re-check each time the app process starts. Dismissing the dialog does not suppress it
    // on the next launch, so users keep seeing the download option until they update.
    LaunchedEffect(Unit) {
        update = runCatching { AppUpdateManager.checkForUpdate() }.getOrNull()
        showDialog = update != null
    }

    if (showDialog && update != null) {
        AlertDialog(
            onDismissRequest = { if (!isDownloading) showDialog = false },
            title = { Text("Update available: v${update!!.version}") },
            text = {
                Column {
                    Text("A newer version of the app is available. Download and install it to get the latest fixes.")
                    if (isDownloading) {
                        Spacer(Modifier.height(12.dp))
                        LinearProgressIndicator(
                            progress = { progress / 100f },
                            modifier = Modifier
                        )
                        Text(
                            if (progress > 0) "Downloading… $progress%" else "Starting download…",
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                    statusMessage?.let {
                        Spacer(Modifier.height(8.dp))
                        Text(it, style = MaterialTheme.typography.bodySmall)
                    }
                }
            },
            confirmButton = {
                TextButton(
                    enabled = !isDownloading,
                    onClick = {
                        scope.launch {
                            isDownloading = true
                            statusMessage = null
                            try {
                                val apk = AppUpdateManager.download(context, update!!.downloadUrl) {
                                    progress = it
                                }
                                isDownloading = false
                                val installerOpened = AppUpdateManager.openInstaller(context, apk)
                                if (installerOpened) {
                                    showDialog = false
                                } else {
                                    statusMessage = "After enabling install permission, tap Download & Install again."
                                }
                            } catch (error: Exception) {
                                statusMessage = error.message ?: "Update download failed. Check your internet and retry."
                                isDownloading = false
                            }
                        }
                    }
                ) {
                    Text(if (isDownloading) "Downloading…" else "Download & Install")
                }
            },
            dismissButton = {
                TextButton(
                    enabled = !isDownloading,
                    onClick = { showDialog = false }
                ) {
                    Text("Later")
                }
            }
        )
    }
}
