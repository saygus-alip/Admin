package com.alip.admin.Data

import com.google.firebase.Timestamp

data class ActivityLog(
    val action: String = "",
    val details: String = "",
    val status: String = "",
    val timestamp: Timestamp? = null,
    val adminEmail: String = "",
    val cost: Float = 0.0f
)
