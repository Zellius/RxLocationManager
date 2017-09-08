package ru.solodovnikov.rxlocationmanager

interface RxLocationTransformer<RX> {
    fun transform(rx: RX): RX
}

interface BasePermissionTransformer<RX> : RxLocationTransformer<RX> {
    interface PermissionCallback {
        fun shouldShowRequestPermissionRationale(permission: String): Boolean

        fun requestPermissions(permissions: Array<String>)
    }
}