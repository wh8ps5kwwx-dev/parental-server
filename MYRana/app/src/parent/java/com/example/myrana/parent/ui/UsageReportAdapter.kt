package com.example.myrana.parent.ui

import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.ProgressBar
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.example.myrana.R
import com.example.myrana.data.remote.dto.UsageAppItem
import com.example.myrana.util.AppIconHelper

/**
 * قائمة تطبيقات الطفل — أيقونة، اسم، مدة، وتجميد.
 */
class UsageReportAdapter(
    private val pm: PackageManager,
    private val onFreezeApp: ((UsageAppItem) -> Unit)? = null,
) : RecyclerView.Adapter<UsageReportAdapter.VH>() {

    private val items = mutableListOf<UsageAppItem>()
    private var maxSeconds: Long = 1L

    fun submit(newItems: List<UsageAppItem>) {
        items.clear()
        items.addAll(newItems)
        maxSeconds = items.maxOfOrNull {
            if (it.showDailyTotal) it.totalSeconds else it.avgSecondsPerDay
        }?.coerceAtLeast(1L) ?: 1L
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_usage_app, parent, false)
        return VH(view)
    }

    override fun onBindViewHolder(holder: VH, position: Int) {
        holder.bind(items[position], maxSeconds, pm, onFreezeApp)
    }

    override fun getItemCount(): Int = items.size

    class VH(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val icon: ImageView = itemView.findViewById(R.id.imgAppIcon)
        private val name: TextView = itemView.findViewById(R.id.textAppName)
        private val pkg: TextView = itemView.findViewById(R.id.textPackageName)
        private val duration: TextView = itemView.findViewById(R.id.textDuration)
        private val bar: ProgressBar = itemView.findViewById(R.id.progressUsage)
        private val btnFreeze: ImageButton = itemView.findViewById(R.id.btnFreezeApp)

        fun bind(
            item: UsageAppItem,
            maxSec: Long,
            pm: PackageManager,
            onFreezeApp: ((UsageAppItem) -> Unit)?,
        ) {
            pkg.text = item.packageName
            name.text = item.displayLabel(resolveLabel(item.packageName, pm))
            val sec = if (item.showDailyTotal) item.totalSeconds else item.avgSecondsPerDay
            duration.text = if (item.showDailyTotal) {
                formatDailyDuration(item.totalSeconds)
            } else {
                formatAvgDuration(item.avgSecondsPerDay)
            }
            bar.progress = ((sec * 100) / maxSec).toInt().coerceIn(0, 100)
            bindIcon(item, pm)
            btnFreeze.setOnClickListener { onFreezeApp?.invoke(item) }
        }

        private fun bindIcon(item: UsageAppItem, pm: PackageManager) {
            val fromServer: Bitmap? = item.iconBase64?.let { AppIconHelper.fromBase64Png(it) }
            if (fromServer != null) {
                icon.setImageBitmap(fromServer)
                return
            }
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

        private fun formatAvgDuration(avgSeconds: Long): String {
            val minutes = avgSeconds / 60
            return if (minutes > 0) "$minutes د/يوم" else "<1 د/يوم"
        }

        private fun formatDailyDuration(totalSeconds: Long): String {
            val minutes = totalSeconds / 60
            return if (minutes > 0) "$minutes د" else "<1 د"
        }
    }
}
