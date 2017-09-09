package ru.solodovnikov.rx2locationmanager

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import android.os.Build
import io.reactivex.Completable
import io.reactivex.Maybe
import io.reactivex.Single
import io.reactivex.subjects.PublishSubject
import java.util.*

abstract class BasePermissionTransformerImpl<RX>(context: Context,
                                                          private val permissionResult: PublishSubject<Pair<Array<out String>, IntArray>>,
                                                          private val callback: BasePermissionTransformer.PermissionCallback
) : BasePermissionTransformer<RX> {
    private val context = context.applicationContext

    private val permissions =
            arrayOf(Manifest.permission.ACCESS_FINE_LOCATION,
                    Manifest.permission.ACCESS_COARSE_LOCATION)

    protected fun checkPermissions(): Completable =
            Completable.create { emitter ->
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                    val deniedP = permissions.filter {
                        context.checkSelfPermission(it) == PackageManager.PERMISSION_DENIED
                    }.toTypedArray()

                    if (deniedP.isNotEmpty()) {
                        callback.requestPermissions(deniedP)
                        //wait until user approve permissions or dispose action
                        permissionResult.subscribe {
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

internal class PermissionRxSingleTransformer(context: Context,
                                             permissionResult: PublishSubject<Pair<Array<out String>, IntArray>>,
                                             callback: BasePermissionTransformer.PermissionCallback
) : BasePermissionTransformerImpl<Single<Location>>(context, permissionResult, callback) {
    override fun transform(rx: Single<Location>): Single<Location> = checkPermissions().andThen(rx)
}

internal class PermissionRxMaybeTransformer(context: Context,
                                            permissionResult: PublishSubject<Pair<Array<out String>, IntArray>>,
                                            callback: BasePermissionTransformer.PermissionCallback
) : BasePermissionTransformerImpl<Maybe<Location>>(context, permissionResult, callback) {
    override fun transform(rx: Maybe<Location>): Maybe<Location> = checkPermissions().andThen(rx)
}