package com.yuu.trap.drawingsupporter

import android.Manifest
import android.accounts.AccountManager
import android.app.Activity
import android.app.AlertDialog
import android.app.ProgressDialog
import android.content.ContentUris
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.database.Cursor
import android.net.Uri
import android.os.*
import android.provider.DocumentsContract
import android.provider.MediaStore
import android.support.design.widget.FloatingActionButton
import android.support.design.widget.NavigationView
import android.support.v4.app.ActivityCompat
import android.support.v4.content.ContextCompat
import android.support.v4.content.FileProvider
import android.support.v4.view.GravityCompat
import android.support.v4.widget.DrawerLayout
import android.support.v7.app.ActionBarDrawerToggle
import android.support.v7.app.AppCompatActivity
import android.support.v7.widget.Toolbar
import android.util.Log
import android.view.KeyEvent
import android.view.Menu
import android.view.MenuItem
import android.widget.EditText
import android.widget.ListView
import android.widget.Toast
import com.google.api.client.extensions.android.http.AndroidHttp
import com.google.api.client.googleapis.extensions.android.gms.auth.GoogleAccountCredential
import com.google.api.client.googleapis.extensions.android.gms.auth.UserRecoverableAuthIOException
import com.google.api.client.http.FileContent
import com.google.api.client.json.gson.GsonFactory
import com.google.api.services.drive.model.ParentReference
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.IOException
import java.net.SocketTimeoutException
import java.text.SimpleDateFormat
import java.util.*
import kotlin.collections.ArrayList

/**
 * 最初の画面処理を担当するクラス
 * @author yuu
 * @since 2017/03/30
 *
 * @property REQUEST_ACCOUNT ユーザー認証後の識別番号
 * @property MY_PERMISSIONS_REQUEST_READ_CONTACTS 権限の許可後の識別番号
 * @property REQUEST_AUTHORIZATION エラー時のユーザー認証後の識別番号
 *
 * @property credential Google APIs Client Library for Javaを利用するための認証情報
 * @property service Google APIs Client Library for Javaの利用元
 *
 * @property remainTask 全処理終了後に処理を行うためのタスクのカウンタ
 *
 * @property syncRoot グーグルドライブのフォルダ同期のルート。ただし、クエリの都合上、IDは正しいが、タイトルなどは正しくない。
 * @property nowRoots 現在参照中のフォルダルートの連なり
 * @property adapter リストのアダプター。動的に追加、削除するため必要
 *
 */
class MainActivity : AppCompatActivity(), NavigationView.OnNavigationItemSelectedListener {
    private val REQUEST_ACCOUNT = 1
    private val MY_PERMISSIONS_REQUEST_READ_CONTACTS = 2
    private val REQUEST_AUTHORIZATION = 3
    private val RESULT_CAMERA = 4
    private val RESULT_PICK_IMAGEFILE = 5

    // 共通して使われるデータはCompanion Objectを使う
    companion object {
        var credential : GoogleAccountCredential? = null
        var service : com.google.api.services.drive.Drive? = null

        var syncRoot : ListItem? = null
    }

    private val remainTask = ArrayList<AsyncTask<Void, Void, Unit>>()
    private var nowRoots = ArrayList<ListItem>()
    private var adapter : ImageArrayAdapter? = null

    private var imagePath : String? = null
    private var cameraUri : Uri? = null

    private var progress : ProgressDialog? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Log.d("CREATE", "ON CREATE")

        // 権限の許可
        checkPermission()

        startUp()
    }

    /**
     * 権限の認証後に行われる初期設定
     */
    fun startUp() {
        Log.d("START", "ON START")
        // 初期情報取得
        val userName = getPreferences(Context.MODE_PRIVATE).getString("userName", null)

        credential = GoogleAccountCredential.usingOAuth2(this, Arrays.asList("https://www.googleapis.com/auth/drive"))
        // 保存されたユーザー名があるならば設定
        if(userName != null) {
            credential?.selectedAccountName = userName
            service = com.google.api.services.drive.Drive.Builder(AndroidHttp.newCompatibleTransport(), GsonFactory(), credential).build()
        }
        // ユーザー名が保存されていないか、適切でない場合は取得
        if(credential?.selectedAccountName == null) {
            startActivityForResult(credential?.newChooseAccountIntent(), REQUEST_ACCOUNT)
        }

        // 画面構成をセット
        setContentView(R.layout.activity_main)

        // ツールバーを設定
        val toolbar = findViewById(R.id.toolbar) as Toolbar
        setSupportActionBar(toolbar)

        // 戻るボタンを設定
        val back = findViewById(R.id.pre_back) as FloatingActionButton
        back.setOnClickListener { back() }

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
            // 権限認証後に遷移
            MY_PERMISSIONS_REQUEST_READ_CONTACTS -> startUp()

            // ユーザー設定後に遷移
            REQUEST_ACCOUNT -> {
                if(resultCode == Activity.RESULT_OK && data != null && data.extras != null) {
                    // 名前情報を取得し、nullでなければ設定
                    val name = data.getStringExtra(AccountManager.KEY_ACCOUNT_NAME)
                    if(name != null) {
                        credential?.selectedAccountName = name
                        service = com.google.api.services.drive.Drive.Builder(AndroidHttp.newCompatibleTransport(), GsonFactory(), credential).build()
                        // ユーザー名を保存
                        getPreferences(Context.MODE_PRIVATE).edit().putString("userName", credential?.selectedAccountName).apply()
                    }
                }
            }
            // カメラ処理後に遷移
            RESULT_CAMERA -> {
                Log.d("Camera", "$cameraUri")
                if(resultCode == Activity.RESULT_OK && cameraUri != null) {
                    uploadToDrive()
                }
            }

            //　イメージファイル選択処理後に遷移
            RESULT_PICK_IMAGEFILE -> {
                if (resultCode == Activity.RESULT_OK && data != null) {
                    imagePath = getPathFromUri(data.data)
                    Log.i("URI", "Uri ${data.data} Path: $imagePath")
                    if(imagePath != null)
                        uploadToDrive()
                    else
                        displayToast("Upload Error -- Can't get file path")
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
            R.id.action_sync -> { syncDrive(); return true }
            R.id.action_user -> { startActivityForResult(credential?.newChooseAccountIntent(), REQUEST_ACCOUNT); return true }
            R.id.action_sync_file -> { syncData(); return true }
            R.id.action_upload_file -> { uploadData(); return true }
            R.id.action_upload_camera -> { uploadByCamera(); return true }
            R.id.action_upload_gallery -> { uploadByGallery(); return true }
        }
        return super.onOptionsItemSelected(item)
    }

    override fun onNavigationItemSelected(item: MenuItem): Boolean {
        // Handle navigation view item clicks here.
        when(item.itemId) {
            R.id.nav_camera -> {}
            R.id.nav_gallery -> {}
            R.id.nav_slideshow -> {}
            R.id.nav_manage -> {}
            R.id.nav_share -> {}
            R.id.nav_send -> {}
        }

        val drawer = findViewById(R.id.drawer_layout) as DrawerLayout
        drawer.closeDrawer(GravityCompat.START)
        return true
    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent?): Boolean {
        when(keyCode) {
            // 遷移中の場合は、一個前の状態に戻す
            KeyEvent.KEYCODE_BACK -> return if(nowRoots.size == 1) super.onKeyDown(keyCode, event) else { back(); true }
        }
        return super.onKeyDown(keyCode, event)
    }

    /**
     * 戻るボタンの処理
     */
    fun back() {
        // 現在のルートに親があるならば、そこに戻る
        if(nowRoots.size > 1) {
            Log.d("BACK", nowRoots.map { it.title }.reduceRight { s, acc -> s + "/" + acc })
            nowRoots.removeAt(nowRoots.lastIndex)
            val element = nowRoots.removeAt(nowRoots.lastIndex)
            addElements(element)
        }
    }

    /**
     * 認証状態を調べ、認証されていなければ、認証のダイアログを出す
     * @return 権限が既にすべて認められているかどうか
     */
    fun checkPermission() : Boolean {
        val permissions = ArrayList<String>()
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.GET_ACCOUNTS) != PackageManager.PERMISSION_GRANTED)
            if (!ActivityCompat.shouldShowRequestPermissionRationale(this, Manifest.permission.GET_ACCOUNTS))
                permissions.add(Manifest.permission.GET_ACCOUNTS)
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.INTERNET) != PackageManager.PERMISSION_GRANTED)
            if (!ActivityCompat.shouldShowRequestPermissionRationale(this, Manifest.permission.INTERNET))
                permissions.add(Manifest.permission.INTERNET)
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.WRITE_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED)
            if (!ActivityCompat.shouldShowRequestPermissionRationale(this, Manifest.permission.WRITE_EXTERNAL_STORAGE))
                permissions.add(Manifest.permission.WRITE_EXTERNAL_STORAGE)
        if(permissions.isNotEmpty())
            ActivityCompat.requestPermissions(this, permissions.toTypedArray(), MY_PERMISSIONS_REQUEST_READ_CONTACTS)
        return permissions.isEmpty()
    }

    /**
     * Google Driveとのファイルとフォルダ構成の同期を行う
     */
    fun syncDrive() {
        searchDrawingSupporter()
        showProgress()
    }

    /**
     * Google Drive上にあるデータをダウンロードして同期する
     */
    fun syncData() {
        (object : AsyncTask<Void, Void, Unit>() {
            override fun doInBackground(vararg params: Void?){
                try {
                    val dataID = getSyncDataID()
                    if(dataID != null) {
                        val out = ByteArrayOutputStream()
                        MainActivity.service?.files()?.get(dataID)?.setAlt("media")?.executeAndDownloadTo(out)
                        openFileOutput("text.db", Context.MODE_PRIVATE).write(out.toByteArray())
                        Log.d("SYNC", "Downloads Ended")
                    }
                } catch (e: UserRecoverableAuthIOException) {
                    startActivityForResult(e.intent, REQUEST_AUTHORIZATION)
                } catch (e: IOException) {
                    displaySyncError()
                } catch (e: SocketTimeoutException) {
                    displayTimeOut()
                } finally {
                    hideProgress()
                }
            }
        }).execute()
        showProgress()
    }

    /**
     * Google Drive上にあるデータにアップロードする
     */
    fun uploadData() {
        (object : AsyncTask<Void, Void, Unit>() {
            override fun doInBackground(vararg params: Void?){
                try {
                    val dataID = getSyncDataID()
                    if(dataID != null) {
                        val file = MainActivity.service?.files()?.get(dataID)?.execute()
                        val media = FileContent(file?.mimeType, File("$filesDir/text.db"))
                        MainActivity.service?.files()?.update(dataID, file, media)?.execute()
                        Log.d("UPLOAD", "UPLOADED")
                    }
                } catch (e: UserRecoverableAuthIOException) {
                    startActivityForResult(e.intent, REQUEST_AUTHORIZATION)
                } catch (e: IOException) {
                    displaySyncError()
                } catch (e: SocketTimeoutException) {
                    displayTimeOut()
                } finally {
                    hideProgress()
                }
            }
        }).execute()
        showProgress()
    }

    /**
     * 同期するデータのIDを取得する
     * @return 同期するデータのID
     */
    fun getSyncDataID() : String? {
        var token : String? = null
        // テキストデータのIDを取得する
        do {
            val result = service?.files()?.list()
                    ?.setQ("title = 'text.db'")
                    ?.setPageToken(token)
                    ?.execute()
            result?.items?.filterNot { it.labels.trashed }?.forEach {
                Log.d("SYNC", "Sync id is ${it.id}")
                return it.id
            }
            token = result?.nextPageToken
        } while(token != null)

        // 何もなければNull
        return null
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
                            // 親の名前は . にして、相対表現を実現
                            syncRoot = ListItem(it.parents.first().id, true, ".", null, ArrayList())
                            expandFolder(it.parents.first().id, syncRoot!!)
                        }
                        token = result?.nextPageToken
                    } while(token != null)
                } catch (e: UserRecoverableAuthIOException) {
                    startActivityForResult(e.intent, REQUEST_AUTHORIZATION)
                    hideProgress()
                } catch (e: IOException) {
                    displaySyncError()
                    hideProgress()
                } catch (e: SocketTimeoutException) {
                    displayToast("Time out -- Retry")
                    doInBackground()
                }
            }
        }).execute()
    }

    /**
     * 指定IDをもつrootのリスト要素に、子供を追加して、以下再帰的に行う
     * @param id 展開するフォルダのID
     * @param root 展開するフォルダを表すリスト要素
     */
    fun expandFolder(id : String, root : ListItem) {
        Log.d("EXPAND", "ID is $id")
        val task = (object : AsyncTask<Void, Void, Unit>() {
            override fun doInBackground(vararg params: Void?){
                var token : String? = null
                try {
                    do {
                        // 指定の親を持つフォルダまたは画像を検索する
                        val result = service?.files()?.list()
                                ?.setQ("'$id' in parents and (mimeType contains 'image/' or mimeType = 'application/vnd.google-apps.folder')")
                                ?.setPageToken(token)
                                ?.execute()
                        result?.items?.filterNot { it.title.startsWith('.')}?.forEach {
                            Log.d("EXPAND", it.title)
                            val isFolder = it.mimeType == "application/vnd.google-apps.folder"
                            val item = ListItem(it.id, isFolder, it.title, root, ArrayList())
                            // 子供に追加した後、フォルダならば再展開
                            root.children.add(item)
                            if(isFolder)
                                expandFolder(it.id, item)
                        }
                        token = result?.nextPageToken
                    } while(token != null)
                    // 再帰処理が終わった判定を行うため、この処理が開始した段階で一つ減らす
                    remainTask.remove(this)
                } catch (e: UserRecoverableAuthIOException) {
                    remainTask.forEach { it.cancel(true) }
                    remainTask.clear()
                    startActivityForResult(e.intent, REQUEST_AUTHORIZATION)
                    hideProgress()
                } catch (e: IOException) {
                    remainTask.forEach { it.cancel(true) }
                    remainTask.clear()
                    displaySyncError()
                    hideProgress()
                } catch (e: SocketTimeoutException) {
                    displayToast("Time out -- Retry")
                    doInBackground()
                }
            }
            override fun onPostExecute(result: Unit?) {
                // 全ての処理が終わっていた場合は、同期元をリストに展開して、リスト構造をセーブする
                if(remainTask.isEmpty() && syncRoot != null) {
                    nowRoots.clear()
                    addElements(syncRoot!!)
                    parseListItem()
                    hideProgress()
                }
            }
        })
        // 再帰処理が終わった判定を行うため、この処理が開始した段階で一つ追加する
        remainTask.add(task)
        task.execute()
    }

    /**
     * リストの要素より下にある要素を展開して、リストに追加する
     * @param root 展開元のフォルダ
     */
    fun addElements(root : ListItem) {
        // リストの要素をすべて消す
        adapter?.clear()

        // リストにフォルダの子を名前でソートしてすべて追加
        root.children.sortedBy { it.title }.forEach {
            adapter?.add(it)
        }
        adapter?.notifyDataSetChanged()

        // 現在の展開元までのリストを更新
        nowRoots.add(root)
    }

    /**
     * 現在のリストを更新する
     */
    fun updateElements() {
        // リストの要素をすべて消す
        adapter?.clear()

        // リストにフォルダの子を名前でソートしてすべて追加
        nowRoots.last().children.sortedBy { it.title }.forEach {
            adapter?.add(it)
        }
        adapter?.notifyDataSetChanged()
    }

    /**
     * フォルダ構成をパースして保存する
     */
    fun parseListItem(){
        Log.d("PARSE", "Parse Start")
        val result = if(syncRoot != null) parseListItem(syncRoot!!, 0) else ""
        getPreferences(Context.MODE_PRIVATE).edit().putString("listData", result).commit()
        Log.d("PARSE", "Parse Written")
    }

    /**
     * フォルダ構成を再帰的にパースする
     * @param root 対象となるリスト要素
     * @param depth 現在処理中の深さ
     * @return パースした文字列
     */
    fun parseListItem(root : ListItem, depth : Int) : String{
        return "{\nid:${root.id}\nis:${root.isFolder}\ntitle:${root.title}\n${if(root.children.isNotEmpty()) root.children.map { parseListItem(it, depth + 1) }.reduce { acc, s -> acc + s } else ""}}\n"
    }

    /**
     * パースして保存された文字列からフォルダ構成を復元する
     */
    fun unparseListItem() {
        var processingItem : ListItem? = null
        val parents = ArrayList<ListItem>()
        val listData = getPreferences(Context.MODE_PRIVATE).getString("listData", null)
        if(listData != null && listData.isNotBlank()) {
            listData.lines().forEach {
                when {
                    // { は新規リスト要素を作成し、親が存在するならそこに格納しつつ、新たな親を作る
                    it == "{" -> {
                        processingItem = ListItem("", true, "", if(parents.isEmpty()) null else parents[parents.lastIndex], ArrayList())
                        if(parents.isNotEmpty())
                            parents[parents.lastIndex].children.add(processingItem!!)
                        parents.add(processingItem!!)
                    }
                    it.startsWith("id:") -> { processingItem?.id = it.substring(3) }
                    it.startsWith("is:") -> { processingItem?.isFolder = it.substring(3) == "true" }
                    it.startsWith("title:") -> { processingItem?.title = it.substring(6) }
                    // } は作られたリスト要素を閉じ、親の状態を閉じる
                    it == "}" -> { if(parents.size > 1) parents.removeAt(parents.lastIndex) }
                }
            }
        }
        if(parents.isNotEmpty()) {
            Log.d("UNPARSE", "${parents[0].title} -> ${parents[0].children[0].title}}")
            syncRoot = parents[0]
            addElements(syncRoot!!)
        }
    }

    /**
     * ビューアを起動して、画像とそのテキストを表示する
     * @param item 表示する画像のリスト要素
     */
    fun showImage(item : ListItem) {
        val intent = Intent(application, ImageActivity::class.java)
                .putExtra("Image", item.id)
                .putExtra("Title", item.title)
                .putExtra("Path", nowRoots.map { it.title }.reduceRight { s, acc -> s + "/" + acc })
        startActivity(intent)
    }

    fun uploadByCamera() {
        // 保存先のフォルダーを作成
        val cameraFolder = File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES), "IMG")
        cameraFolder.mkdirs()

        // 保存ファイル名
        imagePath = "${cameraFolder.path}/${SimpleDateFormat("ddHHmmss").format(Date())}.jpg"
        Log.d("CAMERA","filePath is $imagePath")

        // capture画像のファイルパス
        cameraUri = FileProvider.getUriForFile(this, "${applicationContext.packageName}.provider", File(imagePath))

        val intent = Intent(MediaStore.ACTION_IMAGE_CAPTURE)
        intent.putExtra(MediaStore.EXTRA_OUTPUT, cameraUri)
        startActivityForResult(intent, RESULT_CAMERA)
    }
    fun uploadToDrive() {
        val image = File(imagePath)
        val editView = EditText(this)
        editView.setText(image.nameWithoutExtension)
        AlertDialog.Builder(this).setIcon(android.R.drawable.ic_dialog_info).setTitle("アップロードするファイル名").setView(editView)
                .setPositiveButton("OK", { _ , _->
                    (object : AsyncTask<Void, Void, Unit>() {
                        override fun doInBackground(vararg params: Void?){
                            try {
                                val file = com.google.api.services.drive.model.File().setTitle("${editView.text}.${image.extension}").setMimeType("image/jpeg")
                                        .setParents(Arrays.asList(ParentReference().setId(nowRoots.last().id)))
                                val media = FileContent("image/jpeg", image)
                                val result = MainActivity.service?.files()?.insert(file, media)?.execute()
                                nowRoots.last().children.add(ListItem(result!!.id, false, result.title, nowRoots.last(), ArrayList()))
                                Log.d("UPLOAD", "UPLOADED image to drive")
                            } catch (e: UserRecoverableAuthIOException) {
                                startActivityForResult(e.intent, REQUEST_AUTHORIZATION)
                            } catch (e: IOException) {
                                displaySyncError()
                            } catch (e: SocketTimeoutException) {
                                displayTimeOut()
                            } finally {
                                hideProgress()
                            }
                        }

                        override fun onPostExecute(result: Unit?) {
                            updateElements()
                        }
                    }).execute()
                    showProgress()
                })
                .setNegativeButton("Cancel", { _, _ ->

                }).show()
    }


    fun uploadByGallery() {
        val intent = Intent(Intent.ACTION_OPEN_DOCUMENT)
        intent.addCategory(Intent.CATEGORY_OPENABLE)
        intent.type = "image/*"
        startActivityForResult(intent, RESULT_PICK_IMAGEFILE)
    }

    fun getPathFromUri(uri : Uri) : String? {
       val isAfterKitKat = Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT
        // DocumentProvider
        Log.d("URI","uri:${uri.authority}")
        if (isAfterKitKat && DocumentsContract.isDocumentUri(this, uri)) {
            if ("com.android.externalstorage.documents" == uri.authority) {
                // ExternalStorageProvider
                val split = DocumentsContract.getDocumentId(uri).split(":")
                val type = split[0]
                if ("primary".equals(type, true)) {
                    return "${Environment.getExternalStorageDirectory()}/${split[1]}"
                }else {
                    return "/stroage/$type/${split[1]}"
                }
            }else if ("com.android.providers.downloads.documents" == uri.authority) {
                // DownloadsProvider
                val id = DocumentsContract.getDocumentId(uri)
                val contentUri = ContentUris.withAppendedId(Uri.parse("content://downloads/public_downloads"), id.toLong())
                return getDataColumn(contentUri, null, null)
            }else if ("com.android.providers.media.documents" == uri.authority) {
                // MediaProvider
                val split = DocumentsContract.getDocumentId(uri).split(":")
                return getDataColumn(MediaStore.Files.getContentUri("external"), "_id=?", Array(1, {split[1]}))
            }
        }else if ("content".equals(uri.scheme, true)) {
            //MediaStore
            return getDataColumn(uri, null, null)
        }else if ("file".equals(uri.scheme, true)) {
            // File
            return uri.path
        }
        return null
    }

    fun getDataColumn(uri : Uri, selection : String?, selectionArgs : Array<String>?) : String? {
        var cursor : Cursor? = null
        val projection = Array(1, {MediaStore.Files.FileColumns.DATA})
        try {
            cursor = contentResolver.query(uri, projection, selection, selectionArgs, null)
            if (cursor != null && cursor.moveToFirst()) {
                return cursor.getString(cursor.getColumnIndexOrThrow(projection[0]))
            }
        } finally {
            if (cursor != null)
                cursor.close()
        }
        return null
    }

    fun showProgress() {
        progress = ProgressDialog(this)
        progress?.setProgressStyle(ProgressDialog.STYLE_SPINNER)
        progress?.setMessage("処理を実行中です")
        progress?.setCancelable(true)
        progress?.show()
    }

    fun hideProgress() {
        if(progress != null) {
            progress?.dismiss()
            progress = null
        }
    }

    fun displaySyncError() = displayToast("Sync Error")
    fun displayTimeOut() = displayToast("Time Out")
    fun displayToast(msg : String) = Handler(application.mainLooper).post({ Toast.makeText(applicationContext, msg, Toast.LENGTH_SHORT).show() })
}
