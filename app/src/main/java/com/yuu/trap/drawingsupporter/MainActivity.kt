package com.yuu.trap.drawingsupporter

import android.Manifest
import android.accounts.AccountManager
import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.os.AsyncTask
import android.os.Bundle
import android.support.design.widget.FloatingActionButton
import android.support.design.widget.Snackbar
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
import com.google.android.gms.auth.GoogleAuthUtil
import com.google.android.gms.common.GoogleApiAvailability
import com.google.android.gms.common.api.GoogleApiClient
import com.google.android.gms.drive.*
import com.google.android.gms.drive.query.Filters
import com.google.android.gms.drive.query.Query
import com.google.android.gms.drive.query.SearchableField
import com.google.api.client.extensions.android.http.AndroidHttp
import com.google.api.client.googleapis.extensions.android.gms.auth.GoogleAccountCredential
import com.google.api.client.googleapis.extensions.android.gms.auth.UserRecoverableAuthIOException
import com.google.api.client.json.gson.GsonFactory
import java.util.*
import kotlin.collections.ArrayList

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

    private var client : GoogleApiClient? = null
    private var credential : GoogleAccountCredential? = null
        private var service : com.google.api.services.drive.Drive? = null
    private var syncRoot : DriveFolder? = null
    private var adapter : ImageArrayAdapter? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        //クライアント作成
        checkPermission()
        credential = GoogleAccountCredential.usingOAuth2(this, Arrays.asList("https://www.googleapis.com/auth/drive"))
        startActivityForResult(credential?.newChooseAccountIntent(), REQUEST_ACCOUNT)

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
        fab.setOnClickListener { view -> Snackbar.make(view, "Replace with your own action", Snackbar.LENGTH_LONG).setAction("Action", null).show() }

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
            val item = (adapterView as ListView).selectedItem as ListItem
            if(!item.isFile)
                addElements(item.id.asDriveFolder())
        }
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
                        result?.items?.forEach {

                            Log.d("RESULT", it.title)
                        }
                        token = result?.nextPageToken
                    } while(token != null)
                } catch (e: UserRecoverableAuthIOException) {
                    startActivityForResult(e.intent, REQUEST_AUTHORIZATION);
                }
            }
        }).execute()
        if(client?.isConnected ?: false) {
            /*
            val query = Query.Builder().addFilter(Filters.contains(SearchableField.TITLE, "00-p-start.JPG")).build()
            Log.d("Root", "$query")
            Drive.DriveApi.query(client, query).setResultCallback {
                it.metadataBuffer.forEach {
                    Log.d("Root", "${it.title}")
                }
                if(it.metadataBuffer.firstOrNull()?.isFolder ?: false)
                    syncRoot = it.metadataBuffer.firstOrNull()?.driveId?.asDriveFolder()
                if(syncRoot != null)
                    addElements(syncRoot!!)
                Log.d("Root", "$syncRoot")
                /*
                val file = it.metadataBuffer.filterNot { it.isFolder }.first()
                Log.d("Root", "$file -> ${file.title} -> ${file.driveId}")
                adapter?.add(ListItem(R.mipmap.ic_image, file.title, true, file.driveId))
                file.driveId.asDriveResource().listParents(client).setResultCallback {
                    it.metadataBuffer.forEach { Log.d("Root", "List1 $it") }
                }
                it.metadataBuffer.filterNot { it.isFolder }.first().driveId.asDriveFile().listParents(client).setResultCallback {
                    it.metadataBuffer.forEach { Log.d("Root", "List2 $it") }
                    Log.d("Root", "${it.metadataBuffer}")
                    syncRoot = it.metadataBuffer.firstOrNull()?.driveId?.asDriveFolder()
                    if(syncRoot != null)
                        addElements(syncRoot!!)
                    Log.d("Root", "$syncRoot")
                    it.release()
                }
                */
                it.release()
            }
            */
        }
    }

    fun addElements(root : DriveFolder) {
        adapter?.clear()
        if(client?.isConnected ?: false) {
            root.listChildren(client).setResultCallback {
                it.metadataBuffer.forEach {
                    adapter?.add(ListItem(if(it.isFolder) R.mipmap.ic_folder else R.mipmap.ic_image, it.title, !it.isFolder, it.driveId))
                }
                adapter?.notifyDataSetChanged()
            }
        }
    }
}
