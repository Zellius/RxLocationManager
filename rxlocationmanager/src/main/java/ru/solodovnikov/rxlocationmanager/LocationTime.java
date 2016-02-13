package ru.solodovnikov.rxlocationmanager;

import java.util.concurrent.TimeUnit;

public final class LocationTime {
    private final long value;
    private final TimeUnit timeUnit;

    public LocationTime(long value, TimeUnit timeUnit) {
        this.value = value;
        this.timeUnit = timeUnit;
    }

    public static LocationTime OneDay() {
        return new LocationTime(1L, TimeUnit.DAYS);
    }

    public static LocationTime OneHour() {
        return new LocationTime(1L, TimeUnit.HOURS);
    }

    long getValue() {
        return value;
    }

    TimeUnit getTimeUnit() {
        return timeUnit;
    }
}
