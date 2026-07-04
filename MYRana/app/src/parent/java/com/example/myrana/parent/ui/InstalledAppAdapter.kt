package com.example.myrana.parent.ui

import android.content.pm.PackageManager
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.example.myrana.R
import com.example.myrana.data.remote.GuardianApi
import com.example.myrana.util.AppIconHelper

/** قائمة التطبيقات المثبتة على جهاز الطفل. */
class InstalledAppAdapter(
    private val pm: PackageManager,
    private val onBlockApp: (GuardianApi.InstalledAppItem) -> Unit,
) : RecyclerView.Adapter<InstalledAppAdapter.VH>() {

    private val items = mutableListOf<GuardianApi.InstalledAppItem>()

    fun submit(newItems: List<GuardianApi.InstalledAppItem>) {
        items.clear()
        items.addAll(newItems)
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_installed_app, parent, false)
        return VH(view)
    }

    override fun onBindViewHolder(holder: VH, position: Int) {
        holder.bind(items[position], pm, onBlockApp)
    }

    override fun getItemCount(): Int = items.size

    class VH(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val icon: ImageView = itemView.findViewById(R.id.imgAppIcon)
        private val name: TextView = itemView.findViewById(R.id.textAppName)
        private val pkg: TextView = itemView.findViewById(R.id.textPackageName)
        private val btnBlock: ImageButton = itemView.findViewById(R.id.btnBlockApp)

        fun bind(
            item: GuardianApi.InstalledAppItem,
            pm: PackageManager,
            onBlockApp: (GuardianApi.InstalledAppItem) -> Unit,
        ) {
            name.text = item.appLabel
            pkg.text = item.packageName
            val fromServer = item.iconBase64?.let { AppIconHelper.fromBase64Png(it) }
            if (fromServer != null) {
                icon.setImageBitmap(fromServer)
            } else {
                icon.setImageResource(android.R.drawable.sym_def_app_icon)
            }
            btnBlock.setOnClickListener { onBlockApp(item) }
        }
    }
}
