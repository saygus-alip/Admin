package com.alip.admin

import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.google.firebase.firestore.ktx.firestore
import com.google.firebase.ktx.Firebase
import androidx.constraintlayout.motion.widget.MotionLayout
import androidx.fragment.app.Fragment
import com.alip.admin.databinding.ActivityPanelBinding
import com.alip.admin.Fragment.AddUserFragment
import com.alip.admin.Fragment.ChangePasswordFragment
import com.alip.admin.Fragment.ExportUserFragment
import com.alip.admin.Fragment.RenewUserFragment
import com.alip.admin.Fragment.ActivityLogFragment
import com.alip.admin.Fragment.SearchUserFragment
import com.alip.admin.Fragment.SettingFragment
import android.content.res.ColorStateList
import android.widget.ImageButton
import androidx.appcompat.app.AlertDialog
import android.view.View
import android.widget.TextView

class PanelActivity : AppCompatActivity() {

    private lateinit var loginManager: LoginManager
    private val db = Firebase.firestore

    private lateinit var motionLayout: MotionLayout
    private lateinit var binding: ActivityPanelBinding

    // ตัวแปรสำหรับเก็บ ImageButton ที่ถูกเลือกในปัจจุบัน (สำหรับ Compact Menu)
    private var activeCompactIcon: ImageButton? = null

    // ตัวแปรสำหรับเก็บ View ที่ถูกเลือกในปัจจุบัน (สำหรับ Full Menu)
    private var activeFullMenuItem: View? = null

    // สีสำหรับไอคอนที่เลือกและไม่ถูกเลือก
    private val selectedColor by lazy { resources.getColor(R.color.selected_icon_color, theme) }
    private val defaultColor by lazy { resources.getColor(R.color.default_icon_color, theme) }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        binding = ActivityPanelBinding.inflate(layoutInflater)
        setContentView(binding.root)

        motionLayout = findViewById(R.id.motion_layout)
        loginManager = LoginManager(this)

        setupRealtimeListener()

        val username = loginManager.getLoggedInUsername()
        val credit = loginManager.getLoggedInCredit()

        binding.fullMenuUser.text = "Seller: $username"
        binding.fullMenuCredit.text = "Credit: $credit"

        setSupportActionBar(binding.toolbar)

        // โหลด Fragment และตั้งค่าสีไอคอนเริ่มต้น
        if (savedInstanceState == null) {
            loadFragment(AddUserFragment())
            setActiveIcon(binding.iconAdd, binding.fullMenuAddUserContainer)
        }

        setupMenuListeners()
    }

    private fun setupRealtimeListener() {
        val username = loginManager.getLoggedInUsername()

        if (username != null) {
            db.collection("Sellers")
                .whereEqualTo("Username", username)
                .addSnapshotListener { snapshots, e ->
                    if (e != null) {
                        Toast.makeText(this, "Error fetching data: ${e.message}", Toast.LENGTH_SHORT).show()
                        return@addSnapshotListener
                    }

                    if (snapshots != null && !snapshots.isEmpty) {
                        val document = snapshots.documents[0]
                        val updatedUsername = document.getString("Username")
                        val updatedCreditFromFirestore = document.getDouble("Credit")
                        val creditToUpdate = updatedCreditFromFirestore?.toFloat() ?: 0.0f

                        // อัปเดต UI ด้วยค่าใหม่
                        binding.fullMenuUser.text = "Username: $updatedUsername"
                        binding.fullMenuCredit.text = "Credit: $creditToUpdate"

                        loginManager.updateCredit(creditToUpdate)
                    }
                }
        }
    }

    override fun onResume() {
        super.onResume()
        checkDeviceID()
    }

    /**
     * ตั้งค่า Listener สำหรับปุ่มต่างๆ ใน Compact Menu และ Full Menu
     */
    private fun setupMenuListeners() {
        // Listeners สำหรับ Compact Menu
        binding.iconAdd.setOnClickListener { selectMenuItem(AddUserFragment(), binding.iconAdd, binding.fullMenuAddUserContainer) }
        binding.iconSetting.setOnClickListener { selectMenuItem(SettingFragment(), binding.iconSetting, binding.fullMenuSettingContainer) }
        binding.iconPass.setOnClickListener { selectMenuItem(ChangePasswordFragment(), binding.iconPass, binding.fullMenuPassContainer) }
        binding.iconClock.setOnClickListener { selectMenuItem(RenewUserFragment(), binding.iconClock, binding.fullMenuClockContainer) }
        binding.iconGroup.setOnClickListener { selectMenuItem(SearchUserFragment(), binding.iconGroup, binding.fullMenuGroupContainer) }
        binding.iconUpload.setOnClickListener { selectMenuItem(ExportUserFragment(), binding.iconUpload, binding.fullMenuUploadContainer) }
        binding.iconLog.setOnClickListener { selectMenuItem(ActivityLogFragment(), binding.iconLog, binding.fullMenuLogContainer) }
        binding.iconExit.setOnClickListener { showLogoutConfirmationDialog() }

        // Listeners สำหรับ Full Menu
        binding.fullMenuAddUserContainer.setOnClickListener { selectMenuItem(AddUserFragment(), binding.iconAdd, binding.fullMenuAddUserContainer) }
        binding.fullMenuSettingContainer.setOnClickListener { selectMenuItem(SettingFragment(), binding.iconSetting, binding.fullMenuSettingContainer) }
        binding.fullMenuPassContainer.setOnClickListener { selectMenuItem(ChangePasswordFragment(), binding.iconPass, binding.fullMenuPassContainer) }
        binding.fullMenuClockContainer.setOnClickListener { selectMenuItem(RenewUserFragment(), binding.iconClock, binding.fullMenuClockContainer) }
        binding.fullMenuGroupContainer.setOnClickListener { selectMenuItem(SearchUserFragment(), binding.iconGroup, binding.fullMenuGroupContainer) }
        binding.fullMenuUploadContainer.setOnClickListener { selectMenuItem(ExportUserFragment(), binding.iconUpload, binding.fullMenuUploadContainer) }
        binding.fullMenuLogContainer.setOnClickListener { selectMenuItem(ActivityLogFragment(), binding.iconLog, binding.fullMenuLogContainer) }
        binding.fullMenuExitContainer.setOnClickListener { showLogoutConfirmationDialog() }
    }

    /**
     * ฟังก์ชันสำหรับจัดการการเลือกเมนู
     * - โหลด Fragment ใหม่
     * - อัปเดตการแสดงผลของไอคอนที่ถูกเลือก
     * - ปิดเมนูแบบเต็มโดยใช้แอนิเมชัน
     *
     * @param fragment Fragment ที่ต้องการแสดงผล
     * @param compactIcon ImageButton ของเมนูแบบย่อ
     * @param fullMenuItem View ของเมนูแบบเต็ม
     */
    private fun selectMenuItem(fragment: Fragment, compactIcon: ImageButton, fullMenuItem: View) {
        // โหลด Fragment ใหม่เข้าไปใน FragmentContainerView
        supportFragmentManager.beginTransaction()
            .replace(R.id.fragment_container, fragment)
            .commit()

        // อัปเดตสีของไอคอน
        setActiveIcon(compactIcon, fullMenuItem)

        // สั่งให้ MotionLayout กลับไปที่สถานะเริ่มต้น (เมนูแบบย่อ)
        motionLayout.transitionToStart()
    }

    /**
     * ฟังก์ชันสำหรับจัดการการเปลี่ยนสีไอคอนและพื้นหลังเมนูที่ถูกเลือก
     * - เปลี่ยนสีไอคอนและพื้นหลังที่เคยถูกเลือกให้กลับเป็นสีปกติ
     * - เปลี่ยนสีไอคอนและพื้นหลังที่เพิ่งถูกกดให้เป็นสี Active
     *
     * @param compactIcon ImageButton ที่เพิ่งถูกกดในเมนูแบบย่อ
     * @param fullMenuItem View ที่เพิ่งถูกกดในเมนูแบบเต็ม
     */
    private fun setActiveIcon(compactIcon: ImageButton, fullMenuItem: View) {
        // เปลี่ยนสีไอคอนที่เคยถูกเลือกกลับเป็นสี Inactive
        activeCompactIcon?.drawable?.setTintList(ColorStateList.valueOf(defaultColor))
        activeFullMenuItem?.setBackgroundResource(android.R.color.transparent)

        // เปลี่ยนสีไอคอนที่เพิ่งถูกกดให้เป็นสี Active และเปลี่ยนพื้นหลัง
        compactIcon.drawable?.setTintList(ColorStateList.valueOf(selectedColor))
        fullMenuItem.setBackgroundResource(R.drawable.menu_selected_background) // สมมติว่ามี drawable ชื่อนี้

        // อัปเดตตัวแปร activeIcon ให้ชี้ไปที่ไอคอนที่เพิ่งถูกกด
        activeCompactIcon = compactIcon
        activeFullMenuItem = fullMenuItem
    }

    /**
     * แสดงหน้าต่างยืนยันการออกจากระบบ
     */
    private fun showLogoutConfirmationDialog() {
        AlertDialog.Builder(this)
            .setIcon(R.drawable.ic_eazy)
            .setTitle("Notice")
            .setMessage("You want to Logout Account?")
            .setCancelable(false)
            .setPositiveButton("Yes") { dialog, which ->
                logoutAndRedirect()
            }
            .setNegativeButton("No", null)
            .show()
    }

    private fun loadFragment(fragment: Fragment) {
        supportFragmentManager.beginTransaction()
            .replace(R.id.fragment_container, fragment)
            .commit()
    }

    private fun checkDeviceID() {
        val username = loginManager.getLoggedInUsername()

        if (username != null) {
            db.collection("Sellers")
                .whereEqualTo("Username", username)
                .get()
                .addOnSuccessListener { documents ->
                    if (!documents.isEmpty) {
                        val document = documents.documents[0]
                        val storedDeviceId = document.getString("DeviceID")
                        val currentDeviceId = loginManager.getCurrentDeviceId()

                        if (storedDeviceId != null && storedDeviceId != currentDeviceId) {
                            loginManager.logout()
                            Toast.makeText(this, "Your account is already logged in on another device!", Toast.LENGTH_LONG).show()

                            val intent = Intent(this, MainActivity::class.java)
                            intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                            startActivity(intent)
                            finish()
                        }
                    } else {
                        logoutAndRedirect()
                    }
                }
                .addOnFailureListener {
                    logoutAndRedirect()
                }
        } else {
            logoutAndRedirect()
        }
    }

    private fun logoutAndRedirect() {
        loginManager.logout()
        val intent = Intent(this, MainActivity::class.java)
        intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        startActivity(intent)
        finish()
    }
}
