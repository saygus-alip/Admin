package com.alip.admin

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.RecyclerView
import com.alip.admin.Data.ActivityLog
import java.text.SimpleDateFormat
import java.util.*

class ActivityLogAdapter(private val logs: List<ActivityLog>) :
    RecyclerView.Adapter<ActivityLogAdapter.LogViewHolder>() {

    class LogViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val textViewAction: TextView = view.findViewById(R.id.textViewAction)
        val textViewCost: TextView = view.findViewById(R.id.textViewCost)
        val textViewActivityDescription: TextView = view.findViewById(R.id.textViewActivityDescription)
        val textViewTimestamp: TextView = view.findViewById(R.id.textViewTimestamp)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): LogViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_activity_log, parent, false)
        return LogViewHolder(view)
    }

    override fun onBindViewHolder(holder: LogViewHolder, position: Int) {
        val log = logs[position]

        // แสดง Action
        holder.textViewAction.text = log.action

        // แสดง Cost
        if (log.cost > 0) {
            holder.textViewCost.text = "-${log.cost}"
            holder.textViewCost.setTextColor(ContextCompat.getColor(holder.itemView.context, android.R.color.holo_red_light))
        } else {
            holder.textViewCost.text = "0.0"
            holder.textViewCost.setTextColor(ContextCompat.getColor(holder.itemView.context, android.R.color.white))
        }

        // แสดงรายละเอียด
        holder.textViewActivityDescription.text = log.details

        // จัดรูปแบบวันที่และเวลา
        val dateFormat = SimpleDateFormat("dd/MM/yyyy HH:mm:ss", Locale.getDefault())
        val dateString = log.timestamp?.toDate()?.let { dateFormat.format(it) } ?: "N/A"
        holder.textViewTimestamp.text = dateString
    }

    override fun getItemCount() = logs.size
}
