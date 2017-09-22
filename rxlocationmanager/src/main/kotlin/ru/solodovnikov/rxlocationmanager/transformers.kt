package ru.solodovnikov.rxlocationmanager

import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import android.os.Build
import rx.Completable
import rx.Single
import rx.Subscription
import java.util.*

/**
 * Transformer used to request runtime permissions
 *
 * Call [RxLocationManager.onRequestPermissionsResult] inside your [android.app.Activity.onRequestPermissionsResult]
 * to get request permissions results in the transformer.
 */
open class PermissionTransformer(context: Context,
                                 private val rxLocationManager: RxLocationManager,
                                 callback: BasePermissionTransformer.PermissionCallback
) : BasePermissionTransformer(context, callback),
        Single.Transformer<Location, Location> {

    override fun call(t: Single<Location>): Single<Location> =
            checkPermissions().andThen(t)

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
                            val resultPermissions = it.first
                            val resultPermissionsResults = it.second
                            if (!Arrays.equals(resultPermissions, deniedPermissions) ||
                                    resultPermissionsResults
                                            .find { it == PackageManager.PERMISSION_DENIED } != null) {
                                emitter.onError(SecurityException("User denied permissions: ${deniedPermissions.asList()}"))
                            } else {
                                emitter.onCompleted()
                            }
                        }.apply { emitter.setCancellation { unsubscribe() } }

                        callback.requestPermissions(deniedPermissions)
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
 * Transformer used to ignore any described error type.
 *
 * @param errorsToIgnore if empty, then ignore all errors, otherwise just described types.
 */
class IgnoreErrorTransformer(vararg errorsToIgnore: Class<out Throwable>
) : Single.Transformer<Location, Location> {
    private val toIgnore: Array<out Class<out Throwable>> = errorsToIgnore

    override fun call(t: Single<Location>): Single<Location> =
            t.onErrorResumeNext {
                if (toIgnore.isEmpty() || toIgnore.contains(it.javaClass)) {
                    IgnorableException()
                } else {
                    it
                }.let { Single.error<Location>(it) }
            }
}