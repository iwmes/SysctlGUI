package com.androidvip.sysctlgui.activities

import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.view.View
import android.view.WindowManager
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.preference.PreferenceManager
import com.androidvip.sysctlgui.Prefs
import com.androidvip.sysctlgui.R
import com.androidvip.sysctlgui.RootUtils
import com.androidvip.sysctlgui.runSafeOnUiThread
import com.topjohnwu.superuser.Shell
import kotlinx.android.synthetic.main.activity_splash.*
import kotlinx.coroutines.*

class SplashActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_splash)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            window.addFlags(WindowManager.LayoutParams.FLAG_DRAWS_SYSTEM_BAR_BACKGROUNDS)
            window.statusBarColor = ContextCompat.getColor(this,
                R.color.colorPrimaryLight
            )
        }

        val prefs = PreferenceManager.getDefaultSharedPreferences(this)
        GlobalScope.launch {
            val isRootAccessGiven = checkRootAccess()
            runSafeOnUiThread {
                splashStatusText.setText(R.string.splash_status_checking_busybox)
            }

            val isBusyBoxAvailable = checkBusyBox()
            runSafeOnUiThread {
                if (isRootAccessGiven) {
                    if (!isBusyBoxAvailable) {
                        prefs.edit().putBoolean(Prefs.USE_BUSYBOX, false).apply()
                    }
                    startActivity(Intent(this@SplashActivity, MainActivity::class.java))
                    finish()
                } else {
                    splashProgress.visibility = View.GONE
                    AlertDialog.Builder(this@SplashActivity)
                        .setTitle(R.string.error)
                        .setMessage("Root access not found. You can only edit properties with root access.")
                        .setPositiveButton("OK") { _, _ ->  }
                        .setCancelable(false)
                        .show()
                }
            }
        }
    }

    private suspend fun checkRootAccess() = withContext(Dispatchers.IO) {
        delay(500)
        Shell.rootAccess()
    }

    private suspend fun checkBusyBox() = withContext(Dispatchers.IO) {
        delay(500)
        RootUtils.isBusyboxAvailable()
    }
}
