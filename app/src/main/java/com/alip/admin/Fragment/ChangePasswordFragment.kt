package com.alip.admin.Fragment

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.fragment.app.Fragment
import com.alip.admin.LoginManager
import com.alip.admin.databinding.ChangePasswordBinding
import com.google.firebase.firestore.ktx.firestore
import com.google.firebase.ktx.Firebase
import com.alip.admin.LoadingSpinnerFragment
import org.mindrot.jbcrypt.BCrypt
import androidx.appcompat.app.AlertDialog
import com.alip.admin.Data.ActivityLog
import com.google.firebase.Timestamp
import java.util.Date
import android.util.Log
import com.alip.admin.R


class ChangePasswordFragment : Fragment() {

    private var _binding: ChangePasswordBinding? = null
    private val binding get() = _binding!!
    private lateinit var loginManager: LoginManager
    private val db = Firebase.firestore

    private val loadingSpinner = LoadingSpinnerFragment()

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        _binding = ChangePasswordBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        loginManager = LoginManager(requireContext())

        binding.changePassword.setOnClickListener {
            handleChangePassword()
        }
    }

    private fun handleChangePassword() {
        val targetUsername = binding.usernameEditText.text.toString().trim()
        val oldAdminPassword = binding.oldPasswordEditText.text.toString().trim()
        val newPassword = binding.newPasswordEditText.text.toString().trim()
        val confirmPassword = binding.confirmPasswordEditText.text.toString().trim()
        val adminEmail = loginManager.getLoggedInEmail()

        // Removed creditCost since there is no credit deduction for this action.

        if (targetUsername.isEmpty() || newPassword.isEmpty() || confirmPassword.isEmpty()) {
            Toast.makeText(requireContext(), "Please enter username and password!", Toast.LENGTH_SHORT).show()
            return
        }

        if (newPassword != confirmPassword) {
            Toast.makeText(requireContext(), "New passwords do not match!", Toast.LENGTH_SHORT).show()
            return
        }

        if (newPassword.length < 6) {
            Toast.makeText(requireContext(), "New password must be at least 6 characters!", Toast.LENGTH_SHORT).show()
            return
        }

        if (adminEmail == null) {
            Toast.makeText(requireContext(), "Admin user not found. Please log in again!", Toast.LENGTH_LONG).show()
            return
        }

        // Removed credit check.

        loadingSpinner.show(parentFragmentManager, "loading_spinner")

        db.collection("Sellers").document(adminEmail).get()
            .addOnSuccessListener { adminDocument ->
                if (adminDocument.exists()) {
                    val hashedAdminPassword = adminDocument.getString("hashedPassword")

                    if (hashedAdminPassword != null && BCrypt.checkpw(oldAdminPassword, hashedAdminPassword)) {
                        db.collection("Users").document(targetUsername).get()
                            .addOnSuccessListener { userDocument ->
                                if (userDocument.exists()) {
                                    val createdByEmail = userDocument.getString("CreatedBy")

                                    if (createdByEmail == adminEmail) {
                                        val newHashedPassword = BCrypt.hashpw(newPassword, BCrypt.gensalt())
                                        // Removed newCredit calculation.

                                        // Update only the user's password using a batch write.
                                        val userRef = db.collection("Users").document(targetUsername)
                                        db.runBatch { batch ->
                                            batch.update(userRef, "hashedPassword", newHashedPassword)
                                        }.addOnSuccessListener {
                                            loadingSpinner.dismiss()
                                            // No credit update in LoginManager.
                                            Toast.makeText(requireContext(), "Password for '$targetUsername' changed successfully!", Toast.LENGTH_SHORT).show()
                                            binding.oldPasswordEditText.text?.clear()

                                            // Added Log for password change with custom details.
                                            val adminUsername = loginManager.getLoggedInUsername()
                                            val action = "Change Password"
                                            val details = "$adminUsername changed password for $targetUsername"
                                            val cost = 0.0f // Set cost to 0.0 since there's no deduction.
                                            saveActivityLog(action, details, adminEmail, cost)

                                        }.addOnFailureListener { e ->
                                            loadingSpinner.dismiss()
                                            Toast.makeText(requireContext(), "Error updating password: ${e.message}", Toast.LENGTH_LONG).show()
                                        }
                                    } else {
                                        loadingSpinner.dismiss()
                                        Toast.makeText(requireContext(), "You can only change passwords for users you created!", Toast.LENGTH_SHORT).show()
                                    }
                                } else {
                                    loadingSpinner.dismiss()
                                    Toast.makeText(requireContext(), "User not found!", Toast.LENGTH_SHORT).show()
                                }
                            }
                    } else {
                        loadingSpinner.dismiss()
                        Toast.makeText(requireContext(), "Incorrect password for admin!", Toast.LENGTH_SHORT).show()
                    }
                } else {
                    loadingSpinner.dismiss()
                    Toast.makeText(requireContext(), "Admin document not found!", Toast.LENGTH_SHORT).show()
                }
            }
            .addOnFailureListener { e ->
                loadingSpinner.dismiss()
                Toast.makeText(requireContext(), "Failed to access database: ${e.message}", Toast.LENGTH_LONG).show()
            }
    }

    // Function to save the log to Firestore.
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
                Log.d("ChangePasswordFragment", "Activity Log added successfully!")
            }
            .addOnFailureListener { e ->
                Log.w("ChangePasswordFragment", "Error adding activity log!", e)
            }
    }

    private fun showCreditAlertDialog() {
        AlertDialog.Builder(requireContext())
            .setIcon(R.drawable.ic_eazy)
            .setTitle("Not Enough Credit")
            .setMessage("You do not have enough credit to change your password")
            .setPositiveButton("OK") { _, _ -> }
            .show()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
