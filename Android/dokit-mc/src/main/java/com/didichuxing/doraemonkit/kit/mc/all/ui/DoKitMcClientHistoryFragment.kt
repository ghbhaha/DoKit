package com.didichuxing.doraemonkit.kit.mc.all.ui

import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.text.TextUtils
import android.view.View
import android.widget.Button
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.didichuxing.doraemonkit.DoKit
import com.didichuxing.doraemonkit.constant.WSMode
import com.didichuxing.doraemonkit.kit.core.BaseFragment
import com.didichuxing.doraemonkit.kit.mc.all.DoKitMcManager
import com.didichuxing.doraemonkit.kit.mc.all.DoKitWindowManager
import com.didichuxing.doraemonkit.kit.mc.client.ClientDokitView
import com.didichuxing.doraemonkit.kit.mc.client.DoKitWsClient
import com.didichuxing.doraemonkit.kit.mc.connect.ConnectHistoryUtils
import com.didichuxing.doraemonkit.kit.mc.connect.DokitMcConnectManager
import com.didichuxing.doraemonkit.kit.mc.server.HostInfo
import com.didichuxing.doraemonkit.mc.R
import com.didichuxing.doraemonkit.util.GsonUtils
import com.didichuxing.doraemonkit.util.LogHelper
import com.didichuxing.doraemonkit.util.TimeUtils
import com.didichuxing.doraemonkit.util.ToastUtils
import com.didichuxing.doraemonkit.widget.recyclerview.DividerItemDecoration
import com.didichuxing.doraemonkit.zxing.activity.CaptureActivity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.*

/**
 * ================================================
 * 作    者：jint（金台）
 * 版    本：1.0
 * 创建日期：2020/12/10-10:52
 * 描    述：
 * 修订历史：
 * ================================================
 */
class DoKitMcClientHistoryFragment : BaseFragment() {

    private val REQUEST_CODE_SCAN = 0x101

    private lateinit var mRv: RecyclerView
    private lateinit var mAdapter: McClientHistoryAdapter
    private lateinit var histories: MutableList<McClientHistory>

    override fun onRequestLayout(): Int {
        return R.layout.dk_fragment_mc_client_history
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val add: Button = findViewById(R.id.add)
        add.setOnClickListener { view ->
            startScan()
        }

        mRv = findViewById(R.id.rv)

        mAdapter = McClientHistoryAdapter(mutableListOf<McClientHistory>()) { client ->
            handleConnect(client)
        }

        mAdapter.setOnItemClickListener { adapter, view, pos ->
            val data = histories[pos]
            DokitMcConnectManager.itemHistory = data

            if (activity is DoKitMcActivity) {
                (activity as DoKitMcActivity).pushFragment(WSMode.CLIENT)
            }
        }

        mRv.apply {
            adapter = mAdapter
            layoutManager = LinearLayoutManager(requireActivity())
            val decoration = DividerItemDecoration(DividerItemDecoration.VERTICAL)
            decoration.setDrawable(resources.getDrawable(R.drawable.dk_divider))
            addItemDecoration(decoration)
        }

        updateHistoryView()

    }

    override fun onResume() {
        super.onResume()
        updateHistoryView()
    }

    private fun updateHistoryView() {
        lifecycleScope.launch {
            val clients = ClientHistoryUtils.loadClientHistory()
            val current = DokitMcConnectManager.currentClientHistory
            for (history in clients) {
                history.enable = TextUtils.equals(history.host, current?.host)
            }

            histories = clients
            clients?.let {
                mAdapter.setList(clients)
            }
        }
    }

    /**
     * 开始扫描
     */
    private fun startScan() {
        val intent = Intent(activity, DoKitMcScanActivity::class.java)
        startActivityForResult(intent, REQUEST_CODE_SCAN)
    }


    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == REQUEST_CODE_SCAN && resultCode == Activity.RESULT_OK) {
            if (data != null && data.hasExtra(CaptureActivity.INTENT_EXTRA_KEY_QR_SCAN)) {
                val code = data.getStringExtra(CaptureActivity.INTENT_EXTRA_KEY_QR_SCAN)
                if (!TextUtils.isEmpty(code)) {
                    try {
                        val uri = Uri.parse(code)
                        uri?.let {
                            val name = uri.host.toString()
                            val time = TimeUtils.date2String(Date())
                            ClientHistoryUtils.saveClientHistory(McClientHistory(uri.host!!, uri.port, uri.path!!, name, time))
                            handleConnect(McClientHistory(uri.host!!, uri.port, uri.path!!, name, time))
                        }

                    } catch (e: Exception) {
                        e.printStackTrace()
                    } finally {
                        if (activity is DoKitMcActivity) {
                            (activity as DoKitMcActivity).onBackPressed()
                        }
                    }
                } else {
                    handleNoResult()
                }
            } else {
                handleNoResult()
            }
        } else {
            handleNoResult()
        }
    }

    /**
     * 没有返回结果
     */
    private fun handleNoResult() {
        ToastUtils.showShort("没有扫描到任何内容 >_< .")
    }

    private fun handleConnect(clientHistory: McClientHistory) {
        DoKitWsClient.connect(clientHistory.host!!, clientHistory.port, clientHistory.path!!) { code, message ->
            withContext(Dispatchers.Main) {
                when (code) {
                    DoKitWsClient.CONNECT_SUCCEED -> {
                        DoKitWindowManager.hookWindowManagerGlobal()
                        DoKitMcManager.HOST_INFO = GsonUtils.fromJson<HostInfo>(message, HostInfo::class.java)
                        DokitMcConnectManager.currentClientHistory = clientHistory
                        updateHistoryView()
                        //启动悬浮窗
                        DoKit.launchFloating(ClientDokitView::class)
                    }
                    DoKitWsClient.CONNECT_FAIL -> {
                        LogHelper.e(TAG, "message===>$message")
                        ToastUtils.showShort(message)
                        DokitMcConnectManager.currentClientHistory = null
                    }
                    else -> {
                        LogHelper.e(TAG, "code=$code, message===>$message")
                    }
                }
            }
        }

    }


}
