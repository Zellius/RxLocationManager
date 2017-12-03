package ru.solodovnikov.rxlocationmanager

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.provider.Settings
import com.google.android.gms.common.api.ApiException
import com.google.android.gms.common.api.CommonStatusCodes
import com.google.android.gms.common.api.ResolvableApiException
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.LocationSettingsRequest
import com.google.android.gms.location.LocationSettingsStates
import rx.Completable
import rx.Observable
import rx.Single
import java.util.*

/**
 * Behavior for [Single]
 */
interface SingleBehavior {
    /**
     * Transform [upstream]
     *
     * @param upstream input rx stream
     * @param rxLocationManager rxlocationManager instance
     * @param params request params
     */
    fun <T> transform(upstream: Single<T>,
                      rxLocationManager: RxLocationManager,
                      params: BehaviorParams): Single<T>
}

/**
 * Behavior for [Observable]
 */
interface ObservableBehavior {
    /**
     * Transform [upstream]
     *
     * @param upstream input rx stream
     * @param rxLocationManager rxlocationManager instance
     * @param params request params
     */
    fun <T> transform(upstream: Observable<T>,
                      rxLocationManager: RxLocationManager,
                      params: BehaviorParams): Observable<T>
}

/**
 * Behavior for [Completable]
 */
interface CompletableBehavior {
    /**
     * Transform [upstream]
     *
     * @param upstream input rx stream
     * @param rxLocationManager rxlocationManager instance
     * @param params request params
     */
    fun transform(upstream: Completable,
                  rxLocationManager: RxLocationManager,
                  params: BehaviorParams): Completable
}

/**
 * Base interface for behaviors
 */
interface Behavior : SingleBehavior, ObservableBehavior, CompletableBehavior

/**
 * Behavior used to request runtime permissions
 *
 * Call [RxLocationManager.onRequestPermissionsResult] inside your [android.app.Activity.onRequestPermissionsResult]
 * to get request permissions results in the behavior.
 *
 * @param context application context
 * @param caller caller of the behavior
 */
open class PermissionBehavior(context: Context, caller: PermissionCaller
) : BasePermissionBehavior(context, caller), Behavior {


    override fun <T> transform(upstream: Single<T>,
                               rxLocationManager: RxLocationManager,
                               params: BehaviorParams): Single<T> =
            checkPermissions(rxLocationManager).andThen(upstream)

    override fun <T> transform(upstream: Observable<T>,
                               rxLocationManager: RxLocationManager,
                               params: BehaviorParams): Observable<T> =
            checkPermissions(rxLocationManager).andThen(upstream)

    override fun transform(upstream: Completable,
                           rxLocationManager: RxLocationManager,
                           params: BehaviorParams): Completable =
            checkPermissions(rxLocationManager).andThen(upstream)

    /**
     * Construct [Completable] which check runtime permissions
     *
     * @param rxLocationManager a locationManager instance
     */
    protected open fun checkPermissions(rxLocationManager: RxLocationManager): Completable =
            Completable.fromEmitter { emitter ->
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                    val deniedPermissions = getDeniedPermissions()

                    if (deniedPermissions.isNotEmpty()) {
                        //wait until user approve permissions or dispose action
                        rxLocationManager.subscribeToPermissionUpdate {
                            val (resultPermissions, resultPermissionsResults) = it
                            if (!Arrays.equals(resultPermissions, deniedPermissions) ||
                                    resultPermissionsResults
                                            .find { it == PackageManager.PERMISSION_DENIED } != null) {
                                emitter.onError(SecurityException("User denied permissions: ${deniedPermissions.asList()}"))
                            } else {
                                emitter.onCompleted()
                            }
                        }.apply { emitter.setCancellation { unsubscribe() } }

                        caller.requestPermissions(deniedPermissions)
                    } else {
                        emitter.onCompleted()
                    }
                } else {
                    emitter.onCompleted()
                }
            }
}

/**
 * Behavior used to ignore any described error type.
 *
 * @param errorsToIgnore if empty, then ignore all errors, otherwise just described types.
 */
internal class IgnoreErrorBehavior(vararg errorsToIgnore: Class<out Throwable>) : Behavior {
    private val toIgnore: Array<out Class<out Throwable>> = errorsToIgnore

    override fun <T> transform(upstream: Single<T>, rxLocationManager: RxLocationManager, params: BehaviorParams): Single<T> =
            upstream.onErrorResumeNext {
                if (toIgnore.isEmpty() || toIgnore.contains(it.javaClass)) {
                    IgnorableException()
                } else {
                    it
                }.let { Single.error<T>(it) }
            }

    override fun <T> transform(upstream: Observable<T>, rxLocationManager: RxLocationManager, params: BehaviorParams): Observable<T> =
            upstream.onErrorResumeNext {
                if (toIgnore.isEmpty() || toIgnore.contains(it.javaClass)) {
                    Observable.empty<T>()
                } else {
                    Observable.error<T>(it)
                }
            }

    override fun transform(upstream: Completable, rxLocationManager: RxLocationManager, params: BehaviorParams): Completable =
            upstream.onErrorResumeNext {
                if (toIgnore.isEmpty() || toIgnore.contains(it.javaClass)) {
                    Completable.complete()
                } else {
                    Completable.error(it)
                }
            }
}

/**
 * Behavior used to enable location provider if needed
 *
 * @param resolver describe how to enable provider
 */
class EnableLocationBehavior(private val resolver: Resolver) : Behavior {
    override fun <T> transform(upstream: Single<T>,
                               rxLocationManager: RxLocationManager,
                               params: BehaviorParams): Single<T> =
            resolver.resolve(rxLocationManager, params.provider!!).andThen(upstream)

    override fun <T> transform(upstream: Observable<T>,
                               rxLocationManager: RxLocationManager,
                               params: BehaviorParams): Observable<T> =
            resolver.resolve(rxLocationManager, params.provider!!).andThen(upstream)

    override fun transform(upstream: Completable,
                           rxLocationManager: RxLocationManager,
                           params: BehaviorParams): Completable =
            resolver.resolve(rxLocationManager, params.provider!!).andThen(upstream)

    companion object {
        /**
         * Create best resolver for your application
         *
         * @param context application context
         * @param forResultCaller caller
         */
        @JvmStatic
        fun create(context: Context,
                   forResultCaller: ForResultCaller): EnableLocationBehavior =
                if (try {
                    Class.forName("com.google.android.gms.location.LocationServices") != null
                } catch (e: Exception) {
                    false
                }) {
                    GoogleResolver(context, forResultCaller)
                } else {
                    SettingsResolver(forResultCaller)
                }.let { EnableLocationBehavior(it) }
    }

    /**
     * Base class for [EnableLocationBehavior] resolver
     */
    abstract class Resolver {
        /**
         * Try to enable provider
         *
         * @param rxLocationManager rxLocationManager instance
         * @param provider [android.location.LocationManager] provider to enable
         */
        abstract fun resolve(rxLocationManager: RxLocationManager, provider: String): Completable

        /**
         * Check is [provider] available and enabled
         * @param rxLocationManager rxLocationManager instance
         * @param provider [android.location.LocationManager] provider to check
         */
        protected fun checkProvider(rxLocationManager: RxLocationManager, provider: String): Single<Boolean> =
                rxLocationManager.getProvider(provider)
                        .flatMap {
                            if (it == null) {
                                Single.error<Boolean>(ProviderNotAvailableException(provider))
                            } else {
                                rxLocationManager.isProviderEnabled(it.name)
                            }
                        }
    }

    /**
     * Resover based on Android system settings
     *
     * @param forResultCaller caller
     */
    class SettingsResolver(private val forResultCaller: ForResultCaller) : Resolver() {
        override fun resolve(rxLocationManager: RxLocationManager, provider: String): Completable =
                checkProvider(rxLocationManager, provider).flatMap { isProviderEnabled ->
                    Single.fromEmitter<Boolean> { emitter ->
                        if (!isProviderEnabled) {
                            rxLocationManager.subscribeToActivityResultUpdate {
                                if (it.resultCode == Activity.RESULT_CANCELED) {
                                    emitter.onSuccess(true)
                                } else {
                                    emitter.onError(IllegalStateException("Unknown result"))
                                }
                            }.apply { emitter.setCancellation { unsubscribe() } }

                            forResultCaller.startActivityForResult(
                                    Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS))
                        } else {
                            emitter.onSuccess(false)
                        }
                    }
                }.flatMapCompletable {
                    if (it) {
                        checkProvider(rxLocationManager, provider).flatMapCompletable {
                            if (it) {
                                Completable.complete()
                            } else {
                                Completable.error(LocationDisabledException())
                            }
                        }
                    } else {
                        Completable.complete()
                    }
                }
    }

    /**
     * Resover based on Android system settings
     *
     * @param context application context
     * @param forResultCaller caller
     */
    class GoogleResolver(context: Context,
                         private val forResultCaller: ForResultCaller) : Resolver() {
        private val context = context.applicationContext

        override fun resolve(rxLocationManager: RxLocationManager, provider: String): Completable =
                Completable.fromEmitter { emitter ->
                    LocationServices.getSettingsClient(context)
                            .checkLocationSettings(LocationSettingsRequest.Builder()
                                    .addLocationRequest(LocationRequest.create()).build())
                            .addOnFailureListener {
                                (it as? ApiException ?: throw it).also {
                                    when (it.statusCode) {
                                        CommonStatusCodes.RESOLUTION_REQUIRED -> {
                                            rxLocationManager.subscribeToActivityResultUpdate {
                                                LocationSettingsStates.fromIntent(it.resultData)
                                                        .isNetworkLocationUsable.also {
                                                    if (it) {
                                                        emitter.onCompleted()
                                                    } else {
                                                        emitter.onError(LocationDisabledException())
                                                    }
                                                }
                                            }.apply { emitter.setCancellation { unsubscribe() } }

                                            (it as? ResolvableApiException ?: throw it).also { e ->
                                                forResultCaller.startIntentSenderForResult(e.resolution.intentSender,
                                                        null, 0, 0, 0, null)
                                            }
                                        }
                                        else -> {
                                            emitter.onError(it)
                                        }
                                    }
                                }
                            }.addOnSuccessListener { emitter.onCompleted() }
                }
    }
}

/**
 * Behavior used to throw [ProviderDisabledException] if provider disabled
 */
class ThrowProviderDisabledBehavior : Behavior {
    override fun <T> transform(upstream: Single<T>,
                               rxLocationManager: RxLocationManager,
                               params: BehaviorParams): Single<T> =
            rxLocationManager.isProviderEnabled(params.provider!!).flatMap {
                if (it) {
                    upstream
                } else {
                    Single.error(ProviderDisabledException(params.provider))
                }
            }

    override fun <T> transform(upstream: Observable<T>,
                               rxLocationManager: RxLocationManager,
                               params: BehaviorParams): Observable<T> =
            rxLocationManager.isProviderEnabled(params.provider!!).flatMapObservable {
                if (it) {
                    upstream
                } else {
                    Observable.error(ProviderDisabledException(params.provider))
                }
            }

    override fun transform(upstream: Completable,
                           rxLocationManager: RxLocationManager,
                           params: BehaviorParams): Completable =
            rxLocationManager.isProviderEnabled(params.provider!!).flatMapCompletable {
                if (it) {
                    upstream
                } else {
                    Completable.error(ProviderDisabledException(params.provider))
                }
            }
}