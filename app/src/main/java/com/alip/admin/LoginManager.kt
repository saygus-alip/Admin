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
        // ล้างข้อมูลทั้งหมดใน SharedPreferences
        prefs.edit().clear().apply()
    }

    // เมธอดใหม่: ใช้สำหรับดึงค่า Username ของผู้ใช้ที่เข้าสู่ระบบอยู่
    fun getLoggedInUsername(): String? {
        return prefs.getString(KEY_LOGGED_IN_USERNAME, null)
    }

    // เมธอดใหม่: ใช้สำหรับดึงค่า DeviceID ของเครื่องปัจจุบัน
    fun getCurrentDeviceId(): String {
        return Settings.Secure.getString(context.contentResolver, Settings.Secure.ANDROID_ID)
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
                        val currentDeviceId = getCurrentDeviceId() // ใช้เมธอดใหม่ที่สร้างขึ้น

                        Log.d("LoginDebug", "Current Device ID: $currentDeviceId")
                        Log.d("LoginDebug", "Stored Device ID: $storedDeviceId")

                        if (storedDeviceId == null || storedDeviceId.isEmpty() || storedDeviceId == currentDeviceId) {

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
                                    Log.e("LoginError", "Failed to update DeviceID: ", exception)
                                    onComplete(false, "An error occurred while saving the device data.")
                                }
                        } else {
                            onComplete(false, "This account is already in use on another device.")
                        }
                    } else {
                        onComplete(false, "Username or password is incorrect.")
                    }
                } else {
                    onComplete(false, "Username or password is incorrect.")
                }
            }
            .addOnFailureListener { exception ->
                Log.e("FirestoreError", "Login failed", exception)
                onComplete(false, "An error occurred logging in.")
            }
    }
}