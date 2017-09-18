package ru.solodovnikov.rxlocationmanager

import android.content.pm.PackageManager
import android.location.Location
import android.os.Build
import rx.Completable
import rx.Single
import java.util.*

class PermissionTransformer(private val rxLocationManager: RxLocationManager,
                            callback: BasePermissionTransformer.PermissionCallback
) : BasePermissionTransformer<Single<Location>>(rxLocationManager.context, callback) {
    override fun transform(rx: Single<Location>): Single<Location> =
            checkPermissions().andThen(rx)

    protected fun checkPermissions(): Completable =
            Completable.fromEmitter { emitter ->
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                    val deniedP = getDeniedPermissions()

                    if (deniedP.isNotEmpty()) {
                        callback.requestPermissions(deniedP)
                        //wait until user approve permissions or dispose action
                        rxLocationManager.subscribeToPermissionUpdate {
                            val resultPermissions = it.first
                            val resultPermissionsResults = it.second
                            if (!Arrays.equals(resultPermissions, deniedP) || resultPermissionsResults.find { it == PackageManager.PERMISSION_DENIED } != null) {
                                emitter.onError(SecurityException("User denied permissions: ${deniedP.asList()}"))
                            } else {
                                emitter.onCompleted()
                            }
                        }.apply { emitter.setCancellation { unsubscribe() } }
                    } else {
                        emitter.onCompleted()
                    }
                } else {
                    emitter.onCompleted()
                }
            }
}

/**
 * Use it to ignore any described error type.
 *
 * @param errorsToIgnore if null or empty, then ignore all errors, otherwise just described types.
 */
class IgnoreErrorTransformer(vararg errorsToIgnore: Class<out Throwable>) : RxLocationTransformer<Single<Location>> {
    private val toIgnore: Array<out Class<out Throwable>> = errorsToIgnore

    override fun transform(rx: Single<Location>): Single<Location> =
            rx.onErrorResumeNext {
                if (toIgnore.isEmpty() || toIgnore.contains(it.javaClass)) {
                    IgnorableException()
                } else {
                    it
                }.let { Single.error<Location>(it) }
            }
}