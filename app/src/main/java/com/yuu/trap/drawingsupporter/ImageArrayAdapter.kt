package com.yuu.trap.drawingsupporter

import android.content.Context
import android.widget.ArrayAdapter
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView


data class ListItem(var id : String, var isFolder : Boolean, var title : String, var parent : ListItem?, val children : ArrayList<ListItem>)

class ImageArrayAdapter(context : Context, private val resource : Int, private val objects : ArrayList<ListItem>) : ArrayAdapter<ListItem>(context, resource, objects){
    private val inflater = context.getSystemService(Context.LAYOUT_INFLATER_SERVICE) as LayoutInflater

    override fun getView(position: Int, convertView: View?, parent: ViewGroup?): View {
        val view = convertView ?: inflater.inflate(resource, null)

        val item = objects[position]
        (view.findViewById(R.id.item_text) as TextView).text = item.title
        (view.findViewById(R.id.item_image) as ImageView).setImageResource(if(item.isFolder) R.mipmap.ic_folder else R.mipmap.ic_image)

        return view
    }
}