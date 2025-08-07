package com.alip.admin

import android.annotation.SuppressLint
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import com.alip.admin.databinding.ActivityMainBinding
import android.content.Intent
import androidx.appcompat.app.AlertDialog
import android.app.Dialog
import com.alip.admin.Internet.NetworkUtils
import com.alip.admin.Internet.NetworkConnectivityObserver
import android.widget.Toast
import android.text.method.LinkMovementMethod
import android.widget.TextView
import android.net.Uri
import android.view.LayoutInflater
import android.text.Spannable
import android.text.style.ClickableSpan
import android.text.style.ForegroundColorSpan
import android.text.style.StyleSpan
import android.view.View
import android.graphics.Typeface
import androidx.core.content.ContextCompat
import android.text.SpannableStringBuilder
import android.text.TextPaint
import android.util.TypedValue


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
                .setIcon(R.drawable.ic_eazy)
                .setMessage("No internet connection found. Please check your network settings.")
                .setCancelable(false)
                .setPositiveButton("OK") { dialog, _ ->
                    dialog.dismiss()
                }
                .create()
        }
        noInternetDialog?.show()
    }

    // data class ที่เก็บ ชื่อ, ชื่อลิงก์ และลิงก์จริง
    data class ContactInfo(val name: String, val linkText: String, val url: String)

    @SuppressLint("ResourceAsColor")
    private fun showSellerInfoDialog() {
        // สร้าง List ที่เก็บข้อมูลทั้ง 3 ส่วน
        val contactList = listOf(
            ContactInfo("Contact channel for creators Click the link to contact", "Seller OFFICIAL", ""),
            ContactInfo("SayGus", "facebook.com/tan.cham", "https://www.facebook.com/tanwa.chamnandong.7"),
            ContactInfo("Instagram", "instagram.com/alip.tanwaa", "https://instagram.com/alip.tanwaa"), // <<--- แก้ไขตรงนี้
            ContactInfo("Telegram", "t.me/saygusthai", "https://t.me/saygusthai"), // <<--- แก้ไขตรงนี้
            ContactInfo("Whatsapp", "wa.me/+66813465042", "https://wa.me/+66813465042"), // <<--- แก้ไขตรงนี้
            ContactInfo("Youtube", "youtube.com/@saygusthai", "https://www.youtube.com/@saygusthai"),
            ContactInfo("Youtube Live", "youtube.com/streams", "https://www.youtube.com/@AlipYummy/streams"),
            ContactInfo("Github", "github.com/saygus-alip", "https://github.com/saygus-alip")
        )

        val inflater = LayoutInflater.from(this)
        val customView = inflater.inflate(R.layout.dialog_seller, null)
        val sellerInfoTextView = customView.findViewById<TextView>(R.id.sellerInfoTextView)

        val spannableStringBuilder = SpannableStringBuilder()
        val linkColor = ContextCompat.getColor(this, R.color.fill_blue_bg)

        contactList.forEachIndexed { index, info ->
            val nameText = info.name
            spannableStringBuilder.append(nameText)
            spannableStringBuilder.append("\n\n")

            val linkText = info.linkText
            val linkStart = spannableStringBuilder.length
            spannableStringBuilder.append(linkText)
            val linkEnd = spannableStringBuilder.length

            val clickableSpan = object : ClickableSpan() {
                override fun onClick(widget: View) {
                    // แก้ไขตรงนี้: เพิ่มการตรวจสอบ URL ก่อนเปิด
                    var url = info.url
                    if (!url.startsWith("http://") && !url.startsWith("https://")) {
                        url = "https://" + url
                    }
                    val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url))
                    startActivity(intent)
                }
                override fun updateDrawState(ds: TextPaint) {
                    super.updateDrawState(ds)
                    ds.isUnderlineText = false
                }
            }
            spannableStringBuilder.setSpan(clickableSpan, linkStart, linkEnd, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)
            spannableStringBuilder.setSpan(ForegroundColorSpan(linkColor), linkStart, linkEnd, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)

            if (index < contactList.size - 1) {
                spannableStringBuilder.append("\n\n")
            }
        }

        sellerInfoTextView.text = spannableStringBuilder
        sellerInfoTextView.movementMethod = LinkMovementMethod.getInstance()
        sellerInfoTextView.highlightColor = android.R.color.transparent
        sellerInfoTextView.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f)

        AlertDialog.Builder(this)
            .setTitle("Notice! This app is for sellers only")
            .setIcon(R.drawable.ic_eazy)
            .setView(customView)
            .setCancelable(false)
            .setPositiveButton("OK") { dialog, _ ->
                dialog.dismiss()
            }
            .show()
    }

    override fun onDestroy() {
        super.onDestroy()
        networkObserver.unregister()
        if (noInternetDialog?.isShowing == true) {
            noInternetDialog?.dismiss()
        }
    }
}