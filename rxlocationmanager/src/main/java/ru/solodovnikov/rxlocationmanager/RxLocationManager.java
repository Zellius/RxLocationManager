package ru.solodovnikov.rxlocationmanager;

import android.annotation.TargetApi;
import android.content.Context;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.os.Build;
import android.os.Bundle;
import android.os.SystemClock;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;

import java.util.concurrent.Callable;
import java.util.concurrent.TimeoutException;

import ru.solodovnikov.rxlocationmanager.error.ElderLocationException;
import ru.solodovnikov.rxlocationmanager.error.ProviderDisabledException;
import rx.Observable;
import rx.Scheduler;
import rx.Subscriber;
import rx.Subscription;
import rx.android.schedulers.AndroidSchedulers;
import rx.functions.Func1;


public class RxLocationManager {

    private final LocationManager locationManager;
    private final Scheduler scheduler;

    public RxLocationManager(Context context) {
        this.locationManager = (LocationManager) context.getSystemService(Context.LOCATION_SERVICE);
        this.scheduler = AndroidSchedulers.mainThread();
    }

    RxLocationManager(LocationManager locationManager, Scheduler scheduler) {
        this.locationManager = locationManager;
        this.scheduler = scheduler;
    }

    /**
     * Get last location from specific provider
     * Observable will emit ElderLocationException if {@code howOldCanBe} is not null and location time is not valid.
     *
     * @param provider    provider name
     * @param howOldCanBe optional. How old a location can be
     * @return observable that emit last known location. May emit null.
     * @see ElderLocationException
     */
    public Observable<Location> getLastLocation(final @NonNull String provider, final @Nullable LocationTime howOldCanBe) {
        return Observable.fromCallable(new Callable<Location>() {
            @Override
            public Location call() throws Exception {
                try {
                    return locationManager.getLastKnownLocation(provider);
                } catch (SecurityException ex) {
                    throw ex;
                }
            }
        }).compose(new Observable.Transformer<Location, Location>() {
            @Override
            public Observable<Location> call(Observable<Location> locationObservable) {

                final Observable<Location> filterObservable = locationObservable.map(new Func1<Location, Location>() {
                    @Override
                    public Location call(Location location) {
                        if (location != null && !isLocationNotOld(location, howOldCanBe)) {
                            throw new ElderLocationException(location);
                        }

                        return location;
                    }
                });

                return howOldCanBe != null ? filterObservable : locationObservable;
            }
        }).compose(applySchedulers());
    }


    /**
     * Get last location from specific provider
     *
     * @param provider provider name
     * @return observable that emit last known location. May emit null.
     */
    public Observable<Location> getLastLocation(@NonNull String provider) {
        return getLastLocation(provider, null);
    }

    /**
     * Try to get current location by specific provider.
     * Observable will emit TimeoutException in case of timeOut if timeOut object is not null.
     * Observable will emit ProviderDisabledException if provider is disabled
     *
     * @param provider provider name
     * @param timeOut  optional request timeout
     * @return observable that emit current location
     * @see TimeoutException
     * @see ProviderDisabledException
     */
    public Observable<Location> requestLocation(final @NonNull String provider, final @Nullable LocationTime timeOut) {
        return requestLocation(provider, timeOut, true);
    }

    /**
     * Try to get current location by specific provider
     * Observable will emit ProviderDisabledException if provider is disabled
     *
     * @param provider provider name
     * @return observable that emit current location
     * @see ProviderDisabledException
     */
    public Observable<Location> requestLocation(@NonNull String provider) {
        return requestLocation(provider, null);
    }

    Observable<Location> requestLocation(final @NonNull String provider, final @Nullable LocationTime timeOut, boolean throwExceptionIfDisabled) {

        final RxLocationListener locationListener = new RxLocationListener(locationManager, provider, throwExceptionIfDisabled);

        return Observable.create(locationListener)
                .compose(new Observable.Transformer<Location, Location>() {
                    @Override
                    public Observable<Location> call(Observable<Location> locationObservable) {
                        return timeOut != null ? locationObservable.timeout(timeOut.getValue(), timeOut.getTimeUnit()) : locationObservable;
                    }
                })
                .compose(applySchedulers());
    }

    private Observable.Transformer<Location, Location> applySchedulers() {
        return new Observable.Transformer<Location, Location>() {
            @Override
            public Observable<Location> call(Observable<Location> locationObservable) {
                return locationObservable.subscribeOn(scheduler).observeOn(scheduler);
            }
        };
    }

    private boolean isLocationNotOld(Location location, LocationTime howOldCanBe) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN_MR1) {
            return isLocationNotOld17(location, howOldCanBe);
        } else {
            return isLocationNotOldDefault(location, howOldCanBe);
        }
    }

    @TargetApi(Build.VERSION_CODES.JELLY_BEAN_MR1)
    private boolean isLocationNotOld17(Location location, LocationTime howOldCanBe) {
        return SystemClock.elapsedRealtimeNanos() - location
                .getElapsedRealtimeNanos() < howOldCanBe.getTimeUnit().toNanos(howOldCanBe.getValue());
    }

    private boolean isLocationNotOldDefault(Location location, LocationTime howOldCanBe) {
        return System.currentTimeMillis() - location.getTime() < howOldCanBe.getTimeUnit().toMillis(howOldCanBe.getValue());
    }

    private static class RxLocationListener implements Observable.OnSubscribe<Location> {

        private final LocationManager locationManager;
        private final String provider;
        private final boolean throwExceptionIfDisabled;

        private LocationListener locationListener;

        public RxLocationListener(LocationManager locationManager, String provider, boolean throwExceptionIfDisabled) {
            this.locationManager = locationManager;
            this.provider = provider;
            this.throwExceptionIfDisabled = throwExceptionIfDisabled;
        }

        @Override
        public void call(final Subscriber<? super Location> subscriber) {
            if (locationManager.isProviderEnabled(provider)) {
                locationListener = new LocationListener() {
                    @Override
                    public void onLocationChanged(Location location) {
                        if (subscriber != null && !subscriber.isUnsubscribed()) {
                            subscriber.onNext(location);
                            subscriber.onCompleted();
                        }
                    }

                    @Override
                    public void onStatusChanged(String provider, int status, Bundle extras) {

                    }

                    @Override
                    public void onProviderEnabled(String provider) {

                    }

                    @Override
                    public void onProviderDisabled(String provider) {
                        if (RxLocationListener.this.provider.equals(provider)) {
                            subscriber.onError(new ProviderDisabledException(provider));
                        }
                    }
                };
                try {
                    locationManager.requestSingleUpdate(provider, locationListener, null);
                } catch (SecurityException ex) {
                    throw ex;
                }
                subscriber.add(new Subscription() {
                    @Override
                    public void unsubscribe() {
                        if (!subscriber.isUnsubscribed()) {
                            subscriber.unsubscribe();
                        }

                        removeUpdates();
                    }

                    @Override
                    public boolean isUnsubscribed() {
                        return subscriber.isUnsubscribed();
                    }
                });
            } else {

                if (throwExceptionIfDisabled) {
                    subscriber.onError(new ProviderDisabledException(provider));
                } else {
                    subscriber.onCompleted();
                }
            }
        }

        void removeUpdates() {
            if (locationListener != null) {
                try {
                    locationManager.removeUpdates(locationListener);
                } catch (SecurityException ex) {
                    throw ex;
                }
            }
        }
    }
}
