package com.alip.admin.Fragment

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
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
            Toast.makeText(requireContext(), "Please enter a username to search.", Toast.LENGTH_SHORT).show()
            return
        }

        if (adminEmail == null) {
            Toast.makeText(requireContext(), "Admin user not found. Please log in again.", Toast.LENGTH_LONG).show()
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
                        // User นี้ถูกสร้างโดย Admin คนนี้
                        foundUsername = targetUsername
                        binding.usernameTextView.text = "Username: $targetUsername"
                        binding.expiredDateTextView.text = "Expired: ${userDocument.getString("Expired") ?: "N/A"}"
                        binding.userResultCardView.visibility = View.VISIBLE
                        Toast.makeText(requireContext(), "User '$targetUsername' found.", Toast.LENGTH_SHORT).show()
                    } else {
                        Toast.makeText(requireContext(), "You can only manage users you created.", Toast.LENGTH_SHORT).show()
                    }
                } else {
                    Toast.makeText(requireContext(), "User '$targetUsername' not found.", Toast.LENGTH_SHORT).show()
                }
            }
            .addOnFailureListener { e ->
                loadingSpinner.dismiss()
                Toast.makeText(requireContext(), "Failed to access database: ${e.message}", Toast.LENGTH_LONG).show()
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

        builder.setView(dialogView)
            .setTitle("Change Password for $targetUsername")
            .setPositiveButton("Change") { _, _ ->
                handleChangePassword(targetUsername, oldAdminPassword.text.toString(), newPassword.text.toString(), confirmPassword.text.toString())
            }
            .setNegativeButton("Cancel") { dialog, _ -> dialog.cancel() }
            .show()
    }

    private fun handleChangePassword(targetUsername: String, oldAdminPassword: String, newPassword: String, confirmPassword: String) {
        val adminEmail = loginManager.getLoggedInEmail()
        val creditCost = 0.25f

        if (newPassword != confirmPassword || newPassword.length < 6) {
            Toast.makeText(requireContext(), "New passwords do not match or are too short.", Toast.LENGTH_SHORT).show()
            return
        }

        if (adminEmail == null || loginManager.getLoggedInCredit() < creditCost) {
            Toast.makeText(requireContext(), "Not enough credit or admin not found.", Toast.LENGTH_SHORT).show()
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
                        Toast.makeText(requireContext(), "Password for '$targetUsername' changed successfully!", Toast.LENGTH_SHORT).show()

                        // เพิ่ม Log สำหรับการเปลี่ยนรหัสผ่าน
                        val action = "Change Password"
                        val adminUsername = loginManager.getLoggedInUsername()
                        val details = "$adminUsername changed password for $targetUsername from the search screen"
                        saveActivityLog(action, details, adminEmail, creditCost)

                    }.addOnFailureListener { e ->
                        loadingSpinner.dismiss()
                        Toast.makeText(requireContext(), "Error updating password: ${e.message}", Toast.LENGTH_LONG).show()
                    }
                } else {
                    loadingSpinner.dismiss()
                    Toast.makeText(requireContext(), "Incorrect admin password.", Toast.LENGTH_SHORT).show()
                }
            }
    }

    private fun showRenewUserDialog() {
        val targetUsername = foundUsername ?: return
        val builder = AlertDialog.Builder(requireContext())
        val inflater = requireActivity().layoutInflater
        val dialogView = inflater.inflate(R.layout.dialog_renew_user, null)

        val renewDays = dialogView.findViewById<com.google.android.material.textfield.TextInputEditText>(R.id.renewDaysEditText)

        builder.setView(dialogView)
            .setTitle("Renew User: $targetUsername")
            .setPositiveButton("Renew") { _, _ ->
                handleRenewUser(targetUsername, renewDays.text.toString())
            }
            .setNegativeButton("Cancel") { dialog, _ -> dialog.cancel() }
            .show()
    }

    private fun handleRenewUser(targetUsername: String, expiredDaysStr: String) {
        val adminEmail = loginManager.getLoggedInEmail()
        val expiredDays = expiredDaysStr.toIntOrNull()

        if (expiredDays == null || expiredDays < 10 || expiredDays > 100 || expiredDays % 10 != 0) {
            Toast.makeText(requireContext(), "Renewal days must be 10, 20, ..., 100.", Toast.LENGTH_SHORT).show()
            return
        }

        val creditCost = expiredDays / 10f
        if (adminEmail == null || loginManager.getLoggedInCredit() < creditCost) {
            Toast.makeText(requireContext(), "Not enough credit or admin not found.", Toast.LENGTH_SHORT).show()
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
                    Toast.makeText(requireContext(), "User '$targetUsername' renewed for $expiredDays days!", Toast.LENGTH_SHORT).show()

                    // เพิ่ม Log สำหรับการต่ออายุผู้ใช้
                    val action = "Renew User"
                    val adminUsername = loginManager.getLoggedInUsername()
                    val details = "$adminUsername renewed $targetUsername for $expiredDays days from the search screen"
                    saveActivityLog(action, details, adminEmail, creditCost)

                }.addOnFailureListener { e ->
                    loadingSpinner.dismiss()
                    Toast.makeText(requireContext(), "Error renewing user: ${e.message}", Toast.LENGTH_LONG).show()
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
            Toast.makeText(requireContext(), "Admin user not found. Please log in again.", Toast.LENGTH_LONG).show()
            return
        }

        loadingSpinner.show(parentFragmentManager, "loading_spinner")

        db.collection("Users")
            .whereEqualTo("CreatedBy", adminEmail)
            .get()
            .addOnSuccessListener { querySnapshot ->
                loadingSpinner.dismiss()
                if (querySnapshot.isEmpty) {
                    Toast.makeText(requireContext(), "No users found to export.", Toast.LENGTH_SHORT).show()
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
                    Toast.makeText(requireContext(), "Data exported to file successfully!", Toast.LENGTH_SHORT).show()

                    // เพิ่ม Log สำหรับการส่งออกข้อมูลผู้ใช้
                    val action = "Export Users"
                    val adminUsername = loginManager.getLoggedInUsername()
                    val details = "$adminUsername exported $exportedUserCount users from the search screen"
                    val cost = 0.0f
                    saveActivityLog(action, details, adminEmail, cost)

                } catch (e: IOException) {
                    Toast.makeText(requireContext(), "Error saving file: ${e.message}", Toast.LENGTH_LONG).show()
                }
            }
            .addOnFailureListener { e ->
                loadingSpinner.dismiss()
                Toast.makeText(requireContext(), "Failed to access database: ${e.message}", Toast.LENGTH_LONG).show()
            }
    }

    private fun showExportedFileOptions() {
        val builder = AlertDialog.Builder(requireContext())
        builder.setTitle("Exported File Actions")
        builder.setMessage("Data has been exported to 'exported_users.csv'.")
        builder.setPositiveButton("Open") { _, _ -> openExportedFile() }
        builder.setNegativeButton("Send") { _, _ -> sendExportedFile() }
        builder.setNeutralButton("OK") { dialog, _ -> dialog.dismiss() }
        builder.show()
    }

    private fun openExportedFile() {
        try {
            val fileUri: Uri = FileProvider.getUriForFile(requireContext(), "${requireContext().packageName}.fileprovider", exportedFile)
            val intent = Intent(Intent.ACTION_VIEW)
            intent.setDataAndType(fileUri, "text/csv")
            intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            startActivity(intent)
        } catch (e: Exception) {
            Toast.makeText(requireContext(), "No app found to open the file. Please ensure you have a spreadsheet app installed.", Toast.LENGTH_SHORT).show()
        }
    }

    private fun sendExportedFile() {
        val fileUri: Uri = FileProvider.getUriForFile(requireContext(), "${requireContext().packageName}.fileprovider", exportedFile)
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "text/csv"
            putExtra(Intent.EXTRA_STREAM, fileUri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        try {
            startActivity(Intent.createChooser(intent, "Send file using"))
        } catch (e: Exception) {
            Toast.makeText(requireContext(), "No app found to send the file.", Toast.LENGTH_SHORT).show()
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
                Log.d("SearchUserFragment", "Activity Log added successfully.")
            }
            .addOnFailureListener { e ->
                Log.w("SearchUserFragment", "Error adding activity log", e)
            }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
