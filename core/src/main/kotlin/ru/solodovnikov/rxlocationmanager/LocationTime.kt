package ru.solodovnikov.rxlocationmanager

import java.util.concurrent.TimeUnit

data class LocationTime(val time: Long, val timeUnit: TimeUnit) {
    companion object {
        @JvmField
        val ONE_DAY = LocationTime(1L, TimeUnit.DAYS)
        @JvmField
        val ONE_HOUR = LocationTime(1L, TimeUnit.HOURS)
    }
}