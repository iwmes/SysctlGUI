package com.androidvip.sysctlgui.activities

import android.os.Bundle
import android.view.MenuItem
import android.widget.LinearLayout
import android.widget.Toast
import androidx.recyclerview.widget.DividerItemDecoration
import androidx.recyclerview.widget.GridLayoutManager
import com.androidvip.sysctlgui.R
import com.androidvip.sysctlgui.activities.base.BaseSearchActivity
import com.androidvip.sysctlgui.adapters.KernelParamBrowserAdapter
import com.androidvip.sysctlgui.runSafeOnUiThread
import kotlinx.android.synthetic.main.activity_kernel_param_browser.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

class KernelParamBrowserActivity : BaseSearchActivity(), DirectoryChangedListener {
    private var currentPath = "/proc/sys"
    private val paramsBrowserAdapter: KernelParamBrowserAdapter by lazy {
        KernelParamBrowserAdapter(arrayOf(), this, this)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_kernel_param_browser)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)

        paramBrowserSwipeLayout.apply {
            setColorSchemeResources(R.color.colorAccent)
            setOnRefreshListener { refreshList() }
        }

        paramBrowserRecyclerView.apply {
            setHasFixedSize(true)
            addItemDecoration(DividerItemDecoration(this.context, LinearLayout.VERTICAL))
            layoutManager = GridLayoutManager(this.context, recyclerViewColumns)
            adapter = paramsBrowserAdapter
        }
    }

    override fun onDirectoryChanged(newDir: File) {
        val newPath = newDir.absolutePath
        if (newPath.isNotEmpty() && newPath.startsWith("/proc/sys")) {
            currentPath = newPath
            supportActionBar?.subtitle = newPath
        } else {
            Toast.makeText(this, getString(R.string.invalid_path), Toast.LENGTH_SHORT).show()
        }

        resetSearchExpression()

        refreshList()
    }

    override fun onStart() {
        super.onStart()
        refreshList()
    }

    override fun onQueryTextChanged() {
        refreshList()
    }

    override fun onBackPressed() {
        if (currentPath == "/proc/sys") {
            finish()
        } else {
            onDirectoryChanged(File(currentPath).parentFile)
            refreshList()
        }
    }

    override fun onOptionsItemSelected(item: MenuItem?): Boolean {
        when (item?.itemId) {
            android.R.id.home -> onBackPressed()
        }
        return super.onOptionsItemSelected(item)
    }

    private fun refreshList() {
        paramBrowserSwipeLayout.isRefreshing = true

        GlobalScope.launch {
            var files = getCurrentPathFiles()

            if (searchExpression.isNotEmpty()) {
                files = files.filter { file ->
                    file.name.toLowerCase(defaultLocale)
                        .contains(searchExpression.toLowerCase(defaultLocale))
                }.toTypedArray()
            }

            runSafeOnUiThread {
                paramBrowserSwipeLayout.isRefreshing = false
                files?.let {
                    paramsBrowserAdapter.updateData(it)
                }
            }
        }
    }

    private suspend fun getCurrentPathFiles() = withContext(Dispatchers.IO) {
        runCatching {
            File(currentPath).listFiles()
        }.getOrDefault(arrayOf<File>())
    }

    private val recyclerViewColumns: Int
        get() {
            val isLandscape = resources.getBoolean(R.bool.is_landscape)
            return if (isLandscape) 2 else 1
        }
}

interface DirectoryChangedListener {
    fun onDirectoryChanged(newDir: File)
}
