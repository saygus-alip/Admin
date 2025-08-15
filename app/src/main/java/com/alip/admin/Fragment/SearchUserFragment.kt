package com.alip.admin.Fragment

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.appcompat.app.AlertDialog
import androidx.core.content.FileProvider
import androidx.fragment.app.Fragment
import com.alip.admin.LoginManager
import com.alip.admin.R
import com.alip.admin.databinding.SearchUserBinding
import com.google.firebase.firestore.ktx.firestore
import com.google.firebase.ktx.Firebase
import com.alip.admin.LoadingSpinnerFragment
import org.mindrot.jbcrypt.BCrypt
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import com.alip.admin.Data.ActivityLog
import com.google.firebase.Timestamp
import android.util.Log
import android.graphics.Typeface
import android.widget.TextView

class SearchUserFragment : Fragment() {

    private var _binding: SearchUserBinding? = null
    private val binding get() = _binding!!
    private lateinit var loginManager: LoginManager
    private val db = Firebase.firestore
    private val loadingSpinner = LoadingSpinnerFragment()
    private val exportFileName = "exported_users.csv"
    private lateinit var exportedFile: File
    private var foundUsername: String? = null

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        _binding = SearchUserBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        loginManager = LoginManager(requireContext())
        exportedFile = File(requireContext().cacheDir, exportFileName)

        binding.searchButton.setOnClickListener {
            searchUser()
        }
        binding.changePasswordButton.setOnClickListener {
            showChangePasswordDialog()
        }
        binding.renewButton.setOnClickListener {
            showRenewUserDialog()
        }
        binding.exportButton.setOnClickListener {
            exportUserDataToFile()
        }
    }

    private fun searchUser() {
        val targetUsername = binding.usernameEditText.text.toString().trim()
        val adminEmail = loginManager.getLoggedInEmail()

        if (targetUsername.isEmpty()) {
            val dialog = AlertDialog.Builder(requireContext())
                .setTitle("Input Required")
                .setIcon(R.drawable.ic_eazy)
                .setMessage("Please enter a username to search!")
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
                .setMessage("Admin user not found! Please log in again!")
                .setPositiveButton("OK") { d, _ -> d.dismiss() }
                .setCancelable(false)
                .show()
            applyCustomFontToDialog(dialog)
            return
        }

        binding.userResultCardView.visibility = View.GONE
        loadingSpinner.show(parentFragmentManager, "loading_spinner")

        db.collection("Users").document(targetUsername).get()
            .addOnSuccessListener { userDocument ->
                loadingSpinner.dismiss()
                if (userDocument.exists()) {
                    val createdByEmail = userDocument.getString("CreatedBy")

                    if (createdByEmail == adminEmail) {
                        // This user was created by this admin
                        foundUsername = targetUsername
                        binding.usernameTextView.text = "Username: $targetUsername"
                        binding.expiredDateTextView.text = "Expired: ${userDocument.getString("Expired") ?: "N/A"}"
                        binding.userResultCardView.visibility = View.VISIBLE
                        val dialog = AlertDialog.Builder(requireContext())
                            .setTitle("Success")
                            .setIcon(R.drawable.ic_eazy)
                            .setMessage("User $targetUsername found!")
                            .setPositiveButton("OK") { d, _ -> d.dismiss() }
                            .setCancelable(false)
                            .show()
                        applyCustomFontToDialog(dialog)
                    } else {
                        val dialog = AlertDialog.Builder(requireContext())
                            .setTitle("Unauthorized Action")
                            .setIcon(R.drawable.ic_eazy)
                            .setMessage("You can only manage users you created!")
                            .setPositiveButton("OK") { d, _ -> d.dismiss() }
                            .setCancelable(false)
                            .show()
                        applyCustomFontToDialog(dialog)
                    }
                } else {
                    val dialog = AlertDialog.Builder(requireContext())
                        .setTitle("User Not Found")
                        .setIcon(R.drawable.ic_eazy)
                        .setMessage("User $targetUsername not found!")
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

    private fun showChangePasswordDialog() {
        val targetUsername = foundUsername ?: return
        val builder = AlertDialog.Builder(requireContext())
        val inflater = requireActivity().layoutInflater
        val dialogView = inflater.inflate(R.layout.dialog_change_password, null)

        val oldAdminPassword = dialogView.findViewById<com.google.android.material.textfield.TextInputEditText>(R.id.oldAdminPasswordEditText)
        val newPassword = dialogView.findViewById<com.google.android.material.textfield.TextInputEditText>(R.id.newPasswordEditText)
        val confirmPassword = dialogView.findViewById<com.google.android.material.textfield.TextInputEditText>(R.id.confirmPasswordEditText)

        val dialog = builder.setView(dialogView)
            .setTitle("Change Password for $targetUsername")
            .setIcon(R.drawable.ic_eazy)
            .setPositiveButton("Change") { _, _ ->
                handleChangePassword(targetUsername, oldAdminPassword.text.toString(), newPassword.text.toString(), confirmPassword.text.toString())
            }
            .setNegativeButton("Cancel") { d, _ -> d.cancel() }
            .setCancelable(false)
            .show()
        applyCustomFontToDialog(dialog)
    }

    private fun handleChangePassword(targetUsername: String, oldAdminPassword: String, newPassword: String, confirmPassword: String) {
        val adminEmail = loginManager.getLoggedInEmail()
        val creditCost = 0.25f

        if (newPassword != confirmPassword || newPassword.length < 6) {
            val dialog = AlertDialog.Builder(requireContext())
                .setTitle("Invalid Password")
                .setIcon(R.drawable.ic_eazy)
                .setMessage("New passwords do not match or are too short (must be at least 6 characters)!")
                .setPositiveButton("OK") { d, _ -> d.dismiss() }
                .setCancelable(false)
                .show()
            applyCustomFontToDialog(dialog)
            return
        }

        if (adminEmail == null || loginManager.getLoggedInCredit() < creditCost) {
            val dialog = AlertDialog.Builder(requireContext())
                .setTitle("Error")
                .setIcon(R.drawable.ic_eazy)
                .setMessage("Not enough credit or admin not found!")
                .setPositiveButton("OK") { d, _ -> d.dismiss() }
                .setCancelable(false)
                .show()
            applyCustomFontToDialog(dialog)
            return
        }

        loadingSpinner.show(parentFragmentManager, "loading_spinner")

        db.collection("Sellers").document(adminEmail).get()
            .addOnSuccessListener { adminDocument ->
                val hashedAdminPassword = adminDocument.getString("hashedPassword")
                if (hashedAdminPassword != null && BCrypt.checkpw(oldAdminPassword, hashedAdminPassword)) {
                    val newHashedPassword = BCrypt.hashpw(newPassword, BCrypt.gensalt())
                    val newCredit = loginManager.getLoggedInCredit() - creditCost

                    db.runBatch { batch ->
                        val userRef = db.collection("Users").document(targetUsername)
                        batch.update(userRef, "hashedPassword", newHashedPassword)
                        val adminRef = db.collection("Sellers").document(adminEmail)
                        batch.update(adminRef, "Credit", newCredit.toDouble())
                    }.addOnSuccessListener {
                        loadingSpinner.dismiss()
                        loginManager.updateCredit(newCredit)
                        val dialog = AlertDialog.Builder(requireContext())
                            .setTitle("Success")
                            .setIcon(R.drawable.ic_eazy)
                            .setMessage("Password for $targetUsername changed successfully!")
                            .setPositiveButton("OK") { d, _ -> d.dismiss() }
                            .setCancelable(false)
                            .show()
                        applyCustomFontToDialog(dialog)

                        // Add Log for password change
                        val action = "Change Password"
                        val adminUsername = loginManager.getLoggedInUsername()
                        val details = "$adminUsername changed password for $targetUsername from the search screen"
                        saveActivityLog(action, details, adminEmail, creditCost)

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
                        .setTitle("Authentication Failed")
                        .setIcon(R.drawable.ic_eazy)
                        .setMessage("Incorrect admin password!")
                        .setPositiveButton("OK") { d, _ -> d.dismiss() }
                        .setCancelable(false)
                        .show()
                    applyCustomFontToDialog(dialog)
                }
            }
    }

    private fun showRenewUserDialog() {
        val targetUsername = foundUsername ?: return
        val builder = AlertDialog.Builder(requireContext())
        val inflater = requireActivity().layoutInflater
        val dialogView = inflater.inflate(R.layout.dialog_renew_user, null)

        val renewDays = dialogView.findViewById<com.google.android.material.textfield.TextInputEditText>(R.id.renewDaysEditText)

        val dialog = builder.setView(dialogView)
            .setTitle("Renew User: $targetUsername")
            .setIcon(R.drawable.ic_eazy)
            .setPositiveButton("Renew") { _, _ ->
                handleRenewUser(targetUsername, renewDays.text.toString())
            }
            .setNegativeButton("Cancel") { d, _ -> d.cancel() }
            .setCancelable(false)
            .show()
        applyCustomFontToDialog(dialog)
    }

    private fun handleRenewUser(targetUsername: String, expiredDaysStr: String) {
        val adminEmail = loginManager.getLoggedInEmail()
        val expiredDays = expiredDaysStr.toIntOrNull()

        if (expiredDays == null || expiredDays < 10 || expiredDays > 100 || expiredDays % 10 != 0) {
            val dialog = AlertDialog.Builder(requireContext())
                .setTitle("Invalid Renewal Days")
                .setIcon(R.drawable.ic_eazy)
                .setMessage("Renewal days must be a multiple of 10, between 10 and 100!")
                .setPositiveButton("OK") { d, _ -> d.dismiss() }
                .setCancelable(false)
                .show()
            applyCustomFontToDialog(dialog)
            return
        }

        val creditCost = expiredDays / 10f
        if (adminEmail == null || loginManager.getLoggedInCredit() < creditCost) {
            val dialog = AlertDialog.Builder(requireContext())
                .setTitle("Error")
                .setIcon(R.drawable.ic_eazy)
                .setMessage("Not enough credit or admin not found!")
                .setPositiveButton("OK") { d, _ -> d.dismiss() }
                .setCancelable(false)
                .show()
            applyCustomFontToDialog(dialog)
            return
        }

        loadingSpinner.show(parentFragmentManager, "loading_spinner")

        db.collection("Users").document(targetUsername).get()
            .addOnSuccessListener { userDocument ->
                val currentExpiredDateStr = userDocument.getString("Expired")
                val newExpiredDate = calculateNewExpiredDate(currentExpiredDateStr, expiredDays)
                val newCredit = loginManager.getLoggedInCredit() - creditCost

                db.runBatch { batch ->
                    val userRef = db.collection("Users").document(targetUsername)
                    batch.update(userRef, "Expired", newExpiredDate)
                    val adminRef = db.collection("Sellers").document(adminEmail)
                    batch.update(adminRef, "Credit", newCredit.toDouble())
                }.addOnSuccessListener {
                    loadingSpinner.dismiss()
                    loginManager.updateCredit(newCredit)
                    binding.expiredDateTextView.text = "Expired: $newExpiredDate" // Update UI
                    val dialog = AlertDialog.Builder(requireContext())
                        .setTitle("Success")
                        .setIcon(R.drawable.ic_eazy)
                        .setMessage("User $targetUsername renewed for $expiredDays days!")
                        .setPositiveButton("OK") { d, _ -> d.dismiss() }
                        .setCancelable(false)
                        .show()
                    applyCustomFontToDialog(dialog)

                    // Add Log for user renewal
                    val action = "Renew User"
                    val adminUsername = loginManager.getLoggedInUsername()
                    val details = "$adminUsername renewed $targetUsername for $expiredDays days from the search screen"
                    saveActivityLog(action, details, adminEmail, creditCost)

                }.addOnFailureListener { e ->
                    loadingSpinner.dismiss()
                    val dialog = AlertDialog.Builder(requireContext())
                        .setTitle("Update Error")
                        .setIcon(R.drawable.ic_eazy)
                        .setMessage("Error renewing user: ${e.message}")
                        .setPositiveButton("OK") { d, _ -> d.dismiss() }
                        .setCancelable(false)
                        .show()
                    applyCustomFontToDialog(dialog)
                }
            }
    }

    private fun calculateNewExpiredDate(currentDateStr: String?, days: Int): String {
        val dateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
        val calendar = Calendar.getInstance()

        if (currentDateStr != null && currentDateStr.isNotBlank()) {
            val currentDate = dateFormat.parse(currentDateStr)
            if (currentDate != null) {
                if (currentDate.after(Date())) {
                    calendar.time = currentDate
                }
            }
        }

        calendar.add(Calendar.DAY_OF_YEAR, days)
        return dateFormat.format(calendar.time)
    }

    private fun exportUserDataToFile() {
        val adminEmail = loginManager.getLoggedInEmail()
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

        loadingSpinner.show(parentFragmentManager, "loading_spinner")

        db.collection("Users")
            .whereEqualTo("CreatedBy", adminEmail)
            .get()
            .addOnSuccessListener { querySnapshot ->
                loadingSpinner.dismiss()
                if (querySnapshot.isEmpty) {
                    val dialog = AlertDialog.Builder(requireContext())
                        .setTitle("No Users Found")
                        .setIcon(R.drawable.ic_eazy)
                        .setMessage("No users found to export!")
                        .setPositiveButton("OK") { d, _ -> d.dismiss() }
                        .setCancelable(false)
                        .show()
                    applyCustomFontToDialog(dialog)
                    return@addOnSuccessListener
                }

                val exportedUserCount = querySnapshot.size()

                val header = "Username,Expired,CreatedBy\n"
                val csvData = StringBuilder(header)
                querySnapshot.documents.forEach { document ->
                    val username = document.id
                    val expired = document.getString("Expired") ?: ""
                    val createdBy = document.getString("CreatedBy") ?: ""
                    csvData.append("$username,$expired,$createdBy\n")
                }

                try {
                    val outputStream = FileOutputStream(exportedFile)
                    outputStream.write(csvData.toString().toByteArray())
                    outputStream.close()
                    showExportedFileOptions()
                    val dialog = AlertDialog.Builder(requireContext())
                        .setTitle("Export Success")
                        .setIcon(R.drawable.ic_eazy)
                        .setMessage("Data exported to file successfully!")
                        .setPositiveButton("OK") { d, _ -> d.dismiss() }
                        .setCancelable(false)
                        .show()
                    applyCustomFontToDialog(dialog)

                    // Add Log for user data export
                    val action = "Export Users"
                    val adminUsername = loginManager.getLoggedInUsername()
                    val details = "$adminUsername exported $exportedUserCount users from the search screen"
                    val cost = 0.0f
                    saveActivityLog(action, details, adminEmail, cost)

                } catch (e: IOException) {
                    val dialog = AlertDialog.Builder(requireContext())
                        .setTitle("File Error")
                        .setIcon(R.drawable.ic_eazy)
                        .setMessage("Error saving file: ${e.message}")
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

    private fun showExportedFileOptions() {
        val builder = AlertDialog.Builder(requireContext())
        val dialog = builder.setIcon(R.drawable.ic_eazy)
            .setTitle("Exported File Actions")
            .setIcon(R.drawable.ic_eazy)
            .setMessage("Data has been exported to exported_users.csv!")
            .setPositiveButton("Open") { _, _ -> openExportedFile() }
            .setNegativeButton("Send") { _, _ -> sendExportedFile() }
            .setNeutralButton("OK") { d, _ -> d.dismiss() }
            .setCancelable(false)
            .show()
        applyCustomFontToDialog(dialog)
    }

    private fun openExportedFile() {
        try {
            val fileUri: Uri = FileProvider.getUriForFile(requireContext(), "${requireContext().packageName}.fileprovider", exportedFile)
            val intent = Intent(Intent.ACTION_VIEW)
            intent.setDataAndType(fileUri, "text/csv")
            intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            startActivity(intent)
        } catch (e: Exception) {
            val dialog = AlertDialog.Builder(requireContext())
                .setTitle("Error")
                .setIcon(R.drawable.ic_eazy)
                .setMessage("No app found to open the file. Please ensure you have a spreadsheet app installed!")
                .setPositiveButton("OK") { d, _ -> d.dismiss() }
                .setCancelable(false)
                .show()
            applyCustomFontToDialog(dialog)
        }
    }

    private fun sendExportedFile() {
        try {
            val fileUri: Uri = FileProvider.getUriForFile(requireContext(), "${requireContext().packageName}.fileprovider", exportedFile)
            val intent = Intent(Intent.ACTION_SEND).apply {
                type = "text/csv"
                putExtra(Intent.EXTRA_STREAM, fileUri)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            startActivity(Intent.createChooser(intent, "Send file using"))
        } catch (e: Exception) {
            val dialog = AlertDialog.Builder(requireContext())
                .setTitle("Error")
                .setIcon(R.drawable.ic_eazy)
                .setMessage("No app found to send the file!")
                .setPositiveButton("OK") { d, _ -> d.dismiss() }
                .setCancelable(false)
                .show()
            applyCustomFontToDialog(dialog)
        }
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
                Log.d("SearchUserFragment", "Activity Log added successfully!")
            }
            .addOnFailureListener { e ->
                Log.w("SearchUserFragment", "Error adding activity log!", e)
            }
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
                Log.e("SearchUserFragment", "Custom font not found or failed to apply: ${e.message} and ${e2.message}")
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
