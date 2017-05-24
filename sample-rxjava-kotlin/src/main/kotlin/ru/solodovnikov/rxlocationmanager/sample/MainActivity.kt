package ru.solodovnikov.rxlocationmanager.sample

import android.location.Location
import android.location.LocationManager
import android.os.Bundle
import android.support.design.widget.CoordinatorLayout
import android.support.design.widget.Snackbar
import android.support.v7.app.AppCompatActivity
import android.support.v7.widget.Toolbar
import android.view.Menu
import android.view.MenuItem
import ru.solodovnikov.rxlocationmanager.IgnoreErrorTransformer
import ru.solodovnikov.rxlocationmanager.LocationRequestBuilder
import ru.solodovnikov.rxlocationmanager.LocationTime
import ru.solodovnikov.rxlocationmanager.RxLocationManager
import rx.Single
import java.util.concurrent.TimeUnit

class MainActivity : AppCompatActivity() {
    private val rxLocationManager: RxLocationManager by lazy { RxLocationManager(this) }
    private val locationRequestBuilder: LocationRequestBuilder by lazy { LocationRequestBuilder(this) }

    private val coordinatorLayout: CoordinatorLayout by lazy { findViewById(R.id.root) as CoordinatorLayout }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        setSupportActionBar(findViewById(R.id.toolbar) as Toolbar)
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.main, menu)
        return super.onCreateOptionsMenu(menu)
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        when (item.itemId) {
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

    private fun requestLastNetworkLocation() {
        rxLocationManager.getLastLocation(LocationManager.NETWORK_PROVIDER)
                .testSubscribe("requestLastNetworkLocation")
    }

    private fun requestLastNetworkOneMinuteOldLocation() {
        rxLocationManager.getLastLocation(LocationManager.NETWORK_PROVIDER, LocationTime(1, TimeUnit.MINUTES))
                .testSubscribe("requestLastNetworkOneMinuteOldLocation")
    }

    private fun requestLocation() {
        rxLocationManager.requestLocation(LocationManager.NETWORK_PROVIDER, LocationTime(15, TimeUnit.SECONDS))
                .testSubscribe("requestLocation")
    }

    private fun requestBuild() {
        locationRequestBuilder
                .addLastLocation(LocationManager.NETWORK_PROVIDER, LocationTime(30, TimeUnit.MINUTES))
                .addRequestLocation(LocationManager.NETWORK_PROVIDER, LocationTime(15, TimeUnit.SECONDS))
                .setDefaultLocation(Location(LocationManager.PASSIVE_PROVIDER))
                .create()
                .testSubscribe("requestBuild")
    }

    private fun requestBuildIgnoreSecurityError() {
        val ignoreError = IgnoreErrorTransformer(listOf(SecurityException::class.java))

        locationRequestBuilder
                .addLastLocation(LocationManager.NETWORK_PROVIDER, LocationTime(30, TimeUnit.MINUTES), ignoreError)
                .addRequestLocation(LocationManager.NETWORK_PROVIDER, LocationTime(15, TimeUnit.SECONDS), ignoreError)
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
}
