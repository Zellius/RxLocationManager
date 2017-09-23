package ru.solodovnikov.rxlocationmanager

import android.content.Context
import android.location.*
import android.os.Bundle
import rx.Emitter
import rx.Observable
import rx.Scheduler
import rx.Single
import rx.android.schedulers.AndroidSchedulers
import rx.functions.Action1
import rx.subjects.PublishSubject
import java.util.concurrent.TimeoutException

/**
 * Implementation of [BaseRxLocationManager] based on RxJava1
 */
class RxLocationManager internal constructor(context: Context,
                                             private val scheduler: Scheduler
) : BaseRxLocationManager(context) {

    constructor(context: Context) : this(context, AndroidSchedulers.mainThread())

    private val permissionSubject by lazy { PublishSubject.create<Pair<Array<out String>, IntArray>>() }

    /**
     * Get last location from specific provider
     * Observable will emit [ElderLocationException] if [howOldCanBe] is not null and location time is not valid.
     *
     * @param provider provider name
     * @param howOldCanBe how old a location can be
     * @param transformers extra transformers
     * @return observable that emit last known location
     * @see ElderLocationException
     */
    @JvmOverloads
    fun getLastLocation(provider: String,
                        howOldCanBe: LocationTime? = null,
                        vararg transformers: TransformerSingle<Location, Location>): Single<Location> =
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
                        vararg transformers: TransformerSingle<Location, Location>): Single<Location>
            = baseRequestLocation(provider, timeOut, transformers)

    /**
     * Register for location updates using a Criteria
     *
     * @param provider the name of the provider with which to register
     * @param minTime minimum time interval between location updates, in milliseconds
     * @param minDistance minimum distance between location updates, in meters
     */
    fun requestLocationUpdates(provider: String,
                               minTime: Long,
                               minDistance: Float): Observable<Location> =
            baseRequestLocationUpdates(provider, minTime, minDistance)

    /**
     * Returns a list of the names of all known location providers.
     *
     * @see LocationManager.getAllProviders
     */
    fun getAllProviders() = Single.fromCallable { locationManager.allProviders }

    /**
     * Returns the name of the provider that best meets the given criteria.
     *
     * @see LocationManager.getBestProvider
     */
    @JvmOverloads
    fun getBestProvider(criteria: Criteria, enabledOnly: Boolean = true) =
            Single.fromCallable { locationManager.getBestProvider(criteria, enabledOnly) }

    /**
     * Returns the information associated with the location provider of the given name, or null if no provider exists by that name.
     *
     * @param transformers extra transformers
     * @see LocationManager.getProvider
     */
    fun getProvider(name: String, vararg transformers: TransformerSingle<LocationProvider, LocationProvider>) =
            Single.fromCallable { locationManager.getProvider(name) }
                    .applyTransformers(transformers)

    override fun onRequestPermissionsResult(permissions: Array<out String>, grantResults: IntArray) {
        permissionSubject.onNext(Pair(permissions, grantResults))
    }

    internal fun subscribeToPermissionUpdate(onUpdate: (Pair<Array<out String>, IntArray>) -> Unit)
            = permissionSubject.subscribe(onUpdate, {}, {})

    /**
     * @return Result [Single] will emit null if there is no location by this [provider].
     * Or it will be emit [ElderLocationException] if [howOldCanBe] not null and location is too old.
     */
    private fun baseGetLastLocation(provider: String,
                                    howOldCanBe: LocationTime?,
                                    transformers: Array<out TransformerSingle<Location, Location>>): Single<Location> =
            Single.fromCallable { locationManager.getLastKnownLocation(provider) }
                    .compose {
                        if (howOldCanBe != null) {
                            it.doOnSuccess {
                                if (it != null && !it.isNotOld(howOldCanBe)) {
                                    throw ElderLocationException(it)
                                }
                            }
                        } else {
                            it
                        }
                    }.applyTransformers(transformers)
                    .compose(this::applySchedulers)

    /**
     * @return Result [Single] can throw [ProviderDisabledException] or [TimeoutException] if [timeOut] not null
     */
    private fun baseRequestLocation(provider: String,
                                    timeOut: LocationTime?,
                                    transformers: Array<out TransformerSingle<Location, Location>>): Single<Location> =
            Observable.create(RxLocationListener(locationManager, provider), Emitter.BackpressureMode.NONE)
                    .toSingle()
                    .compose { if (timeOut != null) it.timeout(timeOut.time, timeOut.timeUnit) else it }
                    .applyTransformers(transformers)
                    .compose(this::applySchedulers)

    private fun baseRequestLocationUpdates(provider: String, minTime: Long, minDistance: Float): Observable<Location> {
        TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
    }

    private fun applySchedulers(s: Single<Location>) = s.subscribeOn(scheduler)

    private fun <T, R> Single<T>.applyTransformers(transformers: Array<out TransformerSingle<T, R>>) =
            let { transformers.fold(it, { acc, transformer -> transformer.transform(acc) }) }

    private fun <T, R> Observable<T>.applyTransformers(transformers: Array<out TransformerObservable<T, R>>) =
            let { transformers.fold(it, { acc, transformer -> transformer.transform(acc) }) }

    private class RxLocationListener(val locationManager: LocationManager, val provider: String) : Action1<Emitter<Location>> {

        override fun call(emitter: Emitter<Location>) {
            if (locationManager.isProviderEnabled(provider)) {
                val locationListener = object : LocationListener {
                    override fun onLocationChanged(location: Location?) {
                        with(emitter) {
                            onNext(location)
                            onCompleted()
                        }
                    }

                    override fun onProviderDisabled(p: String?) {
                        if (provider == p) {
                            emitter.onError(ProviderDisabledException(provider))
                        }
                    }

                    override fun onStatusChanged(p0: String?, p1: Int, p2: Bundle?) {}

                    override fun onProviderEnabled(p0: String?) {}
                }

                locationManager.requestSingleUpdate(provider, locationListener, null)

                emitter.setCancellation { locationManager.removeUpdates(locationListener) }

            } else {
                emitter.onError(ProviderDisabledException(provider))
            }
        }
    }
}


