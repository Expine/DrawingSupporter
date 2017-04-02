package com.yuu.trap.drawingsupporter

import android.content.Context
import android.graphics.BitmapFactory
import android.os.AsyncTask
import android.os.Bundle
import android.support.design.widget.FloatingActionButton
import android.support.v7.app.AppCompatActivity
import android.util.Log
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import com.yuu.trap.drawingsupporter.text.TextData
import java.io.*
import java.util.*

/**
 * 画像を表示するActivity
 * @author yuu
 * @since 2017/04/02
 */
class ImageActivity : AppCompatActivity(){
    var title : String? = null
    var path : String? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // パス情報を取得
        title = intent.getStringExtra("Title")
        path = intent.getStringExtra("Path")
        Log.d("PATH", "Path is $path Title is $title")

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
                    openData(bytes)
                }
            }).execute()
        }
    }

    fun openData(bytes : ByteArray) {
//        val data = TextData.parseFile(BufferedReader(InputStreamReader(openFileInput("text.db"))), path!!, bytes)
        val data = HashMap<String, String>()
        //データが空ならデフォルトの項目を追加する
        if(data.isEmpty()) {
            data["Exp"] = ""
            data["Memo"] = ""
            data["Tips"] = ""
            data["Other"] = ""
        }
//        data["Date"] = if(data.containsKey("Date")) data["Date"] else SimpleDateFormat("yyyy/MM/dd").format(Date())
        data.forEach {
            val tv = TextView(this)
            tv.text = it.key
            val layout = findViewById(R.id.scroll_target) as LinearLayout
            layout.addView(tv)
        }
    }

    fun saveData(bytes : ByteArray) {
        TextData.unparseFile(BufferedReader(InputStreamReader(openFileInput("text.db"))), openFileOutput("text.db", Context.MODE_PRIVATE), path!!, bytes, HashMap<String, String>())
    }

    override fun onDestroy() {
        super.onDestroy()
//        saveData()
    }

}