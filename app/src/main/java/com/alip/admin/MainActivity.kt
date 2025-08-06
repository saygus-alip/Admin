package com.alip.admin

import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import com.alip.admin.databinding.ActivityMainBinding
import android.content.Intent
import android.util.Log
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.launch
import androidx.appcompat.app.AlertDialog
import android.app.Dialog
import com.alip.admin.Internet.NetworkUtils
import com.alip.admin.Internet.NetworkConnectivityObserver
import com.alip.admin.LoadingSpinnerFragment
import android.widget.Toast
import androidx.core.text.HtmlCompat
import android.text.method.LinkMovementMethod
import android.widget.TextView

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private lateinit var networkObserver: NetworkConnectivityObserver
    private var noInternetDialog: Dialog? = null
    private lateinit var loginManager: LoginManager

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // สร้างและลงทะเบียน networkObserver
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

        // Initialize LoginManager
        loginManager = LoginManager(this)

        // **ขั้นตอนที่ 1: ตรวจสอบสถานะการล็อกอินก่อน**
        if (loginManager.isLoggedIn()) {
            val intent = Intent(this, PanelActivity::class.java)
            startActivity(intent)
            finish()
            return
        }

        // **ขั้นตอนที่ 2: ถ้ายังไม่ได้ล็อกอิน ให้ตรวจสอบอินเทอร์เน็ต**
        if (NetworkUtils.isInternetConnected(this)) {
            // ถ้ามีอินเทอร์เน็ต ให้แสดง Dialog ข้อมูลผู้ขาย
            showSellerInfoDialog()
        } else {
            // ถ้าไม่มีอินเทอร์เน็ต ให้แสดง Dialog แจ้งเตือน
            showNoInternetDialog()
        }

        binding.loginButton.setOnClickListener {
            if (!NetworkUtils.isInternetConnected(this)) {
                showNoInternetDialog()
                return@setOnClickListener
            }

            val username = binding.usernameInput.text.toString().trim()
            val password = binding.passwordInput.text.toString().trim()

            if (username.isEmpty() || password.isEmpty()) {
                Toast.makeText(this, "Please enter your username and password.", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            val loadingDialog = LoadingSpinnerFragment()
            loadingDialog.show(supportFragmentManager, "loading_tag")

            loginManager.loginWithUsername(username, password) { success, message ->
                loadingDialog.dismiss()

                if (success) {
                    Toast.makeText(this, "Login successful", Toast.LENGTH_SHORT).show()
                    val intent = Intent(this@MainActivity, PanelActivity::class.java)
                    startActivity(intent)
                    finish()
                } else {
                    Toast.makeText(this, message, Toast.LENGTH_LONG).show()
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

    private fun showSellerInfoDialog() {
        val message = HtmlCompat.fromHtml(
            """
            <b>สร้างโดย:</b><br/>
            ชื่อผู้ขาย<br/><br/>
            <b>ติดต่อ:</b><br/>
            <a href="mailto:your.email@example.com">your.email@example.com</a>
            """, HtmlCompat.FROM_HTML_MODE_LEGACY
        )

        AlertDialog.Builder(this)
            .setTitle("Notice: This app is for sellers only")
            .setMessage(message)
            .setPositiveButton("OK") { dialog, _ ->
                dialog.dismiss()
            }
            .show().apply {
                findViewById<TextView>(android.R.id.message)?.movementMethod = LinkMovementMethod.getInstance()
            }
    }

    override fun onDestroy() {
        super.onDestroy()
        networkObserver.unregister()
        if (noInternetDialog?.isShowing == true) {
            noInternetDialog?.dismiss()
        }
    }
}