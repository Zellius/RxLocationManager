package ru.solodovnikov.rxlocationmanager.sample;

import android.app.Activity;
import android.location.Location;
import android.location.LocationManager;
import android.os.Bundle;
import android.util.Log;

import java.util.concurrent.TimeUnit;

import ru.solodovnikov.rxlocationmanager.LocationRequestBuilder;
import ru.solodovnikov.rxlocationmanager.LocationTime;
import rx.Subscriber;

public class MainActivity extends Activity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        final LocationRequestBuilder locationRequestBuilder = new LocationRequestBuilder(this);

        locationRequestBuilder.addLastLocation(LocationManager.NETWORK_PROVIDER, new LocationTime(30, TimeUnit.SECONDS), false)
                .addRequestLocation(LocationManager.GPS_PROVIDER, new LocationTime(10, TimeUnit.SECONDS))
                .setDefaultLocation(new Location(LocationManager.PASSIVE_PROVIDER))
                .create().subscribe(new Subscriber<Location>() {
            @Override
            public void onCompleted() {

            }

            @Override
            public void onError(Throwable e) {

            }

            @Override
            public void onNext(Location location) {
                Log.d(getClass().getSimpleName(), location != null ? location.toString() : "Location is empty =(");
            }
        });
    }
}
