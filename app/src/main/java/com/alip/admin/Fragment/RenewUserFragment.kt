package com.alip.admin.Fragment

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
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
import com.alip.admin.R
import android.graphics.Typeface
import android.widget.TextView

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

        // 1. Check input data
        if (targetUsername.isEmpty() || expiredDaysStr.isEmpty()) {
            val dialog = AlertDialog.Builder(requireContext())
                .setTitle("Input Required")
                .setIcon(R.drawable.ic_eazy)
                .setMessage("Please enter a username and renewal days.")
                .setPositiveButton("OK") { d, _ -> d.dismiss() }
                .setCancelable(false)
                .show()
            applyCustomFontToDialog(dialog)
            return
        }

        val expiredDays = expiredDaysStr.toIntOrNull()
        if (expiredDays == null || expiredDays < 10 || expiredDays > 100 || expiredDays % 10 != 0) {
            val dialog = AlertDialog.Builder(requireContext())
                .setTitle("Invalid Renewal Days")
                .setIcon(R.drawable.ic_eazy)
                .setMessage("Renewal days must be a multiple of 10, between 10 and 100.")
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
                .setMessage("Admin user not found! Please log in again.")
                .setPositiveButton("OK") { d, _ -> d.dismiss() }
                .setCancelable(false)
                .show()
            applyCustomFontToDialog(dialog)
            return
        }

        // 2. Calculate credit cost and check
        val creditCost = expiredDays / 10f
        val currentCredit = loginManager.getLoggedInCredit()

        if (currentCredit < creditCost) {
            showCreditAlertDialog()
            return
        }

        // Show loading spinner
        loadingSpinner.show(parentFragmentManager, "loading_spinner")

        // 3. Check permissions and retrieve user data
        db.collection("Users").document(targetUsername).get()
            .addOnSuccessListener { userDocument ->
                if (userDocument.exists()) {
                    val createdByEmail = userDocument.getString("CreatedBy")
                    val currentExpiredDateStr = userDocument.getString("Expired")

                    if (createdByEmail == adminEmail) {
                        // 4. Calculate new expiration date
                        val newExpiredDate = calculateNewExpiredDate(currentExpiredDateStr, expiredDays)
                        val newCredit = currentCredit - creditCost

                        // 5. Use runBatch to update data simultaneously
                        db.runBatch { batch ->
                            val userRef = db.collection("Users").document(targetUsername)
                            batch.update(userRef, "Expired", newExpiredDate)

                            val adminRef = db.collection("Sellers").document(adminEmail)
                            batch.update(adminRef, "Credit", newCredit.toDouble())
                        }.addOnSuccessListener {
                            loadingSpinner.dismiss()
                            loginManager.updateCredit(newCredit)
                            val dialog = AlertDialog.Builder(requireContext())
                                .setTitle("Success")
                                .setIcon(R.drawable.ic_eazy)
                                .setMessage("User $targetUsername renewed for $expiredDays days! Your credit has been deducted.")
                                .setCancelable(false)
                                .setPositiveButton("OK") { d, _ -> d.dismiss() }
                                .show()
                            applyCustomFontToDialog(dialog)

                            binding.renewEditText.text?.clear()

                            // Update log for user renewal
                            val adminUsername = loginManager.getLoggedInUsername()
                            val action = "Renew User"
                            val details = "$adminUsername renewed $targetUsername for $expiredDays days"
                            saveActivityLog(action, details, adminEmail, creditCost)

                        }.addOnFailureListener { e ->
                            loadingSpinner.dismiss()
                            val dialog = AlertDialog.Builder(requireContext())
                                .setTitle("Update Error")
                                .setIcon(R.drawable.ic_eazy)
                                .setMessage("Error renewing user: ${e.message}")
                                .setCancelable(false)
                                .setPositiveButton("OK") { d, _ -> d.dismiss() }
                                .show()
                            applyCustomFontToDialog(dialog)
                        }
                    } else {
                        loadingSpinner.dismiss()
                        val dialog = AlertDialog.Builder(requireContext())
                            .setTitle("Unauthorized Action")
                            .setIcon(R.drawable.ic_eazy)
                            .setMessage("You can only renew users you created!")
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
                        .setMessage("User '$targetUsername' not found!")
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

    private fun calculateNewExpiredDate(currentDateStr: String?, days: Int): String {
        val dateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
        val calendar = Calendar.getInstance()

        if (currentDateStr != null && currentDateStr.isNotBlank()) {
            val currentDate = dateFormat.parse(currentDateStr)
            if (currentDate != null) {
                // If the old expiration date has not passed yet, renew from that date.
                if (currentDate.after(Date())) {
                    calendar.time = currentDate
                }
            }
        }

        calendar.add(Calendar.DAY_OF_YEAR, days)
        return dateFormat.format(calendar.time)
    }

    // Function to save the log to Firestore
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
                Log.d("RenewUserFragment", "Activity Log added successfully!")
            }
            .addOnFailureListener { e ->
                Log.w("RenewUserFragment", "Error adding activity log!", e)
            }
    }

    private fun showCreditAlertDialog() {
        val dialog = AlertDialog.Builder(requireContext())
            .setIcon(R.drawable.ic_eazy)
            .setTitle("Not Enough Credit")
            .setMessage("You do not have enough credit to renew this user")
            .setPositiveButton("OK") { d, _ -> d.dismiss() }
            .setCancelable(false)
            .show()
        applyCustomFontToDialog(dialog)
    }

    // Helper function to change the dialog's font
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
                Log.e("RenewUserFragment", "Custom font not found or failed to apply: ${e.message} and ${e2.message}")
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
