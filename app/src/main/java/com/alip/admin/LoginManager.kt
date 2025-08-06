package com.alip.admin

import android.content.Context
import android.content.SharedPreferences
import android.provider.Settings
import android.util.Log
import com.google.firebase.firestore.ktx.firestore
import com.google.firebase.ktx.Firebase
import org.mindrot.jbcrypt.BCrypt

class LoginManager(private val context: Context) {

    private val db = Firebase.firestore
    private val prefs: SharedPreferences = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)

    companion object {
        private const val PREF_NAME = "LoginPrefs"
        private const val KEY_IS_LOGGED_IN = "isLoggedIn"
        private const val KEY_LOGGED_IN_USERNAME = "loggedInUsername"
        private const val KEY_LOGGED_IN_EMAIL = "loggedInEmail"
    }

    fun isLoggedIn(): Boolean {
        return prefs.getBoolean(KEY_IS_LOGGED_IN, false)
    }

    fun logout() {
        prefs.edit().clear().apply()
    }

    fun loginWithUsername(username: String, password: String, onComplete: (Boolean, String?) -> Unit) {

        db.collection("Sellers")
            .whereEqualTo("Username", username)
            .get()
            .addOnSuccessListener { documents ->
                if (!documents.isEmpty) {
                    val document = documents.documents[0]
                    val hashedPassword = document.getString("hashedPassword")

                    if (hashedPassword != null && BCrypt.checkpw(password, hashedPassword)) {
                        val storedDeviceId = document.getString("DeviceID")
                        val currentDeviceId = Settings.Secure.getString(context.contentResolver, Settings.Secure.ANDROID_ID)

                        // เพิ่ม Log เพื่อดูค่า DeviceID ที่ดึงมาจากเครื่อง
                        Log.d("LoginDebug", "Current Device ID: $currentDeviceId")
                        Log.d("LoginDebug", "Stored Device ID: $storedDeviceId")

                        if (storedDeviceId == null || storedDeviceId.isEmpty() || storedDeviceId == currentDeviceId) {

                            // แก้ไขโค้ดส่วนการอัปเดตข้อมูล
                            document.reference.update("DeviceID", currentDeviceId)
                                .addOnSuccessListener {
                                    Log.d("LoginDebug", "DeviceID updated successfully.")
                                    val userEmail = document.getString("Email")
                                    prefs.edit().apply {
                                        putBoolean(KEY_IS_LOGGED_IN, true)
                                        putString(KEY_LOGGED_IN_USERNAME, username)
                                        putString(KEY_LOGGED_IN_EMAIL, userEmail)
                                        apply()
                                    }
                                    onComplete(true, null)
                                }
                                .addOnFailureListener { exception ->
                                    // เพิ่ม Log เพื่อแสดง Error หากอัปเดตล้มเหลว
                                    Log.e("LoginError", "Failed to update DeviceID: ", exception)
                                    onComplete(false, "เกิดข้อผิดพลาดในการบันทึกข้อมูลเครื่อง")
                                }
                        } else {
                            onComplete(false, "บัญชีนี้มีการใช้งานบนเครื่องอื่นอยู่แล้ว")
                        }
                    } else {
                        onComplete(false, "ชื่อผู้ใช้หรือรหัสผ่านไม่ถูกต้อง")
                    }
                } else {
                    onComplete(false, "ชื่อผู้ใช้หรือรหัสผ่านไม่ถูกต้อง")
                }
            }
            .addOnFailureListener { exception ->
                Log.e("FirestoreError", "Login failed", exception)
                onComplete(false, "เกิดข้อผิดพลาดในการเข้าสู่ระบบ")
            }
    }
}