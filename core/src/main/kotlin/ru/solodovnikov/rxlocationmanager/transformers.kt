package ru.solodovnikov.rxlocationmanager

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build

interface RxLocationTransformer<RX> {
    fun transform(rx: RX): RX
}

abstract class BasePermissionTransformer<RX>(context: Context,
                                             protected val callback: BasePermissionTransformer.PermissionCallback
) : RxLocationTransformer<RX> {
    protected val context: Context = context.applicationContext

    protected fun getDeniedPermissions() =
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                arrayOf(Manifest.permission.ACCESS_FINE_LOCATION,
                        Manifest.permission.ACCESS_COARSE_LOCATION)
                        .filter {
                            context.checkSelfPermission(it) == PackageManager.PERMISSION_DENIED
                        }.toTypedArray()
            } else {
                emptyArray()
            }

    interface PermissionCallback {
        fun requestPermissions(permissions: Array<String>)
    }
}