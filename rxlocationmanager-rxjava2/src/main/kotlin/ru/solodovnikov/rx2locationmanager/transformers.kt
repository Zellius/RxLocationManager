package ru.solodovnikov.rx2locationmanager

import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import android.os.Build
import io.reactivex.Completable
import io.reactivex.Maybe
import io.reactivex.Single
import java.util.*

abstract class BasePermissionTransformerImpl<RX>(context: Context,
                                                 private val rxLocationManager: RxLocationManager,
                                                 callback: BasePermissionTransformer.PermissionCallback
) : BasePermissionTransformer<RX>(context, callback) {

    protected fun checkPermissions(): Completable =
            Completable.create { emitter ->
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
                                emitter.onComplete()
                            }
                        }.apply { emitter.setCancellable { dispose() } }
                    } else {
                        emitter.onComplete()
                    }
                } else {
                    emitter.onComplete()
                }
            }
}

class PermissionRxSingleTransformer(context: Context,
                                    rxLocationManager: RxLocationManager,
                                    callback: BasePermissionTransformer.PermissionCallback
) : BasePermissionTransformerImpl<Single<Location>>(context, rxLocationManager, callback) {
    override fun transform(rx: Single<Location>): Single<Location> = checkPermissions().andThen(rx)
}

class PermissionRxMaybeTransformer(context: Context,
                                   rxLocationManager: RxLocationManager,
                                   callback: BasePermissionTransformer.PermissionCallback
) : BasePermissionTransformerImpl<Maybe<Location>>(context, rxLocationManager, callback) {
    override fun transform(rx: Maybe<Location>): Maybe<Location> = checkPermissions().andThen(rx)
}

abstract class BaseIgnoreErrorTransformer<RX>(protected val errorsToIgnore: Array<out Class<out Throwable>>) : RxLocationTransformer<RX>

/**
 * Use it to ignore any described error type.
 *
 * @param errorsToIgnore if null or empty, then ignore all errors, otherwise just described types.
 */
class IgnoreErrorSingleTransformer(vararg errorsToIgnore: Class<out Throwable>
) : BaseIgnoreErrorTransformer<Single<Location>>(errorsToIgnore) {
    override fun transform(rx: Single<Location>): Single<Location> =
            rx.onErrorResumeNext {
                if (errorsToIgnore.isEmpty() || errorsToIgnore.contains(it.javaClass)) {
                    IgnorableException()
                } else {
                    it
                }.let { Single.error<Location>(it) }
            }
}

class IgnoreErrorMaybeTransformer(vararg errorsToIgnore: Class<out Throwable>
) : BaseIgnoreErrorTransformer<Maybe<Location>>(errorsToIgnore) {
    override fun transform(rx: Maybe<Location>): Maybe<Location> =
            rx.onErrorResumeNext { t: Throwable ->
                if (errorsToIgnore.isEmpty() || errorsToIgnore.contains(t.javaClass)) {
                    IgnorableException()
                } else {
                    t
                }.let { Maybe.error<Location>(it) }
            }
}