package ru.solodovnikov.rxlocationmanager

import java.util.concurrent.TimeUnit

data class LocationTime(val time: Long, val timeUnit: TimeUnit) {
    companion object {
        @JvmStatic
        fun oneDay() = LocationTime(1L, TimeUnit.DAYS)

        @JvmStatic
        fun oneHour() = LocationTime(1L, TimeUnit.HOURS)
    }
}