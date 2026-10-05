package com.zabbel.diersapp.util

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import androidx.core.content.FileProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.net.URL

sealed class UpdateState {
    object Idle : UpdateState()
    object Checking : UpdateState()
    data class UpdateAvailable(val versionName: String, val apkUrl: String, val releaseNotes: String) : UpdateState()
    data class Downloading(val progress: Float) : UpdateState()
    object Finished : UpdateState() // Fertig geprüft (nichts gefunden, abgelehnt oder Installation gestartet)
}

object UpdateManager {

    private const val UPDATE_JSON_URL = "https://raw.githubusercontent.com/Zabbel/DieRSApp-Release/refs/heads/master/update.json"

    private val _updateState = MutableStateFlow<UpdateState>(UpdateState.Idle)
    val updateState: StateFlow<UpdateState> = _updateState.asStateFlow()

    fun checkForUpdates(context: Context) {
        if (_updateState.value is UpdateState.Checking || _updateState.value is UpdateState.Downloading) return
        _updateState.value = UpdateState.Checking

        GlobalScope.launch(Dispatchers.IO) {
            try {
                val url = URL(UPDATE_JSON_URL)
                val connection = url.openConnection() as HttpURLConnection
                connection.connectTimeout = 5000
                connection.readTimeout = 5000
                
                val jsonString = connection.inputStream.bufferedReader().use { it.readText() }
                val json = JSONObject(jsonString)

                val serverVersionCode = json.getInt("versionCode")
                val serverVersionName = json.getString("versionName")
                val apkUrl = json.getString("apkUrl")
                val releaseNotes = json.optString("releaseNotes", "")

                val packageInfo = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    context.packageManager.getPackageInfo(context.packageName, android.content.pm.PackageManager.PackageInfoFlags.of(0))
                } else {
                    context.packageManager.getPackageInfo(context.packageName, 0)
                }
                
                val currentVersionCode = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                    packageInfo.longVersionCode
                } else {
                    packageInfo.versionCode.toLong()
                }

                if (serverVersionCode > currentVersionCode) {
                    _updateState.value = UpdateState.UpdateAvailable(serverVersionName, apkUrl, releaseNotes)
                } else {
                    _updateState.value = UpdateState.Finished
                }
            } catch (e: Exception) {
                e.printStackTrace()
                _updateState.value = UpdateState.Finished
            }
        }
    }

    fun skipUpdate() {
        _updateState.value = UpdateState.Finished
    }

    fun startDownload(context: Context, apkUrl: String) {
        _updateState.value = UpdateState.Downloading(0f)
        GlobalScope.launch(Dispatchers.IO) {
            try {
                var currentUrl = apkUrl
                var connection: HttpURLConnection
                var responseCode: Int
                
                // Manuelles Folgen von Redirects (wichtig für GitHub Releases -> S3)
                do {
                    val url = URL(currentUrl)
                    connection = url.openConnection() as HttpURLConnection
                    connection.instanceFollowRedirects = true
                    responseCode = connection.responseCode
                    if (responseCode == HttpURLConnection.HTTP_MOVED_PERM || responseCode == HttpURLConnection.HTTP_MOVED_TEMP) {
                        currentUrl = connection.getHeaderField("Location")
                    } else {
                        break
                    }
                } while (true)

                val fileLength = connection.contentLength
                val apkFile = File(context.cacheDir, "update.apk")
                
                connection.inputStream.use { input ->
                    FileOutputStream(apkFile).use { output ->
                        val data = ByteArray(4096)
                        var total: Long = 0
                        var count: Int
                        while (input.read(data).also { count = it } != -1) {
                            total += count.toLong()
                            if (fileLength > 0) {
                                val progress = (total * 100 / fileLength).toFloat() / 100f
                                _updateState.value = UpdateState.Downloading(progress)
                            }
                            output.write(data, 0, count)
                        }
                    }
                }

                _updateState.value = UpdateState.Finished
                withContext(Dispatchers.Main) {
                    installApk(context, apkFile)
                }
            } catch (e: Exception) {
                e.printStackTrace()
                _updateState.value = UpdateState.Finished
            }
        }
    }

    private fun installApk(context: Context, file: File) {
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.provider", file)
        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, "application/vnd.android.package-archive")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(intent)
    }
}
