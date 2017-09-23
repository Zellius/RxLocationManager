package ru.solodovnikov.rxlocationmanager.sample

import android.location.Location
import android.location.LocationManager
import android.os.Bundle
import android.support.design.widget.CoordinatorLayout
import android.support.design.widget.Snackbar
import android.support.v4.app.ActivityCompat
import android.support.v7.app.AppCompatActivity
import android.view.Menu
import android.view.MenuItem
import ru.solodovnikov.rxlocationmanager.*
import rx.Single
import java.util.concurrent.TimeUnit

class MainActivity : AppCompatActivity(), BasePermissionTransformer.PermissionCallback {
    private val rxLocationManager: RxLocationManager by lazy { RxLocationManager(this) }
    private val locationRequestBuilder: LocationRequestBuilder by lazy { LocationRequestBuilder(rxLocationManager) }

    private val coordinatorLayout by lazy { findViewById<CoordinatorLayout>(R.id.root) }

    private var checkPermissions = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        setSupportActionBar(findViewById(R.id.toolbar))
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.main, menu)
        return super.onCreateOptionsMenu(menu)
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        when (item.itemId) {
            R.id.check_permissions -> {
                item.isChecked = !item.isChecked
                checkPermissions = item.isChecked
                return true
            }
            R.id.last_network -> {
                requestLastNetworkLocation()
                return true
            }
            R.id.last_network_minute_old -> {
                requestLastNetworkOneMinuteOldLocation()
                return true
            }
            R.id.request_location -> {
                requestLocation()
                return true
            }
            R.id.complicated_request_location -> {
                requestBuild()
                return true
            }
            R.id.complicated_request_location_ignore_error -> {
                requestBuildIgnoreSecurityError()
                return true
            }
            else -> {
                return super.onOptionsItemSelected(item)
            }
        }
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == REQUEST_CODE_LOCATION_PERMISSIONS) {
            rxLocationManager.onRequestPermissionsResult(permissions, grantResults)
        }
    }

    override fun requestPermissions(permissions: Array<String>) {
        ActivityCompat.requestPermissions(this, permissions, REQUEST_CODE_LOCATION_PERMISSIONS)
    }

    private fun requestLastNetworkLocation() {
        if (checkPermissions) {
            rxLocationManager.getLastLocation(LocationManager.NETWORK_PROVIDER, transformers = PermissionTransformer(this, rxLocationManager, this))
        } else {
            rxLocationManager.getLastLocation(LocationManager.NETWORK_PROVIDER)
        }.testSubscribe("requestLastNetworkLocation")
    }

    private fun requestLastNetworkOneMinuteOldLocation() {
        if (checkPermissions) {
            rxLocationManager.getLastLocation(LocationManager.NETWORK_PROVIDER, LocationTime(1, TimeUnit.MINUTES), PermissionTransformer(this, rxLocationManager, this))
        } else {
            rxLocationManager.getLastLocation(LocationManager.NETWORK_PROVIDER, LocationTime(1, TimeUnit.MINUTES))
        }.testSubscribe("requestLastNetworkOneMinuteOldLocation")
    }

    private fun requestLocation() {
        if (checkPermissions) {
            rxLocationManager.requestLocation(LocationManager.NETWORK_PROVIDER, LocationTime(15, TimeUnit.SECONDS), PermissionTransformer(this, rxLocationManager, this))
        } else {
            rxLocationManager.requestLocation(LocationManager.NETWORK_PROVIDER, LocationTime(15, TimeUnit.SECONDS))
        }.testSubscribe("requestLocation")
    }

    private fun requestBuild() {
        if (checkPermissions) {
            val permissionTransformer = PermissionTransformer(this, rxLocationManager, this)
            locationRequestBuilder
                    .addLastLocation(LocationManager.NETWORK_PROVIDER, LocationTime(30, TimeUnit.MINUTES), transformers = permissionTransformer)
                    .addRequestLocation(LocationManager.NETWORK_PROVIDER, LocationTime(15, TimeUnit.SECONDS), transformers = permissionTransformer)
                    .setDefaultLocation(Location(LocationManager.PASSIVE_PROVIDER))
                    .create()
        } else {
            locationRequestBuilder
                    .addLastLocation(LocationManager.NETWORK_PROVIDER, LocationTime(30, TimeUnit.MINUTES))
                    .addRequestLocation(LocationManager.NETWORK_PROVIDER, LocationTime(15, TimeUnit.SECONDS))
                    .setDefaultLocation(Location(LocationManager.PASSIVE_PROVIDER))
                    .create()
        }.testSubscribe("requestBuild")
    }

    private fun requestBuildIgnoreSecurityError() {
        val ignoreError = IgnoreErrorTransformer(SecurityException::class.java)

        locationRequestBuilder
                .addLastLocation(LocationManager.NETWORK_PROVIDER, LocationTime(30, TimeUnit.MINUTES), transformers = ignoreError)
                .addRequestLocation(LocationManager.NETWORK_PROVIDER, LocationTime(15, TimeUnit.SECONDS), transformers = ignoreError)
                .setDefaultLocation(Location(LocationManager.PASSIVE_PROVIDER))
                .create()
                .testSubscribe("requestBuild")
    }

    private fun showSnackbar(text: CharSequence) {
        Snackbar.make(coordinatorLayout, text, Snackbar.LENGTH_SHORT)
                .show()
    }

    fun <T> Single<T>.testSubscribe(methodName: String) {
        subscribe({ showSnackbar("$methodName Success: ${it?.toString() ?: "Empty location"}") },
                { showSnackbar("$methodName Error: ${it.message}") })
    }

    companion object {
        private const val REQUEST_CODE_LOCATION_PERMISSIONS = 150
    }
}
