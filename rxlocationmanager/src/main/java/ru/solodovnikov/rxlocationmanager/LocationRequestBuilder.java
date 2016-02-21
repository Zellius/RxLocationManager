package ru.solodovnikov.rxlocationmanager;

import android.content.Context;
import android.location.Location;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeoutException;

import ru.solodovnikov.rxlocationmanager.error.ElderLocationException;
import ru.solodovnikov.rxlocationmanager.error.ProviderDisabledException;
import rx.Observable;
import rx.android.schedulers.AndroidSchedulers;
import rx.functions.Func1;

public class LocationRequestBuilder {

    private final List<Observable<Location>> observables = new ArrayList<>();
    private final RxLocationManager rxLocationManager;

    private Location defaultLocation;

    private boolean returnDefaultLocationOnError = false;

    public LocationRequestBuilder(Context context) {
        this.rxLocationManager = new RxLocationManager(context);
    }

    LocationRequestBuilder(RxLocationManager rxLocationManager) {
        this.rxLocationManager = rxLocationManager;
    }

    /**
     * Try to get current location by specific provider
     *
     * @param provider    provider name
     * @param timeOut     optional request timeout
     * @param transformer optional extra transformer
     * @return same builder
     */
    public LocationRequestBuilder addRequestLocation(@NonNull String provider, @Nullable LocationTime timeOut, @Nullable Observable.Transformer<Location, Location> transformer) {

        final Observable<Location> observable = rxLocationManager.requestLocation(provider, timeOut, false)
                .onErrorResumeNext(new Func1<Throwable, Observable<? extends Location>>() {
                    @Override
                    public Observable<? extends Location> call(Throwable throwable) {
                        if (throwable instanceof TimeoutException) {
                            return Observable.empty();
                        }
                        return Observable.error(throwable);
                    }
                });

        observables.add(transformer != null ? observable.compose(transformer) : observable);

        return this;
    }

    /**
     * Try to get current location by specific provider
     *
     * @param provider provider name
     * @param timeOut  optional request timeout
     * @return same builder
     */
    public LocationRequestBuilder addRequestLocation(@NonNull String provider, @Nullable LocationTime timeOut) {
        return addRequestLocation(provider, timeOut, null);
    }

    /**
     * Try to get current location by specific provider
     *
     * @param provider provider name
     * @return same builder
     */
    public LocationRequestBuilder addRequestLocation(@NonNull String provider) {
        return addRequestLocation(provider, null);
    }


    /**
     * Get last location from specific provider
     *
     * @param provider    provider name
     * @param howOldCanBe optional. How old a location can be
     * @param transformer optional extra transformer
     * @return same builder
     */
    public LocationRequestBuilder addLastLocation(@NonNull String provider, @Nullable LocationTime howOldCanBe, final boolean isNullValid, @Nullable Observable.Transformer<Location, Location> transformer) {

        Observable<Location> observable = rxLocationManager.getLastLocation(provider, howOldCanBe)
                .filter(new Func1<Location, Boolean>() {
                    @Override
                    public Boolean call(Location location) {
                        if (!isNullValid && location == null) {
                            return false;
                        }
                        return true;
                    }
                })
                .onErrorResumeNext(new Func1<Throwable, Observable<? extends Location>>() {
                    @Override
                    public Observable<? extends Location> call(Throwable throwable) {
                        if (throwable instanceof ElderLocationException) {
                            return Observable.empty();
                        }
                        if (throwable instanceof ProviderDisabledException) {
                            return Observable.empty();
                        }
                        return Observable.error(throwable);
                    }
                });

        observables.add(transformer != null ? observable.compose(transformer) : observable);

        return this;
    }

    /**
     * Get last location from specific provider
     *
     * @param provider    provider name
     * @param howOldCanBe optional. How old a location can be
     * @param isNullValid emit null value?
     * @return same builder
     */
    public LocationRequestBuilder addLastLocation(@NonNull String provider, @Nullable LocationTime howOldCanBe, final boolean isNullValid) {
        return addLastLocation(provider, howOldCanBe, isNullValid, null);
    }

    /**
     * Get last location from specific provider
     *
     * @param provider    provider name
     * @param isNullValid emit null value?
     * @return same builder
     */
    public LocationRequestBuilder addLastLocation(@NonNull String provider, boolean isNullValid) {
        return addLastLocation(provider, null, isNullValid);
    }

    /**
     * Set location that will be returned in case of empty observable
     *
     * @param defaultLocation default location
     * @return same builder
     */
    public LocationRequestBuilder setDefaultLocation(@Nullable Location defaultLocation) {
        this.defaultLocation = defaultLocation;

        return this;
    }

    /**
     * If returnDefaultLocationOnError is true, result observable will emit default location if any exception occur
     *
     * @param returnDefaultLocationOnError emit default location if any exception occur?
     * @return same builder
     */
    public LocationRequestBuilder setReturnDefaultLocationOnError(boolean returnDefaultLocationOnError) {
        this.returnDefaultLocationOnError = returnDefaultLocationOnError;

        return this;
    }

    /**
     * Create result observable
     *
     * @return result observable
     */
    @NonNull
    public Observable<Location> create() {

        Observable<Location> result = Observable.empty();

        for (Observable<Location> observable : observables) {
            if (result == null) {
                result = observable;
            } else {
                result = result.concatWith(observable.compose(new Observable.Transformer<Location, Location>() {
                    @Override
                    public Observable<Location> call(Observable<Location> observable) {
                        return observable.onErrorResumeNext(new Func1<Throwable, Observable<? extends Location>>() {
                            @Override
                            public Observable<? extends Location> call(Throwable throwable) {
                                if (returnDefaultLocationOnError) {
                                    return Observable.empty();
                                }
                                return Observable.error(throwable);
                            }
                        });
                    }
                }));
            }
        }

        return result.firstOrDefault(defaultLocation)
                .observeOn(AndroidSchedulers.mainThread());
    }
}
