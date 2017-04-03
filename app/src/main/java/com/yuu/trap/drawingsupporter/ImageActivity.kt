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
 *
 * @property created 画像やテキストの表示が終了したかどうかを判定する
 *
 * @property editTexts 編集テキストエリアのHashMap。リージョン名をキーとする
 *
 * @property title 表示する画像のタイトル
 * @property path 表示する画像のパス
 * @property sha1 表示する画像のSHA1値
 *
 * @property transitions ハイパーリンクで移動した際の、元のタイトル、パス、画像のバイト配列を保存する。
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
        Log.d("IMAGE", "Path is $path Title is $title")

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
                    // 遷移していた場合は、遷移前に戻る
                    val data = transitions[transitions.lastIndex]
                    title = data.first
                    path = data.second
                    openData(data.third)
                    transitions.removeAt(transitions.lastIndex)
                    return true
                }

            }
        }
        return super.onKeyDown(keyCode, event)
    }

    /**
     * HTTPリクエストによって、画像を表示させる
     * @param id 表示する画像のID
     */
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
                    Toast.makeText(applicationContext, "Time out", Toast.LENGTH_LONG).show()
                }
            }

            override fun onPostExecute(param: Unit?) {
                Log.d("REQUESTED", id)
                openData(out.toByteArray())
            }
        }).execute()
    }

    fun openData(bytes : ByteArray) {
        // 画像をビューアに表示
        val image = findViewById(R.id.image) as ImageView
        image.setImageBitmap(BitmapFactory.decodeByteArray(bytes, 0, bytes?.size ?: 0))

        // テキストデータからその画像のテキストを取得し、ついでにSHA1値を保存
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

        // もともと画像ビューア以外の要素が画面内にあるのならば、それを排除
        val base = findViewById(R.id.scroll_target) as LinearLayout
        (0..base.childCount-1).map { base.getChildAt(it) }.filterNot { it is ImageView }.forEach { base.removeView(it) }

        data.forEach {
            // 必要な要素を追加
            val tv = TextView(this)
            val ed = EditText(this)
            val layout = findViewById(R.id.scroll_target) as LinearLayout
            tv.text = it.key
            ed.setText(it.value)
            editTexts[it.key] = ed
            layout.addView(tv)
            layout.addView(ed)

            // ハイパーリンクがあるなら、ボタンも追加する
            it.value.split('\n').filter { it.startsWith(">>") }.forEach {
                val button = Button(this)
                button.text = it.substring(2)
                button.setOnClickListener { _ ->
                    val pair = searchQuery(it.substring(2))
                    Log.d("LINK", "${pair?.second}")
                    if(pair != null) {
                        transitions.add(Triple(title!!, path!!, bytes))
                        title = pair.first.title
                        path = pair.second
                        openDataByRequest(pair.first.id)
                    }
                }
                layout.addView(button)
            }
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