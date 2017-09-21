package ru.solodovnikov.rxlocationmanager

import android.content.Context
import android.location.Location
import android.location.LocationManager
import android.os.Build
import android.os.SystemClock
import java.util.concurrent.TimeoutException

/**
 * Abstract class used just to implement rxJava1 and rxJava2
 */
abstract class BaseRxLocationManager<out SINGLE, out MAYBE, in SINGLE_TRANSFORMER, in MAYBE_TRANSFORMER>(context: Context) {
    protected val locationManager: LocationManager = context.getSystemService(Context.LOCATION_SERVICE) as LocationManager

    /**
     * Get last location from specific provider
     * Observable will emit [ElderLocationException] if [howOldCanBe] is not null and location time is not valid.
     *
     * @param provider provider name
     * @param howOldCanBe how old a location can be
     * @param transformers extra transformers
     * @return observable that emit last known location
     * @see ElderLocationException
     * @see ProviderHasNoLastLocationException
     */
    @JvmOverloads
    @SuppressWarnings
    fun getLastLocation(provider: String,
                        howOldCanBe: LocationTime? = null,
                        vararg transformers: MAYBE_TRANSFORMER): MAYBE =
            baseGetLastLocation(provider, howOldCanBe, transformers)

    /**
     * Try to get current location by specific provider.
     * Observable will emit [TimeoutException] if [timeOut] is not null and timeOut occurs.
     * Observable will emit [ProviderDisabledException] if provider is disabled
     *
     * @param provider provider name
     * @param timeOut  request timeout
     * @param transformers extra transformers
     * @return observable that emit current location
     * @see TimeoutException
     * @see ProviderDisabledException
     */
    @JvmOverloads
    fun requestLocation(provider: String,
                        timeOut: LocationTime? = null,
                        vararg transformers: SINGLE_TRANSFORMER): SINGLE
            = baseRequestLocation(provider, timeOut, transformers)

    /**
     * You need to call this method only if you use any [BasePermissionTransformer] implementations
     * @see android.app.Activity.onRequestPermissionsResult
     */
    abstract fun onRequestPermissionsResult(permissions: Array<out String>, grantResults: IntArray)

    protected abstract fun baseGetLastLocation(provider: String, howOldCanBe: LocationTime?, transformers: Array<out MAYBE_TRANSFORMER>): MAYBE

    protected abstract fun baseRequestLocation(provider: String, timeOut: LocationTime?, transformers: Array<out SINGLE_TRANSFORMER>): SINGLE

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
}