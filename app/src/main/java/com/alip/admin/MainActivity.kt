package com.alip.admin

import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import com.alip.admin.databinding.ActivityMainBinding
import android.content.Intent
import android.util.Log
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.launch
import androidx.appcompat.app.AlertDialog
import com.alip.admin.Internet.RetrofitClient
import com.alip.admin.Internet.NetworkUtils
import kotlinx.coroutines.delay // เพิ่ม import delay

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        val checkInternetDialog = CheckInternetFragment()
        checkInternetDialog.show(supportFragmentManager, "check_internet_tag")

        // ใช้ lifecycleScope.launch แทน Handler
        lifecycleScope.launch {
            // หน่วงเวลา 2 วินาที
            delay(2000)

            // ตรวจสอบว่า Activity ยังอยู่และ Pop-up ยังแสดงอยู่หรือไม่
            if (isFinishing || isDestroyed) return@launch
            checkInternetDialog.dismiss()

            if (NetworkUtils.isInternetConnected(this@MainActivity)) {
                val intent = Intent(this@MainActivity, PanelActivity::class.java)
                startActivity(intent)
                finish()
            } else {
                AlertDialog.Builder(this@MainActivity)
                    .setTitle("แจ้งเตือน")
                    .setMessage("ไม่พบการเชื่อมต่ออินเทอร์เน็ต กรุณาตรวจสอบการตั้งค่าเครือข่าย")
                    .setPositiveButton("ตกลง") { dialog, which ->
                        finish()
                    }
                    .show()
            }
        }
    }
}