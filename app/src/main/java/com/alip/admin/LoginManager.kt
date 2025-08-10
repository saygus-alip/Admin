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
    private val sharedPreferences: SharedPreferences = context.getSharedPreferences("login_prefs", Context.MODE_PRIVATE)
    companion object {
        private const val PREF_NAME = "LoginPrefs"
        private const val KEY_IS_LOGGED_IN = "isLoggedIn"
        private const val KEY_LOGGED_IN_USERNAME = "loggedInUsername"
        private const val KEY_LOGGED_IN_EMAIL = "loggedInEmail"
        private const val KEY_LOGGED_IN_CREDIT = "loggedInCredit"
    }


    fun isLoggedIn(): Boolean {
        return sharedPreferences.getBoolean(KEY_IS_LOGGED_IN, false)
    }
    fun getLoggedInEmail(): String? {
        return sharedPreferences.getString(KEY_LOGGED_IN_EMAIL, null)
    }
    fun logout() {
        sharedPreferences.edit().clear().apply()
    }
    fun getLoggedInUsername(): String? {
        return sharedPreferences.getString(KEY_LOGGED_IN_USERNAME, null)
    }
    fun getLoggedInCredit(): Float {
        return sharedPreferences.getFloat("credit_key_name", 0.0f)
    }
    fun updateCredit(creditValue: Float) {
        val editor = sharedPreferences.edit()
        editor.putFloat("credit_key_name", creditValue)
        editor.apply()
    }
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
                                    val userCredit = document.getLong("Credit")?.toInt() ?: 0
                                    sharedPreferences.edit().apply {
                                        putBoolean(KEY_IS_LOGGED_IN, true)
                                        putString(KEY_LOGGED_IN_USERNAME, username)
                                        putString(KEY_LOGGED_IN_EMAIL, userEmail)
                                        putInt(KEY_LOGGED_IN_CREDIT, userCredit)
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