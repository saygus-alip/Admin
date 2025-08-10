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

                // สร้าง header สำหรับไฟล์ CSV
                val header = "Username,Expired,CreatedBy\n"
                val csvData = StringBuilder(header)

                // ดึงข้อมูลแต่ละ user มาสร้างเป็นแถวในไฟล์ CSV
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

                    // เพิ่ม Log สำหรับการส่งออกข้อมูลผู้ใช้
                    val adminUsername = loginManager.getLoggedInUsername()
                    val action = "Export Users"
                    val details = "$adminUsername exported $exportedUserCount users"
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

    private fun openExportedFile() {
        if (!exportedFile.exists() || exportedFile.length() == 0L) {
            Toast.makeText(requireContext(), "Please export data first.", Toast.LENGTH_SHORT).show()
            return
        }

        try {
            val fileUri: Uri = FileProvider.getUriForFile(requireContext(), "${requireContext().packageName}.fileprovider", exportedFile)
            val intent = Intent(Intent.ACTION_VIEW)
            intent.setDataAndType(fileUri, "text/csv") // เปลี่ยนเป็น text/csv
            intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            startActivity(intent)
        } catch (e: Exception) {
            Toast.makeText(requireContext(), "No app found to open the file.", Toast.LENGTH_SHORT).show()
        }
    }

    private fun sendExportedFile() {
        if (!exportedFile.exists() || exportedFile.length() == 0L) {
            Toast.makeText(requireContext(), "Please export data first.", Toast.LENGTH_SHORT).show()
            return
        }

        val fileUri: Uri = FileProvider.getUriForFile(requireContext(), "${requireContext().packageName}.fileprovider", exportedFile)
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "text/csv" // เปลี่ยนเป็น text/csv
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
                Log.d("ExportUserFragment", "Activity Log added successfully.")
            }
            .addOnFailureListener { e ->
                Log.w("ExportUserFragment", "Error adding activity log", e)
            }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
