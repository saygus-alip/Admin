package com.alip.admin.Fragment

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.Fragment
import com.alip.admin.LoginManager
import com.alip.admin.databinding.SettingUserBinding
import com.google.firebase.firestore.ktx.firestore
import com.google.firebase.ktx.Firebase
import com.alip.admin.LoadingSpinnerFragment
import com.google.firebase.Timestamp
import java.util.Date
import com.alip.admin.Data.ActivityLog
import android.util.Log

class SettingFragment : Fragment() {

    private var _binding: SettingUserBinding? = null
    private val binding get() = _binding!!
    private lateinit var loginManager: LoginManager
    private val db = Firebase.firestore
    private val loadingSpinner = LoadingSpinnerFragment()

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        _binding = SettingUserBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        loginManager = LoginManager(requireContext())
        val loggedInUsername = loginManager.getLoggedInUsername()

        binding.remover.setOnClickListener {
            handleUserRemoval()
        }

        binding.reset.setOnClickListener {
            handleDeviceReset()
        }
    }

    private fun handleUserRemoval() {
        val targetUsername = binding.usernameEditText.text.toString().trim()
        val adminEmail = loginManager.getLoggedInEmail()

        if (targetUsername.isEmpty() || adminEmail == null) {
            Toast.makeText(requireContext(), "Error: Missing user data.", Toast.LENGTH_SHORT).show()
            return
        }

        loadingSpinner.show(parentFragmentManager, "loading_spinner")

        db.collection("Users").document(targetUsername).get()
            .addOnSuccessListener { document ->
                loadingSpinner.dismiss()
                if (document.exists()) {
                    val createdByEmail = document.getString("CreatedBy")

                    // ตรวจสอบว่า Admin เป็นผู้สร้างผู้ใช้คนนี้หรือไม่
                    if (createdByEmail == adminEmail) {
                        showRemovalConfirmationDialog(targetUsername, adminEmail)
                    } else {
                        Toast.makeText(
                            requireContext(),
                            "You can only delete users you created.",
                            Toast.LENGTH_SHORT
                        ).show()
                    }
                } else {
                    Toast.makeText(requireContext(), "User not found.", Toast.LENGTH_SHORT).show()
                }
            }
            .addOnFailureListener {
                loadingSpinner.dismiss()
                Toast.makeText(requireContext(), "Failed to access database.", Toast.LENGTH_SHORT)
                    .show()
            }
    }

    private fun showRemovalConfirmationDialog(username: String, adminEmail: String) {
        AlertDialog.Builder(requireContext())
            .setTitle("Confirm Removal")
            .setMessage("Are you sure you want to remove user '$username'?")
            .setPositiveButton("Remove") { dialog, _ ->
                removeUser(username, adminEmail)
                dialog.dismiss()
            }
            .setNegativeButton("Cancel") { dialog, _ ->
                dialog.dismiss()
            }
            .show()
    }

    private fun removeUser(username: String, adminEmail: String) {
        loadingSpinner.show(parentFragmentManager, "loading_spinner")
        db.collection("Users").document(username)
            .delete()
            .addOnSuccessListener {
                loadingSpinner.dismiss()
                Toast.makeText(
                    requireContext(),
                    "User '$username' has been removed.",
                    Toast.LENGTH_SHORT
                ).show()

                // เพิ่ม Log สำหรับการลบผู้ใช้
                val adminUsername = loginManager.getLoggedInUsername()
                val action = "Remove User"
                val details = "$adminUsername removed $username"
                val cost = 0.0f // การลบผู้ใช้ไม่มีค่าใช้จ่าย
                saveActivityLog(action, details, adminEmail, cost)
            }
            .addOnFailureListener {
                loadingSpinner.dismiss()
                Toast.makeText(requireContext(), "Failed to remove user.", Toast.LENGTH_SHORT)
                    .show()
            }
    }

    private fun handleDeviceReset() {
        val targetUsername = binding.usernameEditText.text.toString().trim()
        val adminEmail = loginManager.getLoggedInEmail()
        val creditCost = 0.25f // แก้ไข: เปลี่ยนเป็น Float ให้ตรงกับ cost ใน Log

        if (targetUsername.isEmpty() || adminEmail == null) {
            Toast.makeText(requireContext(), "Error: Missing user data.", Toast.LENGTH_SHORT).show()
            return
        }

        loadingSpinner.show(parentFragmentManager, "loading_spinner")

        db.collection("Sellers").document(adminEmail).get()
            .addOnSuccessListener { adminDocument ->
                loadingSpinner.dismiss()
                if (adminDocument.exists()) {
                    val currentCredit = adminDocument.getDouble("Credit") ?: 0.0

                    if (currentCredit < creditCost) {
                        showCreditAlertDialog()
                    } else {
                        db.collection("Users").document(targetUsername).get()
                            .addOnSuccessListener { userDocument ->
                                if (userDocument.exists()) {
                                    val createdByEmail = userDocument.getString("CreatedBy")
                                    val currentDeviceId = userDocument.getString("DeviceId")

                                    if (currentDeviceId == "none") {
                                        Toast.makeText(
                                            requireContext(),
                                            "User's device is already reset.",
                                            Toast.LENGTH_SHORT
                                        ).show()
                                    } else if (createdByEmail == adminEmail) {
                                        resetDeviceAndDeductCredit(
                                            targetUsername,
                                            adminEmail,
                                            currentCredit
                                        )
                                    } else {
                                        Toast.makeText(
                                            requireContext(),
                                            "You can only reset users you created.",
                                            Toast.LENGTH_SHORT
                                        ).show()
                                    }

                                } else {
                                    Toast.makeText(
                                        requireContext(),
                                        "User not found.",
                                        Toast.LENGTH_SHORT
                                    ).show()
                                }
                            }
                    }
                } else {
                    Toast.makeText(
                        requireContext(),
                        "Admin document not found.",
                        Toast.LENGTH_SHORT
                    ).show()
                }
            }
            .addOnFailureListener {
                loadingSpinner.dismiss()
                Toast.makeText(requireContext(), "Failed to access database.", Toast.LENGTH_SHORT)
                    .show()
            }
    }

    private fun resetDeviceAndDeductCredit(
        targetUsername: String,
        adminEmail: String,
        currentCredit: Double
    ) {
        val newCredit = currentCredit - 0.25
        val cost = 0.25f

        loadingSpinner.show(parentFragmentManager, "loading_spinner")

        db.runBatch { batch ->
            val adminRef = db.collection("Sellers").document(adminEmail)
            batch.update(adminRef, "Credit", newCredit)

            val userRef = db.collection("Users").document(targetUsername)
            batch.update(userRef, "DeviceId", "none")
        }.addOnSuccessListener {
            loadingSpinner.dismiss()
            loginManager.updateCredit(newCredit.toFloat())
            Toast.makeText(
                requireContext(),
                "User '$targetUsername' device has been reset. Your credit has been deducted.",
                Toast.LENGTH_SHORT
            ).show()

            // เพิ่ม Log สำหรับการ Reset อุปกรณ์
            val action = "Reset Device"
            val adminUsername = loginManager.getLoggedInUsername()
            val details = "$adminUsername reset device for $targetUsername"
            saveActivityLog(action, details, adminEmail, cost)
        }.addOnFailureListener {
            loadingSpinner.dismiss()
            Toast.makeText(
                requireContext(),
                "Failed to reset device and deduct credit.",
                Toast.LENGTH_LONG
            ).show()
        }
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
                Log.d("SettingFragment", "Activity Log added successfully.")
            }
            .addOnFailureListener { e ->
                Log.w("SettingFragment", "Error adding activity log", e)
            }
    }

    private fun showCreditAlertDialog() {
        AlertDialog.Builder(requireContext())
            .setTitle("Not Enough Credit")
            .setMessage("You do not have enough credit to reset this user's device.")
            .setPositiveButton("OK") { _, _ -> }
            .show()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
