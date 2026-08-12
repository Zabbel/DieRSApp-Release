package com.zabbel.diersapp.util

import android.app.AlertDialog
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import androidx.core.content.FileProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.net.URL

object UpdateManager {

    private const val UPDATE_JSON_URL = "https://raw.githubusercontent.com/Zabbel/DieRSApp-Release/main/update.json"

    fun checkForUpdates(context: Context) {
        GlobalScope.launch(Dispatchers.IO) {
            try {
                val url = URL(UPDATE_JSON_URL)
                val connection = url.openConnection() as HttpURLConnection
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
                    withContext(Dispatchers.Main) {
                        showUpdateDialog(context, serverVersionName, apkUrl, releaseNotes)
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    private fun showUpdateDialog(context: Context, versionName: String, apkUrl: String, releaseNotes: String) {
        AlertDialog.Builder(context)
            .setTitle("Update verfügbar")
            .setMessage("Version $versionName ist verfügbar.\n\nÄnderungen:\n$releaseNotes\n\nMöchtest du das Update jetzt installieren?")
            .setPositiveButton("Update") { _, _ ->
                downloadAndInstallApk(context, apkUrl)
            }
            .setNegativeButton("Später", null)
            .show()
    }

    private fun downloadAndInstallApk(context: Context, apkUrl: String) {
        GlobalScope.launch(Dispatchers.IO) {
            try {
                val url = URL(apkUrl)
                val connection = url.openConnection() as HttpURLConnection
                val apkFile = File(context.cacheDir, "update.apk")
                
                connection.inputStream.use { input ->
                    FileOutputStream(apkFile).use { output ->
                        input.copyTo(output)
                    }
                }

                withContext(Dispatchers.Main) {
                    installApk(context, apkFile)
                }
            } catch (e: Exception) {
                e.printStackTrace()
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
