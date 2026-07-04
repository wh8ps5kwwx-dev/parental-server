package com.example.myrana.parent.ui

import android.view.View
import android.widget.ImageView
import android.widget.TextView
import androidx.annotation.DrawableRes
import com.example.myrana.R

object ParentHubUi {

    fun bindTile(
        root: View,
        @DrawableRes iconRes: Int,
        label: String,
        onClick: () -> Unit,
    ) {
        root.findViewById<ImageView>(R.id.hubTileIcon).setImageResource(iconRes)
        root.findViewById<TextView>(R.id.hubTileLabel).text = label
        root.setOnClickListener { onClick() }
    }
}
