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
abstract class BaseRxLocationManager<SINGLE, MAYBE>(context: Context) {
    internal val context: Context = context.applicationContext
    protected val locationManager: LocationManager = context.getSystemService(Context.LOCATION_SERVICE) as LocationManager

    /**
     * Get last location from specific provider
     * Observable will emit [ElderLocationException] if [howOldCanBe] is not null and location time is not valid.
     *
     * @param provider provider name
     * @param howOldCanBe how old a location can be
     * @return observable that emit last known location
     * @see ElderLocationException
     * @see ProviderHasNoLastLocationException
     */
    @JvmOverloads
    fun getLastLocation(provider: String, howOldCanBe: LocationTime? = null, vararg transformers: RxLocationTransformer<MAYBE>): MAYBE =
            baseGetLastLocation(provider, howOldCanBe, transformers)

    /**
     * Try to get current location by specific provider.
     * Observable will emit [TimeoutException] if [timeOut] is not null and timeOut occurs.
     * Observable will emit [ProviderDisabledException] if provider is disabled
     *
     * @param provider provider name
     * @param timeOut  request timeout
     * @return observable that emit current location
     * @see TimeoutException
     * @see ProviderDisabledException
     */
    @JvmOverloads
    fun requestLocation(provider: String, timeOut: LocationTime? = null, vararg transformers: RxLocationTransformer<SINGLE>): SINGLE
            = baseRequestLocation(provider, timeOut, transformers)

    abstract fun onRequestPermissionsResult(permissions: Array<out String>, grantResults: IntArray)

    protected abstract fun baseGetLastLocation(provider: String, howOldCanBe: LocationTime?, transformers: Array<out RxLocationTransformer<MAYBE>>?): MAYBE

    protected abstract fun baseRequestLocation(provider: String, timeOut: LocationTime?, transformers: Array<out RxLocationTransformer<SINGLE>>?): SINGLE

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