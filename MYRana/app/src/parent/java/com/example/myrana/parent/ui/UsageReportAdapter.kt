package com.example.myrana.parent.ui

import android.content.pm.PackageManager
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.ProgressBar
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.example.myrana.R
import com.example.myrana.data.remote.dto.UsageAppItem

/**
 * قائمة RecyclerView لتقرير استخدام التطبيقات (من `/weekly-report`).
 * يعرض: أيقونة التطبيق، الاسم، الحزمة، المدة، شريط نسبي.
 */
class UsageReportAdapter(
    private val pm: PackageManager
) : RecyclerView.Adapter<UsageReportAdapter.VH>() {

    private val items = mutableListOf<UsageAppItem>()
    private var maxSeconds: Long = 1L

    fun submit(newItems: List<UsageAppItem>) {
        items.clear()
        items.addAll(newItems)
        maxSeconds = items.maxOfOrNull { it.totalSeconds }?.coerceAtLeast(1L) ?: 1L
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_usage_app, parent, false)
        return VH(view)
    }

    override fun onBindViewHolder(holder: VH, position: Int) {
        holder.bind(items[position], maxSeconds, pm)
    }

    override fun getItemCount(): Int = items.size

    class VH(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val icon: ImageView = itemView.findViewById(R.id.imgAppIcon)
        private val name: TextView = itemView.findViewById(R.id.textAppName)
        private val pkg: TextView = itemView.findViewById(R.id.textPackageName)
        private val duration: TextView = itemView.findViewById(R.id.textDuration)
        private val bar: ProgressBar = itemView.findViewById(R.id.progressUsage)

        fun bind(item: UsageAppItem, maxSec: Long, pm: PackageManager) {
            pkg.text = item.packageName
            name.text = resolveLabel(item.packageName, pm)
            duration.text = formatDuration(item.totalSeconds)
            bar.progress = ((item.totalSeconds * 100) / maxSec).toInt().coerceIn(0, 100)
            try {
                icon.setImageDrawable(pm.getApplicationIcon(item.packageName))
            } catch (_: PackageManager.NameNotFoundException) {
                icon.setImageResource(R.mipmap.ic_launcher)
            }
        }

        private fun resolveLabel(packageName: String, pm: PackageManager): String {
            return try {
                pm.getApplicationLabel(pm.getApplicationInfo(packageName, 0)).toString()
            } catch (_: Exception) {
                packageName.substringAfterLast('.')
            }
        }

        private fun formatDuration(totalSeconds: Long): String {
            val hours = totalSeconds / 3600
            val minutes = (totalSeconds % 3600) / 60
            return when {
                hours > 0 -> "${hours} س ${minutes} د"
                minutes > 0 -> "$minutes د"
                else -> "$totalSeconds ث"
            }
        }
    }
}
