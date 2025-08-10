package com.alip.admin.Fragment

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.Fragment
import com.alip.admin.LoginManager
import com.alip.admin.databinding.RenewUserBinding
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.ktx.firestore
import com.google.firebase.ktx.Firebase
import com.alip.admin.LoadingSpinnerFragment
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import com.alip.admin.Data.ActivityLog
import com.google.firebase.Timestamp
import android.util.Log

class RenewUserFragment : Fragment() {

    private var _binding: RenewUserBinding? = null
    private val binding get() = _binding!!
    private lateinit var loginManager: LoginManager
    private val db = Firebase.firestore
    private val loadingSpinner = LoadingSpinnerFragment()

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        _binding = RenewUserBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        loginManager = LoginManager(requireContext())

        binding.renewUser.setOnClickListener {
            renewUser()
        }
    }

    private fun renewUser() {
        val targetUsername = binding.usernameEditText.text.toString().trim()
        val expiredDaysStr = binding.renewEditText.text.toString().trim()
        val adminEmail = loginManager.getLoggedInEmail()

        // 1. ตรวจสอบข้อมูลที่กรอกเข้ามา
        if (targetUsername.isEmpty() || expiredDaysStr.isEmpty()) {
            Toast.makeText(requireContext(), "Please fill in all fields", Toast.LENGTH_SHORT).show()
            return
        }

        val expiredDays = expiredDaysStr.toIntOrNull()
        if (expiredDays == null || expiredDays < 10 || expiredDays > 100 || expiredDays % 10 != 0) {
            binding.textInputLayoutRenew.error = "Renewal days must be 10, 20, ..., 100"
            return
        }

        if (adminEmail == null) {
            Toast.makeText(requireContext(), "Admin user not found. Please log in again.", Toast.LENGTH_LONG).show()
            return
        }

        // 2. คำนวณค่าใช้จ่าย Credit และตรวจสอบ
        val creditCost = expiredDays / 10f
        val currentCredit = loginManager.getLoggedInCredit()

        if (currentCredit < creditCost) {
            showCreditAlertDialog()
            return
        }

        // แสดงหน้าโหลด
        loadingSpinner.show(parentFragmentManager, "loading_spinner")

        // 3. ตรวจสอบสิทธิ์และดึงข้อมูล User
        db.collection("Users").document(targetUsername).get()
            .addOnSuccessListener { userDocument ->
                if (userDocument.exists()) {
                    val createdByEmail = userDocument.getString("CreatedBy")
                    val currentExpiredDateStr = userDocument.getString("Expired")

                    if (createdByEmail == adminEmail) {
                        // 4. คำนวณวันหมดอายุใหม่
                        val newExpiredDate = calculateNewExpiredDate(currentExpiredDateStr, expiredDays)
                        val newCredit = currentCredit - creditCost

                        // 5. ใช้ runBatch เพื่ออัปเดตข้อมูลพร้อมกัน
                        db.runBatch { batch ->
                            val userRef = db.collection("Users").document(targetUsername)
                            batch.update(userRef, "Expired", newExpiredDate)

                            val adminRef = db.collection("Sellers").document(adminEmail)
                            batch.update(adminRef, "Credit", newCredit.toDouble())
                        }.addOnSuccessListener {
                            loadingSpinner.dismiss()
                            loginManager.updateCredit(newCredit)
                            Toast.makeText(requireContext(), "User '$targetUsername' renewed for $expiredDays days! Your credit has been deducted.", Toast.LENGTH_SHORT).show()
                            binding.renewEditText.text?.clear()

                            // แก้ไข Log สำหรับการต่ออายุผู้ใช้เพื่อให้สามารถปรับแต่งข้อความได้
                            val adminUsername = loginManager.getLoggedInUsername()
                            val action = "Renew User"
                            val details = "$adminUsername renewed $targetUsername for $expiredDays days"
                            saveActivityLog(action, details, adminEmail, creditCost)

                        }.addOnFailureListener { e ->
                            loadingSpinner.dismiss()
                            Toast.makeText(requireContext(), "Error renewing user: ${e.message}", Toast.LENGTH_LONG).show()
                        }
                    } else {
                        loadingSpinner.dismiss()
                        Toast.makeText(requireContext(), "You can only renew users you created.", Toast.LENGTH_SHORT).show()
                    }
                } else {
                    loadingSpinner.dismiss()
                    Toast.makeText(requireContext(), "User '$targetUsername' not found.", Toast.LENGTH_SHORT).show()
                }
            }
            .addOnFailureListener { e ->
                loadingSpinner.dismiss()
                Toast.makeText(requireContext(), "Failed to access database: ${e.message}", Toast.LENGTH_LONG).show()
            }
    }

    private fun calculateNewExpiredDate(currentDateStr: String?, days: Int): String {
        val dateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
        val calendar = Calendar.getInstance()

        if (currentDateStr != null && currentDateStr.isNotBlank()) {
            val currentDate = dateFormat.parse(currentDateStr)
            if (currentDate != null) {
                // ถ้าวันหมดอายุเดิมยังไม่ถึง ให้ต่ออายุจากวันนั้น
                if (currentDate.after(Date())) {
                    calendar.time = currentDate
                }
            }
        }

        calendar.add(Calendar.DAY_OF_YEAR, days)
        return dateFormat.format(calendar.time)
    }

    // ฟังก์ชันใหม่สำหรับบันทึก Log ลง Firestore
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
                Log.d("RenewUserFragment", "Activity Log added successfully.")
            }
            .addOnFailureListener { e ->
                Log.w("RenewUserFragment", "Error adding activity log", e)
            }
    }

    private fun showCreditAlertDialog() {
        AlertDialog.Builder(requireContext())
            .setTitle("Not Enough Credit")
            .setMessage("You do not have enough credit to renew this user.")
            .setPositiveButton("OK") { _, _ -> }
            .show()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
