package ru.solodovnikov.rxlocationmanager.sample

import android.location.LocationManager
import android.os.Bundle
import android.support.v7.app.AppCompatActivity
import android.util.Log
import io.reactivex.Maybe
import io.reactivex.Single
import ru.solodovnikov.rxlocationmanager.LocationRequestBuilder
import ru.solodovnikov.rxlocationmanager.LocationTime
import ru.solodovnikov.rxlocationmanager.RxLocationManager
import java.util.concurrent.TimeUnit

class MainActivity : AppCompatActivity() {
    private val tag: String by lazy { javaClass.simpleName }

    private val rxLocationManager: RxLocationManager by lazy { RxLocationManager(this) }
    private val locationRequestBuilder: LocationRequestBuilder by lazy { LocationRequestBuilder(this) }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
    }

    private fun requestLastNetworkLocation() {
        rxLocationManager.getLastLocation(LocationManager.NETWORK_PROVIDER)
                .testSubscribe()
    }

    private fun requestLastNetworkOneMinuteOldLocation() {
        rxLocationManager.getLastLocation(LocationManager.NETWORK_PROVIDER, LocationTime(1, TimeUnit.MINUTES))
                .testSubscribe()
    }

    private fun requestLocation() {
        rxLocationManager.requestLocation(LocationManager.NETWORK_PROVIDER, LocationTime(15, TimeUnit.SECONDS))
                .testSubscribe()
    }

    private fun requestBuild() {
        locationRequestBuilder
                .addLastLocation(LocationManager.NETWORK_PROVIDER, LocationTime(30, TimeUnit.MINUTES))
                .addRequestLocation(LocationManager.NETWORK_PROVIDER, LocationTime(15, TimeUnit.SECONDS))
                .create()
                .testSubscribe()
    }

    fun <T> Maybe<T>.testSubscribe() {
        subscribe({ Log.d(tag, it?.toString() ?: "Empty location") },
                { Log.e(tag, "Error") },
                { Log.d(tag, "Complete") })
    }

    fun <T> Single<T>.testSubscribe() {
        subscribe({ Log.d(tag, it?.toString() ?: "Empty location") },
                { Log.d(tag, "Error") })
    }
}
