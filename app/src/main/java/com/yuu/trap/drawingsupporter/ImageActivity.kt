package com.yuu.trap.drawingsupporter

import android.app.Activity
import android.content.Intent
import android.graphics.BitmapFactory
import android.os.AsyncTask
import android.os.Bundle
import android.support.design.widget.FloatingActionButton
import android.support.v7.app.AppCompatActivity
import android.util.Log
import android.widget.ImageView
import com.google.api.client.http.GenericUrl
import com.google.api.services.drive.model.File
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.io.InputStream

/**
 * 画像を表示するActivity
 * @author yuu
 * @since 2017/04/02
 */
class ImageActivity : AppCompatActivity(){
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        //画面構成をセット
        setContentView(R.layout.activity_image)

        //ボタンを設定
        val back = findViewById(R.id.back) as FloatingActionButton
        back.setOnClickListener {
            finish()
        }

        val id = intent.getStringExtra("Image")
        if(id != null) {
            (object :AsyncTask<Unit, Unit, Unit>(){
                val out = ByteArrayOutputStream()
                override fun doInBackground(vararg params: Unit?) {
                    val request = MainActivity.service?.files()?.get(id)
                    request?.alt = "media"
                    request?.executeAndDownloadTo(out)
                }

                override fun onPostExecute(param: Unit?) {
                    val bytes = out.toByteArray()
                    val image = findViewById(R.id.image) as ImageView
                    image.setImageBitmap(BitmapFactory.decodeByteArray(bytes, 0, bytes?.size ?: 0))
                }
            }).execute()
        }

    }
}