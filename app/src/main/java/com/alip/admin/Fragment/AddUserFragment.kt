package com.alip.admin.Fragment

import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.fragment.app.Fragment
import com.alip.admin.LoginManager
import com.alip.admin.databinding.AddUserBinding
import com.google.firebase.firestore.ktx.firestore
import com.google.firebase.ktx.Firebase
import org.mindrot.jbcrypt.BCrypt
import java.util.Calendar
import androidx.appcompat.app.AlertDialog
import com.alip.admin.R
import com.alip.admin.LoadingSpinnerFragment
import com.alip.admin.Data.ActivityLog
import com.google.firebase.Timestamp
import java.util.Date

class AddUserFragment : Fragment() {

    private var _binding: AddUserBinding? = null
    private val binding get() = _binding!!
    private lateinit var loginManager: LoginManager
    private val db = Firebase.firestore
    private val loadingSpinner = LoadingSpinnerFragment()

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        _binding = AddUserBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        loginManager = LoginManager(requireContext())

        binding.register.setOnClickListener {
            registerNewUser()
        }
    }
    private fun registerNewUser() {
        val username = binding.usernameEditText.text.toString().trim()
        val password = binding.passwordEditText.text.toString().trim()
        val expiredDaysStr = binding.expiredEditText.text.toString().trim()
        val adminEmail = loginManager.getLoggedInEmail()
        val adminUsername = loginManager.getLoggedInUsername()

        if (username.isEmpty() || password.isEmpty() || password.length < 6 || expiredDaysStr.isEmpty()) {
            Toast.makeText(requireContext(), "Please fill in all fields", Toast.LENGTH_SHORT).show()
            return
        }

        val expiredDays = expiredDaysStr.toIntOrNull()
        if (expiredDays == null || expiredDays < 10 || expiredDays > 100 || expiredDays % 10 != 0) {
            binding.textInputLayoutExpired.error = "Expired days must be 10, 20, ..., 100"
            return
        }

        if (adminEmail == null) {
            Toast.makeText(requireContext(), "Admin user not found. Please log in again.", Toast.LENGTH_LONG).show()
            return
        }

        val creditCost = expiredDays / 10
        val currentCredit = loginManager.getLoggedInCredit()

        if (currentCredit < creditCost) {
            showCreditAlertDialog()
            return
        }

        loadingSpinner.show(parentFragmentManager, "loading_spinner")

        val newCredit = currentCredit - creditCost
        val hashedPassword = BCrypt.hashpw(password, BCrypt.gensalt())

        val newUser = hashMapOf(
            "Username" to username,
            "hashedPassword" to hashedPassword,
            "DeviceId" to "none",
            "CreatedBy" to adminEmail,
            "isLogin" to false,
            "Seller" to adminUsername,
            "Expired" to calculateExpiredDate(expiredDays)
        )

        db.runBatch { batch ->
            val adminRef = db.collection("Sellers").document(adminEmail)
            batch.update(adminRef, "Credit", newCredit)

            val newUserRef = db.collection("Users").document(username)
            batch.set(newUserRef, newUser)
        }.addOnSuccessListener {
            loadingSpinner.dismiss()
            loginManager.updateCredit(newCredit)
            Toast.makeText(requireContext(), "User $username registered successfully! Your credit has been deducted.", Toast.LENGTH_SHORT).show()

            // แก้ไข: บันทึก Activity Log ที่สมบูรณ์ยิ่งขึ้น
            val action = "Add User"
            val details = "$adminUsername added $username with $expiredDays days"
            saveActivityLog(action, details, adminEmail, creditCost.toFloat())

            binding.passwordEditText.text?.clear()
            binding.expiredEditText.text?.clear()
        }.addOnFailureListener { e ->
            loadingSpinner.dismiss()
            Toast.makeText(requireContext(), "Error registering user: ${e.message}", Toast.LENGTH_LONG).show()
        }
    }

    // ฟังก์ชันสำหรับบันทึก Log ลง Firestore
    private fun saveActivityLog(action: String, details: String, adminEmail: String, cost: Float) {
        val log = ActivityLog(
            action = action,
            details = details,
            status = "Success",
            timestamp = Timestamp(Date()),
            adminEmail = adminEmail,
            cost = cost
        )

        db.collection("ActivityLog")
            .add(log)
            .addOnSuccessListener {
                Log.d("AddUserFragment", "Activity Log added successfully.")
            }
            .addOnFailureListener { e ->
                Log.w("AddUserFragment", "Error adding activity log", e)
            }
    }

    private fun showCreditAlertDialog() {
        AlertDialog.Builder(requireContext())
            .setTitle("Not Enough Credit")
            .setMessage("You do not have enough credit to create this user.")
            .setPositiveButton("OK") { _, _ -> }
            .show()
    }

    private fun calculateExpiredDate(days: Int): String {
        val calendar = Calendar.getInstance()
        calendar.add(Calendar.DAY_OF_YEAR, days)
        val year = calendar.get(Calendar.YEAR)
        val month = calendar.get(Calendar.MONTH) + 1
        val day = calendar.get(Calendar.DAY_OF_MONTH)
        return String.format("%d-%02d-%02d", year, month, day)
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
