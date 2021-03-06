package com.example.idan.lightmeup

import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.content.res.Resources
import android.graphics.Color
import android.location.Location
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.support.v4.app.ActivityCompat
import android.support.v7.app.AppCompatActivity
import android.util.Log
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import android.widget.Switch
import android.widget.Toast
import com.arsy.maps_library.MapRadar
import com.beust.klaxon.Klaxon
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.AdView
import com.google.android.gms.ads.MobileAds
import com.google.android.gms.auth.api.signin.GoogleSignInAccount
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationServices
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.GoogleMap
import com.google.android.gms.maps.OnMapReadyCallback
import com.google.android.gms.maps.SupportMapFragment
import com.google.android.gms.maps.model.*
import okhttp3.*
import java.io.File
import java.io.IOException


class MapsActivity : AppCompatActivity(), OnMapReadyCallback, GoogleMap.OnMarkerClickListener, GoogleMap.OnInfoWindowClickListener {
    override fun onMarkerClick(p0: Marker?) = false

    private lateinit var map: GoogleMap
    private lateinit var fusedLocationClient: FusedLocationProviderClient
    private var backPressed: Long = 0
    lateinit var mAdView : AdView
    val ipAddress: String = "192.168.43.135"
    private lateinit var lastLocation: Location
    var listOfMarkers = mutableMapOf<String, Marker>()
    var lightersLatLngList = mutableMapOf<String, LatLng>()
    var lightersNamesList = mutableMapOf<String, String>()
    var lightersTimeList = mutableMapOf<String, String>()
    var lightersPhoneList = mutableMapOf<String, String>()
    var isCameraMove = false
    lateinit var mapRadar : MapRadar
    val mHandler3 = Handler()
    val mHandler = Handler(Looper.getMainLooper())
    var switchHaveValue: Boolean = false
    lateinit var googleAccount: GoogleSignInAccount
    var isRunInBackground : Boolean = false
    var isMenuLoaded = false

    lateinit var phoneNum : String

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_map)

        initializeSettings()

        setSupportActionBar(findViewById(R.id.my_toolbar))

        googleAccount = intent.getParcelableExtra("googleAccount")
        supportActionBar!!.setDisplayShowTitleEnabled(false)

        phoneNum = intent.getStringExtra("phoneNum")

        initializeProfile()

        MobileAds.initialize(this, "ca-app-pub-3096868502930398~4354694161")
        mAdView = findViewById(R.id.adView)
        val adRequest = AdRequest.Builder().build()
        mAdView.loadAd(adRequest)

        val mapFragment = supportFragmentManager
                .findFragmentById(R.id.map) as SupportMapFragment
        mapFragment.getMapAsync(this)

        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this)

        val switchView = findViewById<Switch>(R.id.switchHave)
        switchView.setOnClickListener(View.OnClickListener {
            switchHaveValue = switchView.isChecked
        })

    }

    override fun onMapReady(googleMap: GoogleMap) {
        map = googleMap

        try {
            // Customise the styling of the base map using a JSON object defined
            // in a raw resource file.
            val success : Boolean = map.setMapStyle(
                    MapStyleOptions.loadRawResourceStyle(
                            this, R.raw.style_json))
        } catch (e : Resources.NotFoundException) {
            Log.e("MapsActivityRaw", "Can't find style.", e)
        }

        map.getUiSettings().setZoomControlsEnabled(true)
        map.setOnMarkerClickListener(this)
        map.setOnInfoWindowClickListener(this)

        mapRadar = MapRadar(map, LatLng(0.0, 0.0), this)
        mapRadar.withDistance(800)
        mapRadar.withOuterCircleStrokeColor(0xfccd29)
        mapRadar.withRadarColors(0x00fccd29, Color.parseColor("#EB3737"))
        mapRadar.startRadarAnimation()

        setUpMap()

        lateinit var runS : Runnable
        runS = Runnable {
            run() {
                setUpMap()
                showingOtherLighters()
                if(true){
                    mHandler3.postDelayed(runS, 5000)
                }
            }
        }
        mHandler3.post(runS)
    }

    private fun setUpMap() {
        if (ActivityCompat.checkSelfPermission(this,
                        android.Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this,
                    arrayOf(android.Manifest.permission.ACCESS_FINE_LOCATION), LOCATION_PERMISSION_REQUEST_CODE)
            return
        }
        map.isMyLocationEnabled = true
        fusedLocationClient.lastLocation.addOnSuccessListener(this) { location ->
            if (location != null) {
                lastLocation = location
                val currentLatLng = LatLng(location.latitude, location.longitude)
                if(!isCameraMove) {
                    map.animateCamera(CameraUpdateFactory.newLatLngZoom(currentLatLng, 16f))
                    isCameraMove = true
                }

                val client = OkHttpClient()

                val port = "5000"
                val route = "/my_location"
                val url = "http://" + ipAddress + ":" + port + route
                val currentLat: Double = currentLatLng.latitude
                val currentLng: Double = currentLatLng.longitude

                val json = """
                    {"lat":${currentLat},"lng":${currentLng},"googleAccountId":"${googleAccount.id}","hasLighter":${switchHaveValue}
                    }""".trimIndent()
                val body = RequestBody.create(MediaType.parse("application/json; charset=utf-8"), json)
                val request = Request.Builder()
                        .url(url)
                        .post(body)
                        .build()

                client.newCall(request).enqueue(object : Callback {
                    override fun onFailure(call: Call, e: IOException) {
                    }

                    override fun onResponse(call: Call, response: Response) {
                        if (response.isSuccessful()) {
                        }
                    }
                })
                client.dispatcher().executorService().shutdown()

                mapRadar.withLatLng(currentLatLng)
            }
        }
    }

    override fun onBackPressed() {
        if (backPressed + 2000 > System.currentTimeMillis()) {
            val intentMain: Intent = Intent(applicationContext, MainActivity::class.java)
            intentMain.flags = Intent.FLAG_ACTIVITY_CLEAR_TOP
            intentMain.putExtra("EXIT", true)

            mHandler3.removeCallbacksAndMessages(null)
            mHandler.removeCallbacksAndMessages(null)

            startActivity(intentMain)
        }
        else {
            Toast.makeText(baseContext, "Press once again to exit", Toast.LENGTH_SHORT).show()
        }
        backPressed = System.currentTimeMillis()
    }

    override fun onOptionsItemSelected(item: MenuItem) = when (item.itemId) {
        R.id.action_settings -> {
            val intentSettings = Intent(this, SettingsActivity::class.java)
            intentSettings.putExtra("googleAccount", googleAccount)
            startActivityForResult(intentSettings, 1)
            true
        }

        R.id.action_info -> {
            val intentInfo = Intent(this, InfoActivity::class.java)
            intentInfo.putExtra("googleAccount", googleAccount)
            startActivity(intentInfo)
            true
        }

//        R.id.action_profile -> {
//            val intentProfile = Intent(this, ProfileActivity::class.java)
//            intentProfile.putExtra("googleAccount", googleAccount)
//            startActivity(intentProfile)
//            true
//        }

        else -> {
            super.onOptionsItemSelected(item)
        }
    }

    override fun onPrepareOptionsMenu(menu: Menu): Boolean {
        if ( ! isMenuLoaded){
            menuInflater.inflate(R.menu.menu_map, menu)
            isMenuLoaded = true
            return super.onCreateOptionsMenu(menu)
        }
        return true
    }

    fun showingOtherLighters() {
        val listMarkersToDelete = mutableListOf<String>()

        val mMapView: ViewGroup = findViewById(R.id.mapLayout);
        val client = OkHttpClient()

        val port = "5000"
        val route = "/get_lighters_latlng?googleAccountId=" + googleAccount.id
        val url = "http://" + ipAddress + ":" + port + route

        val request = Request.Builder()
                .url(url)
                .build()

        client.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
            }

            override fun onResponse(call: Call, response: Response) {
                if (response.isSuccessful()) {
                    val json1 = response.body()!!.string()
                    lightersLatLngList.clear()
                    class DataSon(val lat: Double, val lng: Double, val user_id: String, val name: String, val time: String, val phone: String)
                    class Data(val lighters_latlng: Array<DataSon>)
                    val json2 = Klaxon().parse<Data>(json1)
                    for (item2 in json2!!.lighters_latlng) {
                        val lat: Double = item2.lat
                        val lng: Double = item2.lng
                        val userId: String = item2.user_id
                        val latlng = LatLng(lat, lng)
                        lightersLatLngList[userId] = latlng
                        lightersNamesList[userId] = item2.name
                        lightersTimeList[userId] = item2.time
                        lightersPhoneList[userId] = item2.phone
                    }

                    mHandler.post(Runnable() {
                        run() {
                            for (latlng in lightersLatLngList) {
                                if (!listOfMarkers.containsKey(latlng.key)) {
                                    val marker: Marker =  map.addMarker(MarkerOptions()
                                            .position(latlng.value)
                                            .title(lightersNamesList[latlng.key])
                                            .snippet("Last seen: " + lightersTimeList[latlng.key] + ", Phone number: +" + lightersPhoneList[latlng.key])
                                            .icon(BitmapDescriptorFactory.fromResource(R.drawable.lighter)))
                                    listOfMarkers[latlng.key] = marker
                                }
                                else {
                                if (listOfMarkers[latlng.key]!!.position != latlng.value) {
                                    listOfMarkers[latlng.key]!!.remove()
                                    val marker: Marker =  map.addMarker(MarkerOptions()
                                            .position(latlng.value)
                                            .title(lightersNamesList[latlng.key])
                                            .snippet("Last seen: " + lightersTimeList[latlng.key] + ", Phone number: +" + lightersPhoneList[latlng.key])
                                            .icon(BitmapDescriptorFactory.fromResource(R.drawable.lighter)))
                                    listOfMarkers[latlng.key] = marker
                                    }

                                }
                            }
                            listMarkersToDelete.clear()
                            for (marker in listOfMarkers) {
                                if (marker.key !in lightersLatLngList.keys) {
                                    marker.value.remove()
                                    listMarkersToDelete.add(marker.key)
                                }
                            }
                            for (marker_key in listMarkersToDelete) {
                                listOfMarkers.remove(marker_key)
                            }
                            mMapView.invalidate()
                        }
                    })
                }
            }
        })
        client.dispatcher().executorService().shutdown()
    }

    fun initializeSettings() {
        val file = File(applicationContext.filesDir, "conf.txt")
        if (!file.exists()) {
            file.createNewFile()
        }
        val confText = file.readText()
        if (confText.length == 0){
            file.printWriter().use { out ->
                out.print("{\"is_run_in_background\":false}")
            }
            isRunInBackground = false
        }
        else {
            class ConfData(var is_run_in_background: Boolean)
            val confObj = Klaxon().parse<ConfData>(confText)
            if (confObj != null) {
                isRunInBackground = confObj.is_run_in_background
            }
        }
    }

    fun initializeProfile() {
        val client = OkHttpClient()

        val port = "5000"
        val route = "/initialize_profile"
        val url = "http://" + ipAddress + ":" + port + route

        val json = """
                    {"phoneNum":"${phoneNum}","isRunInBackground":${isRunInBackground},"googleAccountId":"${googleAccount.id}","googleAccountName":"${googleAccount.displayName}"
                    }""".trimIndent()
        val body = RequestBody.create(MediaType.parse("application/json; charset=utf-8"), json)
        val request = Request.Builder()
                .url(url)
                .post(body)
                .build()

        client.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
            }

            override fun onResponse(call: Call, response: Response) {
                if (response.isSuccessful()) {
                }
            }
        })
        client.dispatcher().executorService().shutdown()
    }

    override fun onInfoWindowClick(marker: Marker) {
        val phoneNumberTo = marker.snippet.split("Phone number: +")[1]
        val uri = Uri.parse("smsto:${phoneNumberTo}")
        val i = Intent(Intent.ACTION_SENDTO, uri)
        i.`package` = "com.whatsapp"
        startActivity(Intent.createChooser(i, ""))
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == 1) {
            if (resultCode == Activity.RESULT_OK) {
                if (data != null) {
                    phoneNum = data.getStringExtra("myPhone")
                    isRunInBackground = data.getBooleanExtra("isRunInBackground", false)
                    initializeProfile()
                }
            }
        }
    }

    companion object {
        private const val LOCATION_PERMISSION_REQUEST_CODE = 1
    }
}