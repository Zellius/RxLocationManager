package ru.solodovnikov.rx2locationmanager

import android.content.Context
import android.location.*
import android.os.Bundle
import io.reactivex.*
import io.reactivex.android.schedulers.AndroidSchedulers
import io.reactivex.subjects.PublishSubject
import java.util.concurrent.TimeoutException

/**
 * Implementation of [BaseRxLocationManager] based on RxJava2
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
     * @see ProviderHasNoLastLocationException
     */
    @JvmOverloads
    fun getLastLocation(provider: String,
                        howOldCanBe: LocationTime? = null,
                        vararg transformers: MaybeTransformer<Location, Location>): Maybe<Location> =
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
                        vararg transformers: SingleTransformer<Location, Location>): Single<Location>
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
    fun getProvider(name: String, vararg transformers: MaybeTransformer<LocationProvider, LocationProvider>) =
            Maybe.fromCallable { locationManager.getProvider(name) ?: throw NullEmittedException() }
                    .onErrorComplete { it is NullEmittedException }
                    .applyTransformers(transformers)

    override fun onRequestPermissionsResult(permissions: Array<out String>, grantResults: IntArray) {
        permissionSubject.onNext(Pair(permissions, grantResults))
    }

    internal fun subscribeToPermissionUpdate(onUpdate: (Pair<Array<out String>, IntArray>) -> Unit)
            = permissionSubject.subscribe(onUpdate, {}, {})

    /**
     * @return Result [Maybe] will not emit any value if location is null.
     * Or it will be emit [ElderLocationException] if [howOldCanBe] not null and location is too old
     */
    private fun baseGetLastLocation(provider: String, howOldCanBe: LocationTime?, transformers: Array<out MaybeTransformer<Location, Location>>): Maybe<Location> =
            Maybe.fromCallable { locationManager.getLastKnownLocation(provider) ?: throw NullEmittedException() }
                    .onErrorComplete { it is NullEmittedException }
                    .compose {
                        if (howOldCanBe != null) {
                            it.doOnSuccess {
                                if (!it.isNotOld(howOldCanBe)) {
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
    private fun baseRequestLocation(provider: String, timeOut: LocationTime?, transformers: Array<out SingleTransformer<Location, Location>>): Single<Location> =
            Single.create(SingleOnSubscribe<Location> {
                if (locationManager.isProviderEnabled(provider)) {
                    val locationListener = object : LocationListener {
                        override fun onLocationChanged(location: Location) {
                            it.onSuccess(location)
                        }

                        override fun onProviderDisabled(p: String?) {
                            if (provider == p) {
                                it.onError(ProviderDisabledException(provider))
                            }
                        }

                        override fun onStatusChanged(p0: String?, p1: Int, p2: Bundle?) {}

                        override fun onProviderEnabled(p: String?) {}
                    }

                    locationManager.requestSingleUpdate(provider, locationListener, null)

                    it.setCancellable { locationManager.removeUpdates(locationListener) }

                } else {
                    it.onError(ProviderDisabledException(provider))
                }
            }).compose { if (timeOut != null) it.timeout(timeOut.time, timeOut.timeUnit) else it }
                    .applyTransformers(transformers)
                    .compose(this::applySchedulers)

    private fun baseRequestLocationUpdates(provider: String, minTime: Long, minDistance: Float): Observable<Location> {
        Observable.create(ObservableOnSubscribe<Location> {
            val locationListener = object : LocationListener {
                override fun onLocationChanged(location: Location) {
                    it.onNext(location)
                }

                override fun onProviderDisabled(p: String?) {
                }

                override fun onStatusChanged(p0: String?, p1: Int, p2: Bundle?) {}

                override fun onProviderEnabled(p: String?) {}
            }

            locationManager.requestLocationUpdates(provider, minTime, minDistance, locationListener)

            it.setCancellable { locationManager.removeUpdates(locationListener) }
        })
        TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
    }

    private fun <T> Single<T>.applyTransformers(transformers: Array<out SingleTransformer<T, T>>) =
            let { transformers.fold(it, { acc, transformer -> acc.compose(transformer) }) ?: it }

    private fun <T> Maybe<T>.applyTransformers(transformers: Array<out MaybeTransformer<T, T>>) =
            let { transformers.fold(it, { acc, transformer -> acc.compose(transformer) }) ?: it }

    private fun <T> Observable<T>.applyTransformers(transformers: Array<out ObservableTransformer<T, T>>) =
            let { transformers.fold(it, { acc, transformer -> acc.compose(transformer) }) ?: it }

    private fun applySchedulers(s: Single<Location>) = s.subscribeOn(scheduler)

    private fun applySchedulers(m: Maybe<Location>) = m.subscribeOn(scheduler)
}

