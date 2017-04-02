package com.yuu.trap.drawingsupporter

import android.Manifest
import android.accounts.AccountManager
import android.app.Activity
import android.app.AlertDialog
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.AsyncTask
import android.os.Bundle
import android.support.design.widget.FloatingActionButton
import android.support.design.widget.NavigationView
import android.support.v4.app.ActivityCompat
import android.support.v4.content.ContextCompat
import android.support.v4.view.GravityCompat
import android.support.v4.widget.DrawerLayout
import android.support.v7.app.ActionBarDrawerToggle
import android.support.v7.app.AppCompatActivity
import android.support.v7.widget.Toolbar
import android.util.Log
import android.view.Menu
import android.view.MenuItem
import android.widget.ListView
import com.google.api.client.extensions.android.http.AndroidHttp
import com.google.api.client.googleapis.GoogleUtils
import com.google.api.client.googleapis.extensions.android.gms.auth.GoogleAccountCredential
import com.google.api.client.googleapis.extensions.android.gms.auth.UserRecoverableAuthIOException
import com.google.api.client.http.FileContent
import com.google.api.client.json.gson.GsonFactory
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.IOException
import java.net.SocketTimeoutException
import java.util.*
import kotlin.collections.ArrayList

/**
 * 最初の画面処理を担当するクラス
 * @author yuu
 * @since 2017/03/30
 *
 * @property credential Google APIs Client Library for Javaを利用するための認証情報
 * @property service Google APIs Client Library for Javaの利用元
 *
 * @property remainTask 全処理終了後に処理を行うためのタスクのカウンタ
 *
 * @property syncRoot グーグルドライブのフォルダ同期のルート。ただし、クエリの都合上、IDは正しいが、タイトルなどは正しくない。
 * @property nowRoots 現在参照中のフォルダルートの連なり
 *
 */
class MainActivity : AppCompatActivity(), NavigationView.OnNavigationItemSelectedListener {
    private val REQUEST_ACCOUNT = 3
    private val MY_PERMISSIONS_REQUEST_READ_CONTACTS = 4
    private val REQUEST_AUTHORIZATION = 5

    companion object {
        var credential : GoogleAccountCredential? = null
        var service : com.google.api.services.drive.Drive? = null
    }

    private val remainTask = ArrayList<Boolean>()

    private var dataID : String? = null
    private var syncRoot : ListItem? = null
    private var nowRoots = ArrayList<ListItem>()

    private var adapter : ImageArrayAdapter? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // 初期情報取得
        val userName = getPreferences(Context.MODE_PRIVATE).getString("userName", null)
        dataID = getPreferences(Context.MODE_PRIVATE).getString("data", null)

        // 認証
        checkPermission()
        // 保存されたユーザー名があるならば設定
        if(userName != null) {
            credential = GoogleAccountCredential.usingOAuth2(this, Arrays.asList("https://www.googleapis.com/auth/drive"))
            credential?.selectedAccountName = userName
            service = com.google.api.services.drive.Drive.Builder(AndroidHttp.newCompatibleTransport(), GsonFactory(), credential).build()
        }
        // ユーザー名が保存されていないか、適切でない場合は取得
        if(credential?.selectedAccountName == null) {
            credential = GoogleAccountCredential.usingOAuth2(this, Arrays.asList("https://www.googleapis.com/auth/drive"))
            startActivityForResult(credential?.newChooseAccountIntent(), REQUEST_ACCOUNT)
        }

        // 画面構成をセット
        setContentView(R.layout.activity_main)

        // ツールバーを設定
        val toolbar = findViewById(R.id.toolbar) as Toolbar
        setSupportActionBar(toolbar)

        // 戻るボタンを設定
        val back = findViewById(R.id.pre_back) as FloatingActionButton
        back.setOnClickListener {
            // 現在のルートに親があるならば、そこに戻る
            Log.d("ROOT", "${nowRoots.size}")
            if(nowRoots.size > 1)
                addElements(nowRoots[nowRoots.lastIndex - 1])
            val content = File("$filesDir/text.db")
            content.forEachLine { Log.d("DOWNLOADS", it) }
        }

        //ツールバーのトグルを設定
        val drawer = findViewById(R.id.drawer_layout) as DrawerLayout
        val toggle = ActionBarDrawerToggle(this, drawer, toolbar, R.string.navigation_drawer_open, R.string.navigation_drawer_close)
        drawer.addDrawerListener(toggle)
        toggle.syncState()

        //ナビゲーションのリスナーを設定
        val navigationView = findViewById(R.id.nav_view) as NavigationView
        navigationView.setNavigationItemSelectedListener(this)

        //リストを設定
        val list = findViewById(R.id.list) as ListView
        adapter = ImageArrayAdapter(this, R.layout.list_view_image_item, ArrayList())
        list.adapter = adapter
        list.setOnItemClickListener { adapterView, _, i, _ ->
            val item = (adapterView as ListView).getItemAtPosition(i) as ListItem
            // フォルダなら展開、ファイルならイメージビューアへ遷移
            if(item.isFolder)
                addElements(item)
            else
                showImage(item)
        }

        // 保存されたリストがあるならば、再現する
        unparseListItem()
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        when(requestCode) {
            // ユーザー設定後に遷移
            REQUEST_ACCOUNT -> {
                if(resultCode == Activity.RESULT_OK && data != null && data.extras != null) {
                    val name = data.getStringExtra(AccountManager.KEY_ACCOUNT_NAME)
                    if(name != null) {
                        credential?.selectedAccountName = name
                        service = com.google.api.services.drive.Drive.Builder(AndroidHttp.newCompatibleTransport(), GsonFactory(), credential).build()
                        // ユーザー名を保存
                        getPreferences(Context.MODE_PRIVATE).edit().putString("userName", credential?.selectedAccountName).apply()
                    }
                }
            }
        }
    }

    override fun onBackPressed() {
        val drawer = findViewById(R.id.drawer_layout) as DrawerLayout
        if (drawer.isDrawerOpen(GravityCompat.START)) {
            drawer.closeDrawer(GravityCompat.START)
        } else {
            super.onBackPressed()
        }
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.main, menu)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        when(item.itemId) {
            R.id.action_settings -> return true
            R.id.action_sync -> { syncDrive(); return true }
            R.id.action_user -> {
                startActivityForResult(credential?.newChooseAccountIntent(), REQUEST_ACCOUNT)
                return true
            }
            R.id.action_sync_file -> {
                syncData()
                return true
            }
            R.id.action_upload_file -> {
                uploadData()
                return true
            }
        }

        return super.onOptionsItemSelected(item)
    }

    override fun onNavigationItemSelected(item: MenuItem): Boolean {
        // Handle navigation view item clicks here.
        val id = item.itemId

        if (id == R.id.nav_camera) {
            // Handle the camera action
        } else if (id == R.id.nav_gallery) {

        } else if (id == R.id.nav_slideshow) {

        } else if (id == R.id.nav_manage) {

        } else if (id == R.id.nav_share) {

        } else if (id == R.id.nav_send) {

        }

        val drawer = findViewById(R.id.drawer_layout) as DrawerLayout
        drawer.closeDrawer(GravityCompat.START)
        return true
    }

    /**
     * 認証状態を調べ、認証されていなければ、認証のダイアログを出す
     */
    fun checkPermission() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.GET_ACCOUNTS) != PackageManager.PERMISSION_GRANTED) {
            if (!ActivityCompat.shouldShowRequestPermissionRationale(this, Manifest.permission.GET_ACCOUNTS))
                ActivityCompat.requestPermissions(this, Array(1, {Manifest.permission.GET_ACCOUNTS}), MY_PERMISSIONS_REQUEST_READ_CONTACTS)
        }
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.INTERNET) != PackageManager.PERMISSION_GRANTED) {
            if (!ActivityCompat.shouldShowRequestPermissionRationale(this, Manifest.permission.INTERNET))
                ActivityCompat.requestPermissions(this, Array(1, {Manifest.permission.INTERNET}), MY_PERMISSIONS_REQUEST_READ_CONTACTS)
        }
    }

    /**
     * Google Driveとのファイル同期を行う
     */
    fun syncDrive() {
        searchDrawingSupporter()
    }

    fun syncData() {
        (object : AsyncTask<Void, Void, Unit>() {
            override fun doInBackground(vararg params: Void?){
                var token : String? = null
                try {
                    // テキストデータのIDを取得する
                    do {
                        val result = service?.files()?.list()
                                ?.setQ("title = 'text.db'")
                                ?.setPageToken(token)
                                ?.execute()
                        Log.d("RESULT", "$result")
                        result?.items?.filterNot { it.labels.trashed }?.forEach {
                            dataID = it.id
                            getPreferences(Context.MODE_PRIVATE).edit().putString("data", dataID).apply()
                        }
                        Log.d("RESULT", "$dataID, Ended")
                        token = result?.nextPageToken
                    } while(token != null)
                    if(dataID != null) {
                        (object :AsyncTask<Unit, Unit, Unit>(){
                            val out = ByteArrayOutputStream()
                            override fun doInBackground(vararg params: Unit?) {
                                MainActivity.service?.files()?.get(dataID)?.setAlt("media")?.executeAndDownloadTo(out)
                            }

                            override fun onPostExecute(param: Unit?) {
                                openFileOutput("text.db", Context.MODE_PRIVATE).write(out.toByteArray())
                                val acontent = File("$filesDir/text.db")
                                acontent.forEachLine { Log.d("DOWNLOADS", it) }
                            }
                        }).execute()
                    }
                } catch (e: UserRecoverableAuthIOException) {
                    startActivityForResult(e.intent, REQUEST_AUTHORIZATION)
                } catch (e: IOException) {
                    AlertDialog.Builder(applicationContext).setTitle("Sync Error").setMessage(e.message).create().show()
                } catch (e: SocketTimeoutException) {
                    AlertDialog.Builder(applicationContext).setTitle("Time out").setMessage(e.message).create().show()
                }
            }
        }).execute()
    }

    fun uploadData() {
        (object : AsyncTask<Void, Void, Unit>() {
            override fun doInBackground(vararg params: Void?){
                var token : String? = null
                try {
                    if(dataID != null) {
                        (object :AsyncTask<Unit, Unit, Unit>(){
                            override fun doInBackground(vararg params: Unit?) {
                                val file = MainActivity.service?.files()?.get(dataID)?.execute()
                                val content = File("$filesDir/text.db")
                                val media = FileContent(file?.mimeType, content)
                                MainActivity.service?.files()?.update(dataID, file, media)?.execute()
                                Log.d("UPLOAD", "UPLOADED")
                            }

                            override fun onPostExecute(param: Unit?) {
                            }
                        }).execute()
                    }
                } catch (e: UserRecoverableAuthIOException) {
                    startActivityForResult(e.intent, REQUEST_AUTHORIZATION)
                }
            }
        }).execute()

    }

    /**
     * DrawingSupporter.jarを検索し、そこを基点に再帰検索を繰り返す
     */
    fun searchDrawingSupporter() {
        (object : AsyncTask<Void, Void, Unit>() {
            override fun doInBackground(vararg params: Void?){
                var token : String? = null
                try {
                    do {
                        // TitleがDrawing Supporter.jarであるファイルを検索する
                        val result = service?.files()?.list()
                                ?.setQ("title = 'DrawingSupporter.jar'")
                                ?.setPageToken(token)
                                ?.execute()
                        // そのファイルの親からフォルダを開いていく
                        result?.items?.forEach {
                            syncRoot = ListItem(it.parents.first().id, true, ".", null, ArrayList())
                            expandFolder(it.parents.first().id, syncRoot!!)
                        }
                        token = result?.nextPageToken
                    } while(token != null)
                } catch (e: SocketTimeoutException) {
                    Log.d("RESULT", "RETRY")
                    doInBackground()
                } catch (e: UserRecoverableAuthIOException) {
                    startActivityForResult(e.intent, REQUEST_AUTHORIZATION)
                }
            }
        }).execute()
    }

    fun expandFolder(id : String, root : ListItem) {
        Log.d("RESULT", "ID is $id")
        remainTask.add(true)
        (object : AsyncTask<Void, Void, Unit>() {
            override fun doInBackground(vararg params: Void?){
                var token : String? = null
                try {
                    do {
                        val result = service?.files()?.list()
                                ?.setQ("'$id' in parents and (mimeType contains 'image/' or mimeType = 'application/vnd.google-apps.folder')")
                                ?.setPageToken(token)
                                ?.execute()
                        Log.d("RESULT", "$result")
                        result?.items?.filterNot { it.title.startsWith('.')}?.forEach {
                            Log.d("RESULT", it.title)
                            val isFolder = it.mimeType == "application/vnd.google-apps.folder"
                            val item = ListItem(it.id, isFolder, it.title, root, ArrayList())
                            root.children.add(item)
                            if(isFolder)
                                expandFolder(it.id, item)
                        }
                        token = result?.nextPageToken
                    } while(token != null)
                    remainTask.removeAt(remainTask.size - 1)
                } catch (e: SocketTimeoutException) {
                    Log.d("RESULT", "RETRY")
                    doInBackground()
                } catch (e: UserRecoverableAuthIOException) {
                    startActivityForResult(e.intent, REQUEST_AUTHORIZATION);
                }
            }
            override fun onPostExecute(result: Unit?) {
                if(remainTask.isEmpty())
                    if(syncRoot != null) {
                        addElements(syncRoot!!)
                        parseListItem()
                    }
            }
        }).execute()
    }

    fun addElements(root : ListItem) {
        adapter?.clear()
        root.children.forEach {
            adapter?.add(it)
        }
        adapter?.notifyDataSetChanged()
        while(nowRoots.contains(root)) {
            nowRoots.removeAt(nowRoots.lastIndex)
        }
        nowRoots.add(root)
    }

    fun parseListItem(){
        Log.d("PARSE", "PARSE START")
        var result = ""
        if(syncRoot != null)
           result = parseListItem(syncRoot!!, 0)
        val pref = getPreferences(Context.MODE_PRIVATE)
        val e = pref.edit()
        e.putString("listData", result)
        e.commit()
        Log.d("SAVE", "Written")
    }
    fun parseListItem(root : ListItem, depth : Int) : String{
        var result = "{\nid:${root.id}\nis:${root.isFolder}\ntitle:${root.title}\n"
        root.children.forEach {
            result += parseListItem(it, depth + 1)
        }
        result += "}\n"
        return result
    }

    fun unparseListItem() {
        var processingItem : ListItem? = null
        val parents = ArrayList<ListItem>()
        val listData = getPreferences(Context.MODE_PRIVATE).getString("listData", null)
        Log.d("UNPARSE", "UnparseStart")
        if(listData != null) {
            listData.lines().forEach {
                when {
                    it == "{" -> {
                        processingItem = ListItem("", true, "", if(parents.isEmpty()) null else parents[parents.lastIndex], ArrayList())
                        if(parents.isNotEmpty())
                            parents[parents.lastIndex].children.add(processingItem!!)
                        parents.add(processingItem!!)
                    }
                    it.startsWith("id:") -> { processingItem?.id = it.substring(3) }
                    it.startsWith("is:") -> { processingItem?.isFolder = it.substring(3) == "true" }
                    it.startsWith("title:") -> { processingItem?.title = it.substring(6) }
                    it == "}" -> { if(parents.size > 1) parents.removeAt(parents.lastIndex) }
                }
            }
        }
        Log.d("UNPARSE", "${parents[0].title} -> ${parents[0].children[0].title}}")
        if(parents.isNotEmpty()) {
            syncRoot = parents[0]
            addElements(syncRoot!!)
        }
    }

    fun showImage(item : ListItem) {
        val intent = Intent(application, ImageActivity::class.java)
                .putExtra("Image", item.id)
                .putExtra("Title", item.title)
                .putExtra("Path", nowRoots.map { it.title }.reduceRight { s, acc -> s + "/" + acc })
        startActivity(intent)
    }
}
