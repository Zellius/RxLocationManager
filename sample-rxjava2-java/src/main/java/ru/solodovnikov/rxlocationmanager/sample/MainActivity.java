package ru.solodovnikov.rxlocationmanager.sample;

import android.location.Location;
import android.location.LocationManager;
import android.os.Bundle;
import android.support.annotation.Nullable;
import android.support.design.widget.CoordinatorLayout;
import android.support.design.widget.Snackbar;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.Toolbar;
import android.view.Menu;
import android.view.MenuItem;

import java.util.concurrent.TimeUnit;

import io.reactivex.Maybe;
import io.reactivex.Single;
import io.reactivex.annotations.NonNull;
import io.reactivex.functions.Action;
import io.reactivex.functions.Consumer;
import ru.solodovnikov.rx2locationmanager.LocationRequestBuilder;
import ru.solodovnikov.rx2locationmanager.LocationTime;
import ru.solodovnikov.rx2locationmanager.RxLocationManager;

public class MainActivity extends AppCompatActivity {
    private RxLocationManager rxLocationManager;
    private LocationRequestBuilder locationRequestBuilder;

    private CoordinatorLayout coordinatorLayout;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        rxLocationManager = new RxLocationManager(this);
        locationRequestBuilder = new LocationRequestBuilder(this);

        coordinatorLayout = (CoordinatorLayout) findViewById(R.id.root);

        setSupportActionBar((Toolbar) findViewById(R.id.toolbar));
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.main, menu);
        return super.onCreateOptionsMenu(menu);
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            case R.id.last_network:
                requestLastNetworkLocation();
                return true;
            case R.id.last_network_minute_old:
                requestLastNetworkOneMinuteOldLocation();
                return true;
            case R.id.request_location:
                requestLocation();
                return true;
            case R.id.complicated_request_location:
                requestBuild();
                return true;
            default:
                return super.onOptionsItemSelected(item);
        }
    }

    private void requestLastNetworkLocation() {
        testSubscribe(rxLocationManager.getLastLocation(LocationManager.NETWORK_PROVIDER), "requestLastNetworkLocation");
    }

    private void requestLastNetworkOneMinuteOldLocation() {
        testSubscribe(rxLocationManager.getLastLocation(LocationManager.NETWORK_PROVIDER, new LocationTime(1, TimeUnit.MINUTES)), "requestLastNetworkOneMinuteOldLocation");
    }

    private void requestLocation() {
        testSubscribe(rxLocationManager.requestLocation(LocationManager.NETWORK_PROVIDER, new LocationTime(15, TimeUnit.SECONDS)), "requestLocation");
    }

    private void requestBuild() {
        final Maybe<Location> maybe = locationRequestBuilder
                .addLastLocation(LocationManager.NETWORK_PROVIDER, new LocationTime(30, TimeUnit.MINUTES))
                .addRequestLocation(LocationManager.NETWORK_PROVIDER, new LocationTime(15, TimeUnit.SECONDS))
                .setDefaultLocation(new Location(LocationManager.PASSIVE_PROVIDER))
                .create();

        testSubscribe(maybe, "requestBuild");
    }

    private void showSnackbar(CharSequence text) {
        Snackbar.make(coordinatorLayout, text, Snackbar.LENGTH_SHORT)
                .show();
    }

    private void testSubscribe(Maybe<Location> maybe, final String methodName) {
        maybe.subscribe(new Consumer<Location>() {
            @Override
            public void accept(@NonNull Location t) throws Exception {
                showLocationMessage(t, methodName);
            }
        }, new Consumer<Throwable>() {
            @Override
            public void accept(@NonNull Throwable throwable) throws Exception {
                showErrorMessage(throwable, methodName);
            }
        }, new Action() {
            @Override
            public void run() throws Exception {
                final String pattern = "%s Completed:";
                showSnackbar(String.format(pattern, methodName));
            }
        });
    }

    private void testSubscribe(Single<Location> single, final String methodName) {
        single.subscribe(new Consumer<Location>() {
            @Override
            public void accept(@NonNull Location t) throws Exception {
                showLocationMessage(t, methodName);
            }
        }, new Consumer<Throwable>() {
            @Override
            public void accept(@NonNull Throwable throwable) throws Exception {
                showErrorMessage(throwable, methodName);
            }
        });
    }

    private void showLocationMessage(Location location, String methodName) {
        final String pattern = "%s Success: %s";
        showSnackbar(String.format(pattern, methodName, location != null ? location.toString() : "Empty location"));
    }

    private void showErrorMessage(Throwable throwable, String methodName) {
        final String pattern = "%s Error: %s";
        showSnackbar(String.format(pattern, methodName, throwable.getMessage()));
    }
}
