package ru.solodovnikov.rxlocationmanager

import android.app.Activity
import android.app.Instrumentation
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
import rx.Subscription
import java.util.*

interface SingleBehavior {
    fun <T> transform(upstream: Single<T>, params: BehaviorParams): Single<T>
}

interface ObservableBehavior {
    fun <T> transform(upstream: Observable<T>, params: BehaviorParams): Observable<T>
}

interface CompletableBehavior {
    fun transform(upstream: Completable, params: BehaviorParams): Completable
}

interface Behavior : SingleBehavior, ObservableBehavior, CompletableBehavior

/**
 * Behavior used to request runtime permissions
 *
 * Call [RxLocationManager.onRequestPermissionsResult] inside your [android.app.Activity.onRequestPermissionsResult]
 * to get request permissions results in the transformer.
 */
open class PermissionBehavior(context: Context,
                              private val rxLocationManager: RxLocationManager,
                              caller: PermissionCaller
) : BasePermissionBehavior(context, caller), Behavior {


    override fun <T> transform(upstream: Single<T>, params: BehaviorParams): Single<T> =
            checkPermissions().andThen(upstream)

    override fun <T> transform(upstream: Observable<T>, params: BehaviorParams): Observable<T> =
            checkPermissions().andThen(upstream)

    override fun transform(upstream: Completable, params: BehaviorParams): Completable =
            checkPermissions().andThen(upstream)

    /**
     * Construct [Completable] which check runtime permissions
     */
    protected open fun checkPermissions(): Completable =
            Completable.fromEmitter { emitter ->
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                    val deniedPermissions = getDeniedPermissions()

                    if (deniedPermissions.isNotEmpty()) {
                        //wait until user approve permissions or dispose action
                        subscribeToPermissionUpdate {
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

    /**
     * Subscribe to request permissions result
     */
    protected fun subscribeToPermissionUpdate(onUpdate: (Pair<Array<out String>, IntArray>) -> Unit): Subscription =
            rxLocationManager.subscribeToPermissionUpdate(onUpdate)
}

/**
 * Behavior used to ignore any described error type.
 *
 * @param errorsToIgnore if empty, then ignore all errors, otherwise just described types.
 */
class IgnoreErrorBehavior(vararg errorsToIgnore: Class<out Throwable>) : Behavior {
    private val toIgnore: Array<out Class<out Throwable>> = errorsToIgnore

    override fun <T> transform(upstream: Single<T>, params: BehaviorParams): Single<T> =
            upstream.onErrorResumeNext {
                if (toIgnore.isEmpty() || toIgnore.contains(it.javaClass)) {
                    IgnorableException()
                } else {
                    it
                }.let { Single.error<T>(it) }
            }

    override fun <T> transform(upstream: Observable<T>, params: BehaviorParams): Observable<T> =
            upstream.onErrorResumeNext {
                if (toIgnore.isEmpty() || toIgnore.contains(it.javaClass)) {
                    Observable.empty<T>()
                } else {
                    Observable.error<T>(it)
                }
            }

    override fun transform(upstream: Completable, params: BehaviorParams): Completable =
            upstream.onErrorResumeNext {
                if (toIgnore.isEmpty() || toIgnore.contains(it.javaClass)) {
                    Completable.complete()
                } else {
                    Completable.error(it)
                }
            }
}

class EnableLocationBehavior(private val resolver: Resolver) : Behavior {
    override fun <T> transform(upstream: Single<T>, params: BehaviorParams): Single<T> =
            resolver.create(params.provider!!).andThen(upstream)

    override fun <T> transform(upstream: Observable<T>, params: BehaviorParams): Observable<T> =
            resolver.create(params.provider!!).andThen(upstream)

    override fun transform(upstream: Completable, params: BehaviorParams): Completable =
            resolver.create(params.provider!!).andThen(upstream)

    companion object {
        @JvmStatic
        fun create(context: Context,
                   requestCode: Int,
                   rxLocationManager: RxLocationManager,
                   forResultCaller: ForResultCaller): EnableLocationBehavior =
                if (try {
                    Class.forName("com.google.android.gms.location.LocationServices") != null
                } catch (e: Exception) {
                    false
                }) {
                    GoogleResolver(context, requestCode, rxLocationManager, forResultCaller)
                } else {
                    SettingsResolver(requestCode, rxLocationManager, forResultCaller)
                }.let { EnableLocationBehavior(it) }
    }

    abstract class Resolver(private val rxLocationManager: RxLocationManager) {
        abstract fun create(provider: String): Completable

        protected fun checkProvider(provider: String): Single<Boolean> =
                rxLocationManager.getProvider(provider)
                        .flatMap {
                            if (it == null) {
                                Single.error<Boolean>(ProviderNotAvailableException(provider))
                            } else {
                                rxLocationManager.isProviderEnabled(it.name)
                            }
                        }

        protected fun subscribeToActivityResultUpdate(f: (Instrumentation.ActivityResult) -> Unit): Subscription =
                rxLocationManager.subscribeToActivityResultUpdate(f)
    }

    class SettingsResolver(private val requestCode: Int,
                           rxLocationManager: RxLocationManager,
                           private val forResultCaller: ForResultCaller) : Resolver(rxLocationManager) {
        override fun create(provider: String): Completable =
                checkProvider(provider).flatMap { isProviderEnabled ->
                    Single.fromEmitter<Boolean> { emitter ->
                        if (!isProviderEnabled) {
                            subscribeToActivityResultUpdate {
                                if (it.resultCode == Activity.RESULT_CANCELED) {
                                    emitter.onSuccess(true)
                                } else {
                                    emitter.onError(IllegalStateException("Unknown result"))
                                }
                            }.apply { emitter.setCancellation { unsubscribe() } }

                            forResultCaller.startActivityForResult(
                                    Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS), requestCode)
                        } else {
                            emitter.onSuccess(false)
                        }
                    }
                }.flatMapCompletable {
                    if (it) {
                        checkProvider(provider).flatMapCompletable {
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

    class GoogleResolver(context: Context,
                         private val requestCode: Int,
                         rxLocationManager: RxLocationManager,
                         private val forResultCaller: ForResultCaller) : Resolver(rxLocationManager) {
        private val context = context.applicationContext

        override fun create(provider: String): Completable =
                Completable.fromEmitter { emitter ->
                    LocationServices.getSettingsClient(context)
                            .checkLocationSettings(LocationSettingsRequest.Builder()
                                    .addLocationRequest(LocationRequest.create()).build())
                            .addOnFailureListener {
                                (it as? ApiException ?: throw it).also {
                                    when (it.statusCode) {
                                        CommonStatusCodes.RESOLUTION_REQUIRED -> {
                                            subscribeToActivityResultUpdate {
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
                                                        requestCode, null, 0, 0, 0, null)
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