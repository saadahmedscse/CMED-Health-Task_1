package com.saadahmedev.cmeddownloader

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.ContentValues
import android.content.Intent
import android.os.Binder
import android.os.Environment
import android.os.IBinder
import android.provider.MediaStore
import android.util.Log
import androidx.core.app.NotificationCompat
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.ResponseBody
import java.io.IOException
import java.io.InputStream
import java.util.concurrent.Executors


class DownloadService : Service() {

    private val binder = DownloadBinder()
    private var notificationManager: NotificationManager? = null
    private var isForeground = false
    private var downloadProgress = 0

    inner class DownloadBinder : Binder() {
        fun getService(): DownloadService {
            return this@DownloadService
        }
    }

    override fun onBind(intent: Intent): IBinder? {
        return binder
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        createNotificationChannel()
        return START_NOT_STICKY
    }

    private fun createNotificationChannel() {
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Download Service Channel",
                NotificationManager.IMPORTANCE_LOW
            )
            notificationManager = getSystemService(NotificationManager::class.java)
            notificationManager?.createNotificationChannel(channel)
        }
    }

    private fun createNotification(progress: Int): Notification {

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle(if (progress < 100) "Downloading" else "Download Completed")
            .setContentText("$progress% complete")
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setProgress(100, progress, false)
            .build()
    }

    fun startDownload() {
        Executors.newSingleThreadExecutor().execute {
            val url = "https://file-examples.com/storage/fe1207564e65327fe9c8723/2017/04/file_example_MP4_1920_18MG.mp4"
            val client = OkHttpClient()
            val request = Request.Builder().url(url).build()

            try {
                val response: Response = client.newCall(request).execute()

                if (response.isSuccessful) {
                    val body: ResponseBody? = response.body
                    Log.d("response_debug", "response successful")
                    if (body != null) {
                        Log.d("response_debug", "body not null")
                        val contentLength = body.contentLength()
                        var bytesRead: Int
                        val bufferSize = 4096
                        val data = ByteArray(bufferSize)

                        val values = ContentValues()
                        values.put(MediaStore.MediaColumns.DISPLAY_NAME, "CMED Downloaded File")
                        values.put(MediaStore.MediaColumns.MIME_TYPE, "video/mp4")
                        values.put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS + "/CMED DOWNLOADER/")
                        val uri = contentResolver.insert(MediaStore.Files.getContentUri("external"), values)

                        val outputStream = contentResolver.openOutputStream(uri!!)
                        val inputStream: InputStream = body.byteStream()

                        var totalBytesRead = 0
                        var progress = 0
                        while (inputStream.read(data).also { bytesRead = it } != -1) {
                            outputStream?.write(data, 0, bytesRead)
                            totalBytesRead += bytesRead
                            val newProgress = ((totalBytesRead * 100) / contentLength).toInt()

                            if (newProgress != progress) {
                                progress = newProgress
                                downloadProgress = progress
                                sendProgressUpdateToActivity(progress)
                                updateNotification(progress)
                            }
                        }

                        outputStream?.flush()
                        outputStream?.close()
                        inputStream.close()
                    }
                }
            } catch (e: IOException) {
                e.printStackTrace()
                Log.d("response_debug", "error ${e.localizedMessage}")
            }
        }
    }

    private fun sendProgressUpdateToActivity(progress: Int) {
        val intent = Intent("progress_update")
        intent.putExtra("progress", progress)
        sendBroadcast(intent)
    }

    private fun updateNotification(progress: Int) {
        if (isForeground) {
            val notification = createNotification(progress)
            notificationManager?.notify(NOTIFICATION_ID, notification)
            Log.d("notification_debug", "updateNotification: $progress" + if (notificationManager == null) " null" else " non null")
        }
    }

    fun hideNotification() {
        stopForeground(true)
        notificationManager?.cancel(NOTIFICATION_ID)
        isForeground = false
    }

    fun showNotification() {
        isForeground = true
        startForeground(NOTIFICATION_ID, createNotification(downloadProgress))
    }

    override fun onDestroy() {
        super.onDestroy()
        hideNotification()
    }

    companion object {
        const val NOTIFICATION_ID = 1
        const val CHANNEL_ID = "DownloadServiceChannel"
    }
}