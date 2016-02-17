package ru.solodovnikov.rxlocationmanager.kotlin

import android.annotation.TargetApi
import android.content.Context
import android.location.Location
import android.location.LocationManager
import android.os.Build
import android.os.SystemClock
import rx.Observable
import rx.android.schedulers.AndroidSchedulers
import java.util.concurrent.TimeUnit

class RxLocationManager internal constructor(private val locationManager: LocationManager) {
    constructor(context: Context) : this(context.getSystemService(Context.LOCATION_SERVICE) as LocationManager)

    /**
     * Get last location from specific provider
     * Observable will emit ElderLocationException if {@code howOldCanBe} is not null and location time is not valid.
     *
     * @param provider    provider name
     * @param howOldCanBe optional. How old a location can be
     * @return observable that emit last known location. May emit null.
     * @see ElderLocationException
     */
    fun getLastLocation(provider: String, howOldCanBe: LocationTime?) =
            Observable.create<Location>
            {
                it.onNext(locationManager.getLastKnownLocation(provider))
                it.onCompleted()
            }.compose {
                if (howOldCanBe == null) it else it.map {
                    if (it != null && !isLocationNotOld(it, howOldCanBe)) {
                        throw ElderLocationException(it)
                    }

                    it
                }
            }.compose(applySchedulers())

    private fun applySchedulers() = Observable.Transformer<Location, Location> { it.subscribeOn(AndroidSchedulers.mainThread()) }

    private fun isLocationNotOld(location: Location, howOldCanBe: LocationTime) =
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN_MR1) {
                isLocationNotOld17(location, howOldCanBe)
            } else {
                isLocationNotOldDefault(location, howOldCanBe)
            }

    @TargetApi(Build.VERSION_CODES.JELLY_BEAN_MR1)
    private fun isLocationNotOld17(location: Location, howOldCanBe: LocationTime) =
            SystemClock.elapsedRealtimeNanos() - location.elapsedRealtimeNanos < howOldCanBe.timeUnit.toNanos(howOldCanBe.time);

    private fun isLocationNotOldDefault(location: Location, howOldCanBe: LocationTime) =
            System.currentTimeMillis() - location.time < howOldCanBe.timeUnit.toMillis(howOldCanBe.time)

}

data class LocationTime(val time: Long, val timeUnit: TimeUnit) {
    companion object {
        fun OneDay(): LocationTime = LocationTime(1L, TimeUnit.DAYS)
        fun OneHour(): LocationTime = LocationTime(1L, TimeUnit.HOURS)
    }
}
