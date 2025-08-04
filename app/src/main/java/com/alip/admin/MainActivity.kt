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
import kotlinx.coroutines.delay
import com.alip.admin.Internet.RetrofitClient
import com.alip.admin.Internet.NetworkUtils
import com.alip.admin.Internet.NetworkConnectivityObserver

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private lateinit var networkObserver: NetworkConnectivityObserver
    private var noInternetDialog: Dialog? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // แสดง Pop-up ตรวจสอบอินเทอร์เน็ตทันทีที่ Activity ถูกสร้าง
        val checkInternetDialog = CheckInternetFragment()
        checkInternetDialog.show(supportFragmentManager, "check_internet_tag")

        lifecycleScope.launch {
            // หน่วงเวลา 2 วินาทีเพื่อให้ผู้ใช้เห็น Pop-up
            delay(2000)

            if (isFinishing || isDestroyed) return@launch
            checkInternetDialog.dismiss()

            if (!NetworkUtils.isInternetConnected(this@MainActivity)) {
                // ถ้าไม่มีอินเทอร์เน็ตตั้งแต่ตอนเปิดแอป
                AlertDialog.Builder(this@MainActivity)
                    .setTitle("แจ้งเตือน")
                    .setMessage("ไม่พบการเชื่อมต่ออินเทอร์เน็ต กรุณาตรวจสอบการตั้งค่าเครือข่าย")
                    .setPositiveButton("ตกลง") { dialog, which ->
                        finish()
                    }
                    .setCancelable(false)
                    .show()
            }
        }

        // สร้างและลงทะเบียน observer เพื่อตรวจสอบสถานะเครือข่ายแบบเรียลไทม์
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

        binding.loginButton.setOnClickListener {
            if (NetworkUtils.isInternetConnected(this)) {
                val loadingDialog = LoadingSpinnerFragment()
                loadingDialog.show(supportFragmentManager, "loading_tag")

                lifecycleScope.launch {
                    try {
                        val response = RetrofitClient.apiService.getPost()
                        if (response.isSuccessful) {
                            val post = response.body()
                            Log.d("API_CALL", "Post Title: ${post?.title}")

                            loadingDialog.dismiss()
                            val intent = Intent(this@MainActivity, PanelActivity::class.java)
                            startActivity(intent)
                            finish()

                        } else {
                            Log.e("API_CALL", "Error: ${response.code()}")
                            loadingDialog.dismiss()
                        }
                    } catch (e: Exception) {
                        Log.e("API_CALL", "Network Error: ${e.message}")
                        loadingDialog.dismiss()
                    }
                }
            } else {
                showNoInternetDialog()
            }
        }
    }

    private fun showNoInternetDialog() {
        if (noInternetDialog == null) {
            noInternetDialog = AlertDialog.Builder(this)
                .setTitle("แจ้งเตือน")
                .setMessage("ไม่พบการเชื่อมต่ออินเทอร์เน็ต กรุณาตรวจสอบการตั้งค่าเครือข่าย")
                .setCancelable(false)
                .create()
        }
        noInternetDialog?.show()
    }

    override fun onDestroy() {
        super.onDestroy()
        networkObserver.unregister()
    }
}