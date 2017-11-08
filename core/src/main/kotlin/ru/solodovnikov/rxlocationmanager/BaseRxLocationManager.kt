package ru.solodovnikov.rxlocationmanager

import android.content.Context
import android.content.Intent
import android.location.*
import android.os.Build
import android.os.Bundle
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

    sealed class LocationEvent {
        data class LocationChangedEvent(val location: Location) : LocationEvent()
        data class ProviderEnableStatusEvent(val provider: String, val enabled: Boolean) : LocationEvent()
        data class StatusChangedEvent(val provider: String, val status: Int, val extras: Bundle?) : LocationEvent()
    }

    sealed class GnssStatusResponse {
        class GnssStarted : GnssStatusResponse()
        class GnssStopped : GnssStatusResponse()
        data class GnssSatelliteStatusChanged(val status: GnssStatus) : GnssStatusResponse()
        data class GnssFirstFix(val ttffMillis: Int) : GnssStatusResponse()
    }

    sealed class GnssMeasurementsResponse {
        data class GnssMeasurementsReceived(val eventArgs: GnssMeasurementsEvent) : GnssMeasurementsResponse()
        data class StatusChanged(val status: Int) : GnssMeasurementsResponse()
    }

    sealed class GnssNavigationResponse {
        data class GnssNavigationMessageReceived(val event: GnssNavigationMessage) : GnssNavigationResponse()
        data class StatusChanged(val status: Int) : GnssNavigationResponse()
    }

    data class NmeaMessage(var nmea: String, var timestamp: Long)
}