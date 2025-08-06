package com.alip.admin

import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import androidx.appcompat.app.AlertDialog
import android.app.Dialog
import androidx.slidingpanelayout.widget.SlidingPaneLayout
import com.alip.admin.Internet.NetworkConnectivityObserver
import com.alip.admin.databinding.ActivityPanelBinding
import androidx.activity.OnBackPressedCallback
import android.view.MenuItem // ยังคงจำเป็นสำหรับการจัดการปุ่มเมนูบน Toolbar
 
class PanelActivity : AppCompatActivity() {
    private lateinit var networkObserver: NetworkConnectivityObserver
    private var noInternetDialog: Dialog? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        networkObserver = NetworkConnectivityObserver(this)
        networkObserver.register { isConnected ->
            if (isConnected) {
                noInternetDialog?.dismiss()
                noInternetDialog = null
            } else {
                if (noInternetDialog == null || !noInternetDialog!!.isShowing) {
                    showNoInternetDialog()
                }
            }
        }
    }

    // เมธอดสำหรับแสดง Dialog เมื่อไม่มีอินเทอร์เน็ต (ยังคงจำเป็น)
    private fun showNoInternetDialog() {
        if (noInternetDialog == null) {
            noInternetDialog = AlertDialog.Builder(this)
                .setTitle("Notice")
                .setMessage("No internet connection found. Please check your network settings.")
                .setCancelable(false)
                .create()
        }
        noInternetDialog?.show()
    }

    // เมธอด onDestroy สำหรับยกเลิกการลงทะเบียน NetworkObserver (ยังคงจำเป็น)
    override fun onDestroy() {
        super.onDestroy()
        networkObserver.unregister()
        if (noInternetDialog?.isShowing == true) {
            noInternetDialog?.dismiss()
        }
    }
}