package com.yuu.trap.drawingsupporter

import android.app.AlertDialog
import android.graphics.BitmapFactory
import android.os.AsyncTask
import android.os.Bundle
import android.support.design.widget.FloatingActionButton
import android.support.v7.app.AppCompatActivity
import android.util.Log
import android.view.KeyEvent
import android.widget.*
import com.yuu.trap.drawingsupporter.text.TextData
import java.io.*
import java.net.SocketTimeoutException
import java.text.SimpleDateFormat
import java.util.*
import kotlin.collections.HashMap

/**
 * 画像を表示するActivity
 * @author yuu
 * @since 2017/04/02
 */
class ImageActivity : AppCompatActivity(){
    private var created = false

    private val editTexts = HashMap<String, EditText>()

    private var title : String? = null
    private var path : String? = null
    private var sha1 : String? = null

    private val transitions = ArrayList<Triple<String, String, ByteArray>>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // パス情報を取得
        title = intent.getStringExtra("Title")
        path = intent.getStringExtra("Path") + "/$title"
        Log.d("PATH", "Path is $path Title is $title")

        //画面構成をセット
        setContentView(R.layout.activity_image)

        //ボタンを設定
        val back = findViewById(R.id.back) as FloatingActionButton
        back.setOnClickListener {
            finish()
        }

        val id = intent.getStringExtra("Image")
        if(id != null)
            openDataByRequest(id)
    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent?): Boolean {
        when(keyCode) {
            KeyEvent.KEYCODE_BACK -> {
                if(transitions.isEmpty())
                    return super.onKeyDown(keyCode, event)
                else {
                    val data = transitions[transitions.lastIndex]
                    title = data.first
                    path = data.second
                    val image = findViewById(R.id.image) as ImageView
                    image.setImageBitmap(BitmapFactory.decodeByteArray(data.third, 0, data.third.size))
                    openData(data.third)
                    transitions.removeAt(transitions.lastIndex)
                    return true
                }

            }
        }
        return super.onKeyDown(keyCode, event)
    }

    fun openDataByRequest(id : String) {
        Log.d("REQUEST", id)
        (object :AsyncTask<Unit, Unit, Unit>(){
            val out = ByteArrayOutputStream()
            override fun doInBackground(vararg params: Unit?) {
                try {
                    val request = MainActivity.service?.files()?.get(id)
                    request?.alt = "media"
                    request?.executeAndDownloadTo(out)
                } catch (e : SocketTimeoutException) {
                    AlertDialog.Builder(applicationContext).setTitle("Time out").setMessage(e.message).create().show()
                }
            }

            override fun onPostExecute(param: Unit?) {
                Log.d("REQUESTED", id)
                val bytes = out.toByteArray()
                val image = findViewById(R.id.image) as ImageView
                image.setImageBitmap(BitmapFactory.decodeByteArray(bytes, 0, bytes?.size ?: 0))
                openData(bytes)
            }
        }).execute()
    }

    fun openData(bytes : ByteArray) {
        val data = TextData.parseFile(BufferedReader(InputStreamReader(openFileInput("text.db"))), path!!, bytes)
        sha1 = TextData.sha256(bytes)
        //データが空ならデフォルトの項目を追加する
        if(data.isEmpty()) {
            data["Exp"] = ""
            data["Memo"] = ""
            data["Tips"] = ""
            data["Other"] = ""
        }
        if(!data.containsKey("Date"))
            data["Date"] = SimpleDateFormat("yyyy/MM/dd").format(Date())
        val base = findViewById(R.id.scroll_target) as LinearLayout
        (0..base.childCount-1).map { base.getChildAt(it) }.filterNot { it is ImageView }.forEach { base.removeView(it) }
        data.forEach {
            val tv = TextView(this)
            tv.text = it.key
            val ed = EditText(this)
            ed.setText(it.value)
            val layout = findViewById(R.id.scroll_target) as LinearLayout
            layout.addView(tv)
            layout.addView(ed)
            it.value.split('\n').filter { it.startsWith(">>") }.forEach {
                val button = Button(this)
                button.text = it.substring(2)
                button.setOnClickListener { _ ->
                    val pair = searchQuery(it.substring(2))
                    Log.d("PAIR", "${pair?.second}")
                    if(pair != null) {
                        transitions.add(Triple(title!!, path!!, bytes))
                        title = pair.first.title
                        path = pair.second
                        openDataByRequest(pair.first.id)
                    }
                }
                layout.addView(button)
            }
            editTexts[it.key] = ed
        }
        created = true
    }

    fun saveData() {
        val update = HashMap<String, String>()
        editTexts.forEach {
            update[it.key] = it.value.text.toString()
        }
        TextData.unparseFile(BufferedReader(InputStreamReader(openFileInput("text.db"))), "$filesDir/text.db", path!!, sha1!!, update)
    }

    override fun onDestroy() {
        super.onDestroy()
        if(created)
            saveData()
    }

    fun searchQuery(title : String) : Pair<ListItem, String>?{
        return searchQuery(title, MainActivity.syncRoot!!, ".")
    }
    fun searchQuery(title : String, item : ListItem, root : String) : Pair<ListItem, String>?{
        if(item.isFolder)
            return item.children.map { searchQuery(title, it, "$root/${it.title}") }.filter { it != null }.firstOrNull()
        else {
            if(title == if(item.title.contains('.')) item.title.substring(0, item.title.lastIndexOf('.')) else item.title)
                return item to "$root/${item.title}"
            else
                return null
        }
    }

}