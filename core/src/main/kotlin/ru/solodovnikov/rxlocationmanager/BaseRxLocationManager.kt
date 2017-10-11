package ru.solodovnikov.rxlocationmanager

import android.content.Context
import android.content.Intent
import android.location.Location
import android.location.LocationManager
import android.os.Build
import android.os.SystemClock

abstract class BaseRxLocationManager(context: Context) {
    protected val locationManager: LocationManager = context.getSystemService(Context.LOCATION_SERVICE) as LocationManager

    /**
     * You need to call this method only if you use any [BasePermissionBehavior] implementations
     * @see android.app.Activity.onRequestPermissionsResult
     */
    abstract fun onRequestPermissionsResult(permissions: Array<out String>, grantResults: IntArray)

    abstract fun onActivityResult(resultCode: Int, data: Intent?)

    /**
     * Check is location not old
     * @param howOldCanBe how old the location can be
     * @return true if location is not so old as [howOldCanBe]
     */
    protected fun Location.isNotOld(howOldCanBe: LocationTime): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN_MR1) {
            SystemClock.elapsedRealtimeNanos() - elapsedRealtimeNanos < howOldCanBe.timeUnit.toNanos(howOldCanBe.time)
        } else {
            System.currentTimeMillis() - time < howOldCanBe.timeUnit.toMillis(howOldCanBe.time)
        }
    }

    data class NmeaData(var nmea: String, var timestamp: Long)
}