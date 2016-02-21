package ru.solodovnikov.rxlocationmanager.kotlin.sample

import android.location.Location
import android.location.LocationManager
import android.os.Bundle
import android.support.v7.app.AppCompatActivity
import android.util.Log
import ru.solodovnikov.rxlocationmanager.kotlin.LocationRequestBuilder
import ru.solodovnikov.rxlocationmanager.kotlin.LocationTime
import rx.Subscriber
import java.util.concurrent.TimeUnit


class MainActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val locationRequestBuilder = LocationRequestBuilder(this)

        locationRequestBuilder.addLastLocation(LocationManager.NETWORK_PROVIDER, LocationTime(30, TimeUnit.SECONDS), false)
                .addRequestLocation(LocationManager.GPS_PROVIDER, LocationTime(10, TimeUnit.SECONDS))
                .setDefaultLocation(Location(LocationManager.PASSIVE_PROVIDER))
                .create()
                .subscribe(
                        object : Subscriber<Location>() {
                            override fun onCompleted() {

                            }

                            override fun onNext(location: Location?) {
                                Log.d(javaClass.simpleName, location.toString())
                            }

                            override fun onError(p0: Throwable?) {
                                Log.d(javaClass.simpleName, p0!!.message)
                            }
                        })
    }
}