package com.saadahmedev.cmeddownloader

import android.app.Dialog
import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.ServiceConnection
import android.content.pm.PackageManager
import android.os.Build
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.os.IBinder
import android.view.View
import android.widget.Toast
import androidx.core.content.ContextCompat
import com.saadahmedev.cmeddownloader.databinding.ActivityMainBinding
import com.saadahmedsoft.popupdialog.PopupDialog
import com.saadahmedsoft.popupdialog.Styles
import com.saadahmedsoft.popupdialog.listener.OnDialogButtonClickListener

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private lateinit var dialog: PopupDialog

    companion object {
        const val STORAGE_WRITE_REQUEST_CODE = 99
        const val POST_NOTIFICATION_REQUEST_CODE = 98
    }

    private var downloadService: DownloadService? = null
    private var isServiceBound = false
    private val serviceConnection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, service: IBinder?) {
            val binder = service as DownloadService.DownloadBinder
            downloadService = binder.getService()
            isServiceBound = true
            downloadService?.startDownload()
        }

        override fun onServiceDisconnected(name: ComponentName?) {
            isServiceBound = false
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)
        dialog = PopupDialog.getInstance(this)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, android.Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                requestPermissions(arrayOf(android.Manifest.permission.POST_NOTIFICATIONS), POST_NOTIFICATION_REQUEST_CODE)
            }
        }

        binding.btnDownload.setOnClickListener {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                startDownloadService()
            }
            else {
                if (ContextCompat.checkSelfPermission(this, android.Manifest.permission.WRITE_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) {
                    requestPermissions(arrayOf(android.Manifest.permission.WRITE_EXTERNAL_STORAGE, android.Manifest.permission.READ_EXTERNAL_STORAGE), STORAGE_WRITE_REQUEST_CODE)
                } else {
                    startDownloadService()
                }
            }
        }
    }

    private fun startDownloadService() {
        val serviceIntent = Intent(this, DownloadService::class.java)
        startService(serviceIntent)
        bindService(serviceIntent, serviceConnection, Context.BIND_AUTO_CREATE)

        dialog.setStyle(Styles.PROGRESS).setCancelable(false).showDialog()
    }

    private val progressReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            val progress = intent?.getIntExtra("progress", 0)
            updateProgressBar(progress)
        }
    }

    override fun onResume() {
        super.onResume()
        registerReceiver(progressReceiver, IntentFilter("progress_update"))
        downloadService?.hideNotification()
    }

    override fun onPause() {
        super.onPause()
        downloadService?.showNotification()
    }

    private fun updateProgressBar(progress: Int?) {
        if (progress != null) {
            binding.progressBar.visibility = View.VISIBLE
            binding.progressBar.progress = progress
            binding.btnDownload.isEnabled = false
            binding.btnDownload.alpha = 0.9F
            dialog.dismissDialog()

            if (progress == 100) {
                binding.progressBar.visibility = View.GONE
                binding.btnDownload.isEnabled = true
                binding.btnDownload.alpha = 1.0F

                PopupDialog.getInstance(this)
                    .setStyle(Styles.SUCCESS)
                    .setHeading("Downloaded")
                    .setDescription("Download completed. The file can be found in \"CMED DOWNLOAD\" folder in the Download directory.")
                    .setCancelable(false)
                    .showDialog(object : OnDialogButtonClickListener() {
                        override fun onDismissClicked(dialog: Dialog?) {
                            super.onDismissClicked(dialog)

                            unbindService(serviceConnection)
                            val serviceIntent = Intent(this@MainActivity, DownloadService::class.java)
                            stopService(serviceIntent)
                        }
                    })
            }
        }
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)

        if (requestCode == STORAGE_WRITE_REQUEST_CODE) {
            if (grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                startDownloadService()
            } else {
                Toast.makeText(this, "Storage write permission is required", Toast.LENGTH_SHORT).show()
            }
        }

        if (requestCode == POST_NOTIFICATION_REQUEST_CODE) {
            if (grantResults[0] != PackageManager.PERMISSION_GRANTED) {
                Toast.makeText(this, "Allow notification in order to receive notifications", Toast.LENGTH_SHORT).show()
            }
        }
    }
}