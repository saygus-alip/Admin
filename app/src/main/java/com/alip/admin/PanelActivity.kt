package com.alip.admin

import android.os.Bundle
import android.content.Intent
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.slidingpanelayout.widget.SlidingPaneLayout
import com.alip.admin.R
import com.google.firebase.firestore.ktx.firestore
import com.google.firebase.ktx.Firebase

class PanelActivity : AppCompatActivity() {

    private lateinit var loginManager: LoginManager
    private val db = Firebase.firestore
    private lateinit var slidingPaneLayout: SlidingPaneLayout

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_panel)

        // ค้นหา SlidingPaneLayout
        slidingPaneLayout = findViewById(R.id.sliding_pane_layout)

        // Initialize LoginManager
        loginManager = LoginManager(this)

        // ... (โค้ดอื่นๆ ใน onCreate เช่น การกำหนดค่า View หรือ Listener ต่างๆ)
        // คุณสามารถเพิ่มโค้ดสำหรับปุ่มเปิดเมนูที่นี่ได้
        // val openMenuButton = findViewById<Button>(R.id.open_menu_button)
        // openMenuButton.setOnClickListener {
        //     slidingPaneLayout.open()
        // }
    }

    override fun onResume() {
        super.onResume()
        // เรียกเมธอดตรวจสอบ DeviceID ทุกครั้งที่ Activity กลับมาทำงาน (onResume)
        checkDeviceID()
    }

    private fun checkDeviceID() {
        val username = loginManager.getLoggedInUsername()

        // ตรวจสอบว่ามีผู้ใช้เข้าสู่ระบบอยู่หรือไม่
        if (username != null) {
            db.collection("Sellers")
                .whereEqualTo("Username", username)
                .get()
                .addOnSuccessListener { documents ->
                    if (!documents.isEmpty) {
                        val document = documents.documents[0]
                        val storedDeviceId = document.getString("DeviceID")
                        val currentDeviceId = loginManager.getCurrentDeviceId()

                        // ถ้า DeviceID ที่เก็บใน Firestore ไม่ตรงกับ DeviceID ของเครื่องปัจจุบัน
                        if (storedDeviceId != null && storedDeviceId != currentDeviceId) {
                            // สั่ง Logout ทันที
                            loginManager.logout()
                            Toast.makeText(this, "Your account is already logged in on another device.", Toast.LENGTH_LONG).show()

                            // ย้ายไปยังหน้า Login และเคลียร์ Activity Stack
                            val intent = Intent(this, MainActivity::class.java)
                            intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                            startActivity(intent)
                            finish()
                        }
                    } else {
                        // กรณีหาข้อมูลผู้ใช้ใน Firestore ไม่เจอ
                        logoutAndRedirect()
                    }
                }
                .addOnFailureListener {
                    // กรณีเกิดข้อผิดพลาดในการดึงข้อมูลจาก Firestore
                    logoutAndRedirect()
                }
        } else {
            // กรณีไม่มี Username แสดงว่าไม่ได้ล็อกอินอยู่
            logoutAndRedirect()
        }
    }

    // เมธอดช่วยในการ Logout และย้ายไปยังหน้า Login
    private fun logoutAndRedirect() {
        loginManager.logout()
        val intent = Intent(this, MainActivity::class.java)
        intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        startActivity(intent)
        finish()
    }
}
