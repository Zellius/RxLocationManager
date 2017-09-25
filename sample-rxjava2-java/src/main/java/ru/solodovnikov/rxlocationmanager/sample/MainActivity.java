package ru.solodovnikov.rxlocationmanager.sample;

import android.location.Location;
import android.location.LocationManager;
import android.os.Bundle;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.design.widget.CoordinatorLayout;
import android.support.design.widget.Snackbar;
import android.support.v4.app.ActivityCompat;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.Toolbar;
import android.view.Menu;
import android.view.MenuItem;

import java.util.concurrent.TimeUnit;

import io.reactivex.Maybe;
import io.reactivex.Single;
import io.reactivex.functions.Action;
import io.reactivex.functions.Consumer;
import ru.solodovnikov.rx2locationmanager.BasePermissionBehavior;
import ru.solodovnikov.rx2locationmanager.IgnoreErrorBehavior;
import ru.solodovnikov.rx2locationmanager.LocationRequestBuilder;
import ru.solodovnikov.rx2locationmanager.LocationTime;
import ru.solodovnikov.rx2locationmanager.PermissionBehavior;
import ru.solodovnikov.rx2locationmanager.RxLocationManager;

public class MainActivity extends AppCompatActivity implements BasePermissionBehavior.PermissionCallback {
    private static final int REQUEST_CODE_LOCATION_PERMISSIONS = 150;

    private RxLocationManager rxLocationManager;
    private LocationRequestBuilder locationRequestBuilder;

    private CoordinatorLayout coordinatorLayout;

    private boolean checkPermissions = false;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        rxLocationManager = new RxLocationManager(this);
        locationRequestBuilder = new LocationRequestBuilder(rxLocationManager);

        coordinatorLayout = findViewById(R.id.root);

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
            case R.id.check_permissions:
                item.setChecked(!item.isChecked());
                checkPermissions = item.isChecked();
                return true;
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
            case R.id.complicated_request_location_ignore_error:
                requestBuildIgnoreSecurityError();
                return true;
            default:
                return super.onOptionsItemSelected(item);
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == REQUEST_CODE_LOCATION_PERMISSIONS) {
            rxLocationManager.onRequestPermissionsResult(permissions, grantResults);
        }
    }

    @Override
    public void requestPermissions(@NonNull String[] permissions) {
        ActivityCompat.requestPermissions(this, permissions, REQUEST_CODE_LOCATION_PERMISSIONS);
    }

    private void requestLastNetworkLocation() {
        final Maybe<Location> rx;
        if (checkPermissions) {
            rx = rxLocationManager.getLastLocation(LocationManager.NETWORK_PROVIDER, new PermissionBehavior(this, rxLocationManager, this));
        } else {
            rx = rxLocationManager.getLastLocation(LocationManager.NETWORK_PROVIDER);
        }

        testSubscribe(rx, "requestLastNetworkLocation");
    }

    private void requestLastNetworkOneMinuteOldLocation() {
        final Maybe<Location> rx;
        if (checkPermissions) {
            rx = rxLocationManager.getLastLocation(LocationManager.NETWORK_PROVIDER, new LocationTime(1, TimeUnit.MINUTES), new PermissionBehavior(this, rxLocationManager, this));
        } else {
            rx = rxLocationManager.getLastLocation(LocationManager.NETWORK_PROVIDER, new LocationTime(1, TimeUnit.MINUTES));
        }

        testSubscribe(rx, "requestLastNetworkOneMinuteOldLocation");
    }

    private void requestLocation() {
        final Single<Location> rx;
        if (checkPermissions) {
            rx = rxLocationManager.requestLocation(LocationManager.NETWORK_PROVIDER, new LocationTime(15, TimeUnit.SECONDS), new PermissionBehavior(this, rxLocationManager, this));
        } else {
            rx = rxLocationManager.requestLocation(LocationManager.NETWORK_PROVIDER, new LocationTime(15, TimeUnit.SECONDS));
        }

        testSubscribe(rx, "requestLocation");
    }

    private void requestBuild() {
        final Maybe<Location> rx;
        if (checkPermissions) {
            final PermissionBehavior permissionBehavior = new PermissionBehavior(this, rxLocationManager, this);

            rx = locationRequestBuilder
                    .addLastLocation(LocationManager.NETWORK_PROVIDER, new LocationTime(30, TimeUnit.MINUTES), permissionBehavior)
                    .addRequestLocation(LocationManager.NETWORK_PROVIDER, new LocationTime(15, TimeUnit.SECONDS), permissionBehavior)
                    .setDefaultLocation(new Location(LocationManager.PASSIVE_PROVIDER))
                    .create();
        } else {
            rx = locationRequestBuilder
                    .addLastLocation(LocationManager.NETWORK_PROVIDER, new LocationTime(30, TimeUnit.MINUTES))
                    .addRequestLocation(LocationManager.NETWORK_PROVIDER, new LocationTime(15, TimeUnit.SECONDS))
                    .setDefaultLocation(new Location(LocationManager.PASSIVE_PROVIDER))
                    .create();
        }

        testSubscribe(rx, "requestBuild");
    }

    private void requestBuildIgnoreSecurityError() {
        final IgnoreErrorBehavior ignoreErrorBehavior = new IgnoreErrorBehavior(SecurityException.class);

        final Maybe<Location> maybe = locationRequestBuilder
                .addLastLocation(LocationManager.NETWORK_PROVIDER, new LocationTime(30, TimeUnit.MINUTES), ignoreErrorBehavior)
                .addRequestLocation(LocationManager.NETWORK_PROVIDER, new LocationTime(15, TimeUnit.SECONDS), ignoreErrorBehavior)
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
