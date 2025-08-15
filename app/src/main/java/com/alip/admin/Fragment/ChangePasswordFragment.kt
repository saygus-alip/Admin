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
import android.graphics.Typeface
import android.widget.TextView

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

        if (targetUsername.isEmpty() || newPassword.isEmpty() || confirmPassword.isEmpty()) {
            val dialog = AlertDialog.Builder(requireContext())
                .setTitle("Input Required")
                .setIcon(R.drawable.ic_eazy)
                .setMessage("Please enter username and password!")
                .setPositiveButton("OK") { d, _ -> d.dismiss() }
                .setCancelable(false)
                .show()
            applyCustomFontToDialog(dialog)
            return
        }

        if (newPassword != confirmPassword) {
            val dialog = AlertDialog.Builder(requireContext())
                .setTitle("Passwords Mismatch")
                .setIcon(R.drawable.ic_eazy)
                .setMessage("New passwords do not match!")
                .setPositiveButton("OK") { d, _ -> d.dismiss() }
                .setCancelable(false)
                .show()
            applyCustomFontToDialog(dialog)
            return
        }

        if (newPassword.length < 6) {
            val dialog = AlertDialog.Builder(requireContext())
                .setTitle("Password Too Short")
                .setIcon(R.drawable.ic_eazy)
                .setMessage("New password must be at least 6 characters!")
                .setPositiveButton("OK") { d, _ -> d.dismiss() }
                .setCancelable(false)
                .show()
            applyCustomFontToDialog(dialog)
            return
        }

        if (adminEmail == null) {
            val dialog = AlertDialog.Builder(requireContext())
                .setTitle("Authentication Error")
                .setIcon(R.drawable.ic_eazy)
                .setMessage("Admin user not found. Please log in again!")
                .setPositiveButton("OK") { d, _ -> d.dismiss() }
                .setCancelable(false)
                .show()
            applyCustomFontToDialog(dialog)
            return
        }

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

                                        val userRef = db.collection("Users").document(targetUsername)
                                        db.runBatch { batch ->
                                            batch.update(userRef, "hashedPassword", newHashedPassword)
                                        }.addOnSuccessListener {
                                            loadingSpinner.dismiss()
                                            val dialog = AlertDialog.Builder(requireContext())
                                                .setTitle("Success")
                                                .setIcon(R.drawable.ic_eazy)
                                                .setMessage("Password for $targetUsername changed successfully!")
                                                .setPositiveButton("OK") { d, _ -> d.dismiss() }
                                                .setCancelable(false)
                                                .show()
                                            applyCustomFontToDialog(dialog)

                                            binding.oldPasswordEditText.text?.clear()

                                            val adminUsername = loginManager.getLoggedInUsername()
                                            val action = "Change Password"
                                            val details = "$adminUsername changed password for $targetUsername"
                                            val cost = 0.0f
                                            saveActivityLog(action, details, adminEmail, cost)

                                        }.addOnFailureListener { e ->
                                            loadingSpinner.dismiss()
                                            val dialog = AlertDialog.Builder(requireContext())
                                                .setTitle("Update Error")
                                                .setIcon(R.drawable.ic_eazy)
                                                .setMessage("Error updating password: ${e.message}")
                                                .setPositiveButton("OK") { d, _ -> d.dismiss() }
                                                .setCancelable(false)
                                                .show()
                                            applyCustomFontToDialog(dialog)
                                        }
                                    } else {
                                        loadingSpinner.dismiss()
                                        val dialog = AlertDialog.Builder(requireContext())
                                            .setTitle("Unauthorized Action")
                                            .setIcon(R.drawable.ic_eazy)
                                            .setMessage("You can only change passwords for users you created!")
                                            .setPositiveButton("OK") { d, _ -> d.dismiss() }
                                            .setCancelable(false)
                                            .show()
                                        applyCustomFontToDialog(dialog)
                                    }
                                } else {
                                    loadingSpinner.dismiss()
                                    val dialog = AlertDialog.Builder(requireContext())
                                        .setTitle("User Not Found")
                                        .setIcon(R.drawable.ic_eazy)
                                        .setMessage("User not found!")
                                        .setPositiveButton("OK") { d, _ -> d.dismiss() }
                                        .setCancelable(false)
                                        .show()
                                    applyCustomFontToDialog(dialog)
                                }
                            }
                    } else {
                        loadingSpinner.dismiss()
                        val dialog = AlertDialog.Builder(requireContext())
                            .setTitle("Authentication Failed")
                            .setIcon(R.drawable.ic_eazy)
                            .setMessage("Incorrect password for admin!")
                            .setPositiveButton("OK") { d, _ -> d.dismiss() }
                            .setCancelable(false)
                            .show()
                        applyCustomFontToDialog(dialog)
                    }
                } else {
                    loadingSpinner.dismiss()
                    val dialog = AlertDialog.Builder(requireContext())
                        .setTitle("Authentication Error")
                        .setIcon(R.drawable.ic_eazy)
                        .setMessage("Admin document not found!")
                        .setPositiveButton("OK") { d, _ -> d.dismiss() }
                        .setCancelable(false)
                        .show()
                    applyCustomFontToDialog(dialog)
                }
            }
            .addOnFailureListener { e ->
                loadingSpinner.dismiss()
                val dialog = AlertDialog.Builder(requireContext())
                    .setTitle("Database Error")
                    .setIcon(R.drawable.ic_eazy)
                    .setMessage("Failed to access database: ${e.message}")
                    .setPositiveButton("OK") { d, _ -> d.dismiss() }
                    .setCancelable(false)
                    .show()
                applyCustomFontToDialog(dialog)
            }
    }

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

    private fun applyCustomFontToDialog(dialog: AlertDialog) {
        try {
            // Try to use the font from the assets folder.
            val typeface = Typeface.createFromAsset(requireContext().assets, "regular.ttf")
            dialog.findViewById<TextView>(android.R.id.message)?.typeface = typeface
            // Try to use the font for the title as well.
            dialog.findViewById<TextView>(androidx.appcompat.R.id.alertTitle)?.typeface = typeface
        } catch (e: Exception) {
            // If not found in assets, try from the res/font folder.
            try {
                val typeface = resources.getFont(R.font.regular)
                dialog.findViewById<TextView>(android.R.id.message)?.typeface = typeface
                dialog.findViewById<TextView>(androidx.appcompat.R.id.alertTitle)?.typeface = typeface
            } catch (e2: Exception) {
                // Log a specific error if the font is not found in both locations.
                Log.e("ChangePasswordFragment", "Custom font not found or failed to apply: ${e.message} and ${e2.message}")
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
