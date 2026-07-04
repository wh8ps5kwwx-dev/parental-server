package com.example.myrana.parent.ui

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.example.myrana.R
import com.example.myrana.data.remote.GuardianApi
import com.example.myrana.util.AppIconHelper

class FreezeAppGridAdapter(
    private val onSelect: (GuardianApi.InstalledAppItem) -> Unit,
) : RecyclerView.Adapter<FreezeAppGridAdapter.VH>() {

    private val items = mutableListOf<GuardianApi.InstalledAppItem>()
    private var selectedPackage: String? = null

    fun submit(newItems: List<GuardianApi.InstalledAppItem>) {
        items.clear()
        items.addAll(newItems.sortedBy { it.appLabel.lowercase() })
        selectedPackage = null
        notifyDataSetChanged()
    }

    fun selectedItem(): GuardianApi.InstalledAppItem? =
        selectedPackage?.let { pkg -> items.firstOrNull { it.packageName == pkg } }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_freeze_app_tile, parent, false)
        return VH(view)
    }

    override fun onBindViewHolder(holder: VH, position: Int) {
        val item = items[position]
        holder.bind(
            item = item,
            selected = item.packageName == selectedPackage,
            onSelect = {
                selectedPackage = item.packageName
                notifyDataSetChanged()
                onSelect(item)
            },
        )
    }

    override fun getItemCount(): Int = items.size

    class VH(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val root: View = itemView.findViewById(R.id.freezeAppTileRoot)
        private val icon: ImageView = itemView.findViewById(R.id.imgFreezeAppIcon)
        private val name: TextView = itemView.findViewById(R.id.textFreezeAppName)

        fun bind(
            item: GuardianApi.InstalledAppItem,
            selected: Boolean,
            onSelect: () -> Unit,
        ) {
            name.text = item.appLabel
            val fromServer = item.iconBase64?.let { AppIconHelper.fromBase64Png(it) }
            if (fromServer != null) {
                icon.setImageBitmap(fromServer)
            } else {
                icon.setImageResource(android.R.drawable.sym_def_app_icon)
            }
            root.setBackgroundResource(
                if (selected) R.drawable.bg_freeze_app_tile_selected
                else R.drawable.bg_freeze_app_tile,
            )
            root.setOnClickListener { onSelect() }
        }
    }
}
