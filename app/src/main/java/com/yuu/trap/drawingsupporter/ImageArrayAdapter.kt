package com.yuu.trap.drawingsupporter

import android.content.Context
import android.widget.ArrayAdapter
import com.google.android.gms.drive.DriveId
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView


data class ListItem(val imageId : Int, val text : String, val isFile : Boolean, val id : DriveId)

class ImageArrayAdapter(context : Context, private val resource : Int, private val objects : ArrayList<ListItem>) : ArrayAdapter<ListItem>(context, resource, objects){
    private val inflater = context.getSystemService(Context.LAYOUT_INFLATER_SERVICE) as LayoutInflater

    override fun getView(position: Int, convertView: View?, parent: ViewGroup?): View {
        val view = convertView ?: inflater.inflate(resource, null)

        val item = objects.get(position)
        (view.findViewById(R.id.item_text) as TextView).text = item.text
        (view.findViewById(R.id.item_image) as ImageView).setImageResource(item.imageId)

        return view
    }
}