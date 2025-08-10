package com.alip.admin.Fragment

import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.alip.admin.Data.ActivityLog
import com.alip.admin.ActivityLogAdapter
import com.alip.admin.LoginManager
import com.alip.admin.R
import com.google.firebase.firestore.Query
import com.google.firebase.firestore.ktx.firestore
import com.google.firebase.ktx.Firebase

class ActivityLogFragment : Fragment() {

    private lateinit var loginManager: LoginManager
    private val db = Firebase.firestore
    private lateinit var recyclerView: RecyclerView
    private lateinit var logAdapter: ActivityLogAdapter
    private val logsList = mutableListOf<ActivityLog>()

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        // Inflate the layout for this fragment
        return inflater.inflate(R.layout.fragment_activity_log, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        loginManager = LoginManager(requireContext())
        recyclerView = view.findViewById(R.id.recyclerViewActivityLogs)

        // ตั้งค่า RecyclerView
        recyclerView.layoutManager = LinearLayoutManager(context)
        logAdapter = ActivityLogAdapter(logsList)
        recyclerView.adapter = logAdapter

        fetchActivityLogs()
    }

    private fun fetchActivityLogs() {
        val adminEmail = loginManager.getLoggedInEmail() // แก้ไข: ดึงค่า email แทน username

        if (adminEmail != null) {
            db.collection("ActivityLog")
                .whereEqualTo("adminEmail", adminEmail) // แก้ไข: ใช้ adminEmail ในการค้นหา
                .orderBy("timestamp", Query.Direction.DESCENDING)
                .get()
                .addOnSuccessListener { documents ->
                    logsList.clear()
                    for (document in documents) {
                        try {
                            val log = document.toObject(ActivityLog::class.java)
                            logsList.add(log)
                        } catch (e: Exception) {
                            Log.e("ActivityLogFragment", "Error converting document: ${e.message}")
                            Toast.makeText(context, "Error converting log data.", Toast.LENGTH_SHORT).show()
                        }
                    }
                    logAdapter.notifyDataSetChanged()
                }
                .addOnFailureListener { exception ->
                    Log.w("ActivityLogFragment", "Error getting documents: ", exception)
                    Toast.makeText(context, "Error fetching logs.", Toast.LENGTH_SHORT).show()
                }
        } else {
            Toast.makeText(context, "No user logged in.", Toast.LENGTH_SHORT).show()
        }
    }
}
