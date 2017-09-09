package ru.solodovnikov.rxlocationmanager

interface RxLocationTransformer<RX> {
    fun transform(rx: RX): RX
}

interface BasePermissionTransformer<RX> : RxLocationTransformer<RX> {
    interface PermissionCallback {
        fun requestPermissions(permissions: Array<String>)
    }
}