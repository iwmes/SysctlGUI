package com.androidvip.sysctlgui.activities

import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.text.method.LinkMovementMethod
import android.view.Menu
import android.view.MenuItem
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import com.androidvip.sysctlgui.*
import com.google.gson.JsonParseException
import com.google.gson.JsonSyntaxException
import kotlinx.android.synthetic.main.activity_main.*
import kotlinx.android.synthetic.main.content_main.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.lang.Exception

class MainActivity : AppCompatActivity() {
    companion object {
        private const val OPEN_FILE_REQUEST_CODE: Int = 1
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        setSupportActionBar(toolbar)

        mainParamsList.setOnClickListener {
            Intent(this, KernelParamsListActivity::class.java).apply {
                startActivity(this)
            }
        }

        mainParamBrowser.setOnClickListener {
            Intent(this, KernelParamBrowserActivity::class.java).apply {
                startActivity(this)
            }
        }

        mainReadFromFile.setOnClickListener {
            val intent = Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
                addCategory(Intent.CATEGORY_OPENABLE)
                type = "*/*"
            }
            startActivityForResult(intent, OPEN_FILE_REQUEST_CODE)
        }

        mainAppDescription.movementMethod = LinkMovementMethod.getInstance()
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.menu_main, menu)

        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        when (item.itemId) {
            android.R.id.home -> finish()

            R.id.action_settings -> {
                startActivity(Intent(this, SettingsActivity::class.java))
            }

            R.id.action_exit -> {
                RootUtils.finishProcess()
                moveTaskToBack(true)
                finish()
            }
        }

        return true
    }

    override fun onDestroy() {
        super.onDestroy()
        RootUtils.finishProcess()
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        when (requestCode) {
            OPEN_FILE_REQUEST_CODE -> {

                if (resultCode != Activity.RESULT_OK) {
                    return
                }

                data?.data?.let { uri ->

                    // check if mime is json
                    if (uri.lastPathSegment?.contains(".json")?.not() ?: run { false }) {
                        toast(R.string.import_error_invalid_file_type)
                        return
                    }

                    GlobalScope.launch {
                        applyParamsFromUri(uri)
                    }
                }
            }
        }
        super.onActivityResult(requestCode, resultCode, data)
    }

    private suspend fun applyParamsFromUri(uri: Uri) = withContext(Dispatchers.Default) {
        val context = this@MainActivity
        val successfulParams: MutableList<KernelParameter> = mutableListOf()

        fun showResultDialog(message: String, success: Boolean) {
            val dialog = AlertDialog.Builder(context)
                .setIcon(if (success) R.drawable.ic_check else R.drawable.ic_close)
                .setTitle(if (success) R.string.done else R.string.failed)
                .setMessage(message)
                .setPositiveButton(android.R.string.ok) { _, _ -> }

            if (!isFinishing) {
                dialog.show()
            }
        }

        try {
            val params = KernelParamUtils(context).getParamsFromUri(uri)
            if (params.isNullOrEmpty()) {
                context.toast(R.string.no_parameters_found)
                return@withContext
            }

            params.forEach {
                // apply the param to check if valid
                KernelParamUtils(context).applyParam(it, false, object : KernelParamUtils.KernelParamApply {
                    override fun onEmptyValue() { }
                    override fun onFeedBack(feedback: String) { }

                    override fun onSuccess() {
                        successfulParams.add(it)
                    }

                    override suspend fun onCustomApply(kernelParam: KernelParameter) { }
                })
            }

            val oldParams = Prefs.removeAllParams(context)
            if (Prefs.putParams(successfulParams, context)) {
                runSafeOnUiThread {
                    showResultDialog(getString(R.string.import_success_message, successfulParams.size), true)
                    context.toast(R.string.done, Toast.LENGTH_LONG)
                }
            } else {
                // Probably an IO error, revert back
                Prefs.putParams(oldParams, context)
                runSafeOnUiThread {
                    showResultDialog(getString(R.string.restore_parameters), false)
                }
            }
        } catch (e: Exception) {
            runSafeOnUiThread {
                when (e) {
                    is JsonParseException,
                    is JsonSyntaxException -> showResultDialog(getString(R.string.import_error_invalid_json), false)
                    else -> showResultDialog(getString(R.string.import_error), false)
                }
            }
        }
    }
}
