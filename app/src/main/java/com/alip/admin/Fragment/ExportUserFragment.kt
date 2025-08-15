package com.alip.admin.Fragment

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.core.content.FileProvider
import androidx.fragment.app.Fragment
import com.alip.admin.LoginManager
import com.alip.admin.R
import com.alip.admin.databinding.ExportUserBinding
import com.google.firebase.firestore.ktx.firestore
import com.google.firebase.ktx.Firebase
import com.alip.admin.LoadingSpinnerFragment
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import com.alip.admin.Data.ActivityLog
import com.google.firebase.Timestamp
import java.util.Date
import android.util.Log
import android.graphics.Typeface
import android.widget.TextView
import androidx.appcompat.app.AlertDialog

class ExportUserFragment : Fragment() {

    private var _binding: ExportUserBinding? = null
    private val binding get() = _binding!!
    private lateinit var loginManager: LoginManager
    private val db = Firebase.firestore
    private val loadingSpinner = LoadingSpinnerFragment()
    private val fileName = "exported_users.csv"
    private lateinit var exportedFile: File

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        _binding = ExportUserBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        loginManager = LoginManager(requireContext())
        exportedFile = File(requireContext().cacheDir, fileName)

        binding.export.setOnClickListener {
            exportUserDataToFile()
        }

        binding.open.setOnClickListener {
            openExportedFile()
        }

        binding.send.setOnClickListener {
            sendExportedFile()
        }
    }

    private fun exportUserDataToFile() {
        val adminEmail = loginManager.getLoggedInEmail()

        if (adminEmail == null) {
            val dialog = AlertDialog.Builder(requireContext())
                .setTitle("An Error Occurred")
                .setIcon(R.drawable.ic_eazy)
                .setMessage("Admin user not found! Please log in again!")
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
                        .setTitle("No Data Found")
                        .setIcon(R.drawable.ic_eazy)
                        .setMessage("No users created for export found!")
                        .setPositiveButton("OK") { d, _ -> d.dismiss() }
                        .setCancelable(false)
                        .show()
                    applyCustomFontToDialog(dialog)
                    return@addOnSuccessListener
                }

                val exportedUserCount = querySnapshot.size()

                // Create header for the CSV file
                val header = "Username,Expired,CreatedBy\n"
                val csvData = StringBuilder(header)

                // Retrieve each user's data to create a row in the CSV file
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
                    Toast.makeText(requireContext(), "Data exported to file successfully!", Toast.LENGTH_SHORT).show()

                    // Add log for exporting user data
                    val adminUsername = loginManager.getLoggedInUsername()
                    val action = "Export Users"
                    val details = "$adminUsername exported $exportedUserCount users"
                    val cost = 0.0f
                    saveActivityLog(action, details, adminEmail, cost)

                } catch (e: IOException) {
                    val dialog = AlertDialog.Builder(requireContext())
                        .setTitle("Save Error")
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
                    .setTitle("Data Access Error")
                    .setIcon(R.drawable.ic_eazy)
                    .setMessage("Failed to access database: ${e.message}")
                    .setPositiveButton("OK") { d, _ -> d.dismiss() }
                    .setCancelable(false)
                    .show()
                applyCustomFontToDialog(dialog)
            }
    }

    private fun openExportedFile() {
        if (!exportedFile.exists() || exportedFile.length() == 0L) {
            val dialog = AlertDialog.Builder(requireContext())
                .setTitle("File Not Ready")
                .setIcon(R.drawable.ic_eazy)
                .setMessage("Please export data first!")
                .setPositiveButton("OK") { d, _ -> d.dismiss() }
                .setCancelable(false)
                .show()
            applyCustomFontToDialog(dialog)
            return
        }

        try {
            val fileUri: Uri = FileProvider.getUriForFile(requireContext(), "${requireContext().packageName}.fileprovider", exportedFile)
            val intent = Intent(Intent.ACTION_VIEW)
            intent.setDataAndType(fileUri, "text/csv") // Change to text/csv
            intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            startActivity(intent)
        } catch (e: Exception) {
            val dialog = AlertDialog.Builder(requireContext())
                .setTitle("No App Found")
                .setIcon(R.drawable.ic_eazy)
                .setMessage("No app found to open the file!")
                .setPositiveButton("OK") { d, _ -> d.dismiss() }
                .setCancelable(false)
                .show()
            applyCustomFontToDialog(dialog)
        }
    }

    private fun sendExportedFile() {
        if (!exportedFile.exists() || exportedFile.length() == 0L) {
            val dialog = AlertDialog.Builder(requireContext())
                .setTitle("File Not Ready")
                .setIcon(R.drawable.ic_eazy)
                .setMessage("Please export data first!")
                .setPositiveButton("OK") { d, _ -> d.dismiss() }
                .setCancelable(false)
                .show()
            applyCustomFontToDialog(dialog)
            return
        }

        val fileUri: Uri = FileProvider.getUriForFile(requireContext(), "${requireContext().packageName}.fileprovider", exportedFile)
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "text/csv" // Change to text/csv
            putExtra(Intent.EXTRA_STREAM, fileUri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }

        try {
            startActivity(Intent.createChooser(intent, "Send file using!"))
        } catch (e: Exception) {
            val dialog = AlertDialog.Builder(requireContext())
                .setTitle("No App Found")
                .setIcon(R.drawable.ic_eazy)
                .setMessage("No app found to send the file!")
                .setPositiveButton("OK") { d, _ -> d.dismiss() }
                .setCancelable(false)
                .show()
            applyCustomFontToDialog(dialog)
        }
    }

    // Function to save Log to Firestore
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
                Log.d("ExportUserFragment", "Activity Log added successfully!")
            }
            .addOnFailureListener { e ->
                Log.w("ExportUserFragment", "Error adding activity log!", e)
            }
    }

    // Helper function to change the dialog's font
    private fun applyCustomFontToDialog(dialog: AlertDialog) {
        try {
            // Try to use the font from the assets folder
            val typeface = Typeface.createFromAsset(requireContext().assets, "regular.ttf")
            dialog.findViewById<TextView>(android.R.id.message)?.typeface = typeface
        } catch (e: Exception) {
            // If not found in assets, try from the res/font folder
            try {
                val typeface = resources.getFont(R.font.regular)
                dialog.findViewById<TextView>(android.R.id.message)?.typeface = typeface
            } catch (e2: Exception) {
                Log.e("ExportUserFragment", "Custom font not found or failed to apply: ${e.message} and ${e2.message}")
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
