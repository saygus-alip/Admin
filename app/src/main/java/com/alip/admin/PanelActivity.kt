package com.alip.admin

import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import androidx.appcompat.app.AlertDialog
import android.app.Dialog
import androidx.slidingpanelayout.widget.SlidingPaneLayout
import com.alip.admin.Internet.NetworkConnectivityObserver
import com.alip.admin.databinding.ActivityPanelBinding
import androidx.activity.OnBackPressedCallback

class PanelActivity : AppCompatActivity() {

    private lateinit var binding: ActivityPanelBinding
    private lateinit var slidingPaneLayout: SlidingPaneLayout
    private lateinit var networkObserver: NetworkConnectivityObserver
    private var noInternetDialog: Dialog? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityPanelBinding.inflate(layoutInflater)
        setContentView(binding.root)

        slidingPaneLayout = binding.root as SlidingPaneLayout

        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                if (slidingPaneLayout.isSlideable && slidingPaneLayout.isOpen) {
                    slidingPaneLayout.close()
                } else {
                    isEnabled = false
                    onBackPressedDispatcher.onBackPressed()
                }
            }
        })

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

    override fun onDestroy() {
        super.onDestroy()
        networkObserver.unregister()
        if (noInternetDialog?.isShowing == true) {
            noInternetDialog?.dismiss()
        }
    }
}