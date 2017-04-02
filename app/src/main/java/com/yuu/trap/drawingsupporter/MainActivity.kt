package com.yuu.trap.drawingsupporter

import android.Manifest
import android.accounts.AccountManager
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.BitmapFactory
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
import com.google.android.gms.common.GoogleApiAvailability
import com.google.android.gms.common.api.GoogleApiClient
import com.google.android.gms.drive.*
import com.google.api.client.extensions.android.http.AndroidHttp
import com.google.api.client.googleapis.extensions.android.gms.auth.GoogleAccountCredential
import com.google.api.client.googleapis.extensions.android.gms.auth.UserRecoverableAuthIOException
import com.google.api.client.json.gson.GsonFactory
import java.io.ByteArrayOutputStream
import java.net.SocketTimeoutException
import java.util.*
import kotlin.collections.ArrayList
import kotlin.reflect.KClass

/**
 * 最初の画面処理を担当するクラス
 * @author yuu
 * @since 2017/03/30
 *
 * @property client グーグルドライブ使用のためのAPIクライアント
 * @property syncRoot グーグルドライブのフォルダ同期のルート
 */
class MainActivity : AppCompatActivity(), NavigationView.OnNavigationItemSelectedListener {
    private val RESOLVE_CONNECTION_REQUEST_CODE = 1
    private val OPEN_FILE_ACTIVITY_CODE = 2
    private val REQUEST_ACCOUNT = 3
    private val MY_PERMISSIONS_REQUEST_READ_CONTACTS = 4
    private val REQUEST_AUTHORIZATION = 5

    companion object {
        var client : GoogleApiClient? = null
        var credential : GoogleAccountCredential? = null
        var service : com.google.api.services.drive.Drive? = null
    }

    private val remainTask = ArrayList<Boolean>()
    private var syncRoot : ListItem? = null
    private var nowRoot : ListItem? = null
    private var adapter : ImageArrayAdapter? = null

    private var userName : String? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        //初期情報取得
        val pref = getPreferences(Context.MODE_PRIVATE)
        userName = pref.getString("userName", null)

        //クライアント作成
        checkPermission()
        if(userName != null) {
            credential = GoogleAccountCredential.usingOAuth2(this, Arrays.asList("https://www.googleapis.com/auth/drive"))
            credential?.selectedAccountName = userName
            service = com.google.api.services.drive.Drive.Builder(AndroidHttp.newCompatibleTransport(), GsonFactory(), credential).build()
        }
        Log.d("REQUEST", "ACCOUNT NAME ${credential?.selectedAccountName}")
        if(credential?.selectedAccountName == null) {
            credential = GoogleAccountCredential.usingOAuth2(this, Arrays.asList("https://www.googleapis.com/auth/drive"))
            startActivityForResult(credential?.newChooseAccountIntent(), REQUEST_ACCOUNT)
        }

        //クライアント作成
        client = GoogleApiClient.Builder(this).addApi(Drive.API).addScope(Drive.SCOPE_FILE)
                .addConnectionCallbacks(object : GoogleApiClient.ConnectionCallbacks {
                    override fun onConnected(p0: Bundle?) {
                    }
                    override fun onConnectionSuspended(p0: Int) {
                    }
                }).addOnConnectionFailedListener {
                    if(it.hasResolution())
                        it.startResolutionForResult(this, RESOLVE_CONNECTION_REQUEST_CODE)
                    else
                        GoogleApiAvailability.getInstance().getErrorDialog(this, it.errorCode, 0).show()
                }.build()

        //画面構成をセット
        setContentView(R.layout.activity_main)

        //ツールバーを設定
        val toolbar = findViewById(R.id.toolbar) as Toolbar
        setSupportActionBar(toolbar)

        //ボタンを設定
        val fab = findViewById(R.id.fab) as FloatingActionButton
        fab.setOnClickListener {
            Log.d("BUTTON", "${nowRoot?.title} -> ${nowRoot?.parent?.title}")
            if(nowRoot?.parent != null) {
                addElements(nowRoot?.parent!!)
            }
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
        list.setOnItemClickListener { adapterView, view, i, l ->
            val item = (adapterView as ListView).getItemAtPosition(i) as ListItem
            if(item.isFolder)
                addElements(item)
            else
                showImage(item.id)
        }
        unparseListItem()
    }

    override fun onStart() {
        super.onStart()
        client?.connect()
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        when(requestCode) {
            RESOLVE_CONNECTION_REQUEST_CODE -> {
                if(resultCode == Activity.RESULT_OK)
                    client?.connect()
            }
            OPEN_FILE_ACTIVITY_CODE -> Log.d("test", "$requestCode")
            REQUEST_ACCOUNT -> {
                if(resultCode == Activity.RESULT_OK && data != null && data.extras != null) {
                    val name = data.getStringExtra(AccountManager.KEY_ACCOUNT_NAME)
                    if(name != null) {
                        credential?.selectedAccountName = name
                        service = com.google.api.services.drive.Drive.Builder(AndroidHttp.newCompatibleTransport(), GsonFactory(), credential).build()
                        Log.d("REQUEST", "ACCOUNT NAME ${credential?.selectedAccountName}")
                        val pref = getPreferences(Context.MODE_PRIVATE)
                        val e = pref.edit()
                        e.putString("userName", credential?.selectedAccountName)
                        e.commit()
                    }
                }
            }
        }
    }

    fun checkPermission() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.GET_ACCOUNTS) != PackageManager.PERMISSION_GRANTED) {
            if (ActivityCompat.shouldShowRequestPermissionRationale(this, Manifest.permission.GET_ACCOUNTS)) {
            } else {
                ActivityCompat.requestPermissions(this, Array(1, {Manifest.permission.GET_ACCOUNTS}), MY_PERMISSIONS_REQUEST_READ_CONTACTS);
            }
        }
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.INTERNET) != PackageManager.PERMISSION_GRANTED) {
            if (ActivityCompat.shouldShowRequestPermissionRationale(this, Manifest.permission.INTERNET)) {
            } else {
                ActivityCompat.requestPermissions(this, Array(1, {Manifest.permission.INTERNET}), MY_PERMISSIONS_REQUEST_READ_CONTACTS);
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
        // Inflate the menu; this adds items to the action bar if it is present.
        menuInflater.inflate(R.menu.main, menu)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        // Handle action bar item clicks here. The action bar will
        // automatically handle clicks on the Home/Up button, so long
        // as you specify a parent activity in AndroidManifest.xml.
        when(item.itemId) {
            R.id.action_settings -> return true
            R.id.action_sync -> { syncDrive(true); return true }
            R.id.action_user -> {
                credential = GoogleAccountCredential.usingOAuth2(this, Arrays.asList("https://www.googleapis.com/auth/drive"))
                startActivityForResult(credential?.newChooseAccountIntent(), REQUEST_ACCOUNT)
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

    fun syncDrive(enbale : Boolean) {
        searchDrawingSupporter()
    }

    fun searchDrawingSupporter() {
        (object : AsyncTask<Void, Void, Unit>() {
            override fun doInBackground(vararg params: Void?){
                var token : String? = null
                try {
                    do {
                        val result = service?.files()?.list()
                                ?.setQ("title = 'DrawingSupporter.jar'")
                                ?.setPageToken(token)
                                ?.execute()
                        Log.d("RESULT", "$result")
                        result?.items?.filterNot { it.title.startsWith('.') }?.forEach {
                            Log.d("RESULT", it.title)
                            syncRoot = ListItem(it.id, it.mimeType == "application/vnd.google-apps.folder", it.title, null, ArrayList())
                            expandFolder(it.parents.first().id, syncRoot!!)
                        }
                        Log.d("RESULT", "Ended")
                        token = result?.nextPageToken
                    } while(token != null)
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
        nowRoot = root
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

    fun showImage(id : String) {
        val intent = Intent(application, ImageActivity::class.java)
        intent.putExtra("Image", id)
        startActivity(intent)
    }

}
