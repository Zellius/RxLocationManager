package ru.solodovnikov.rxlocationmanager;

import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.os.Build;
import android.os.Looper;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.MockitoAnnotations;
import org.mockito.invocation.InvocationOnMock;
import org.mockito.stubbing.Answer;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import ru.solodovnikov.rxlocationmanager.error.ElderLocationException;
import ru.solodovnikov.rxlocationmanager.error.ProviderDisabledException;
import rx.Observable;
import rx.Scheduler;
import rx.observers.TestSubscriber;
import rx.schedulers.Schedulers;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = Build.VERSION_CODES.JELLY_BEAN)
public class RxLocationManagerTest {

    private final Scheduler scheduler = Schedulers.immediate();

    @Mock
    private LocationManager locationManager;

    private final String provider = LocationManager.NETWORK_PROVIDER;

    @Before
    public void setUp() {
        MockitoAnnotations.initMocks(this);
    }

    /**
     * Test that all fine
     *
     * @throws SecurityException
     */
    @Test
    public void getLastLocation_Success() throws SecurityException {
        final Location expectedLocation = buildFakeLocation(provider);

        Mockito.when(locationManager.getLastKnownLocation(provider)).thenReturn(expectedLocation);

        final RxLocationManager rxLocationManager = getDefaullRxLocationManager();

        final TestSubscriber<Location> subscriber = new TestSubscriber<>();
        rxLocationManager.getLastLocation(provider).subscribe(subscriber);
        subscriber.awaitTerminalEvent();
        subscriber.assertCompleted();
        subscriber.assertValue(expectedLocation);
    }

    /**
     * Test that getLastLocation throw ElderLocationException if howOldCanBe is provided
     *
     * @throws SecurityException
     */
    @Test
    public void getLastLocation_Old() throws SecurityException {
        final Location expectedLocation = buildFakeLocation(provider);
        expectedLocation.setTime(System.currentTimeMillis() - TimeUnit.HOURS.toMillis(1));

        Mockito.when(locationManager.getLastKnownLocation(provider)).thenReturn(expectedLocation);

        final RxLocationManager rxLocationManager = getDefaullRxLocationManager();

        final TestSubscriber<Location> subscriber = new TestSubscriber<>();
        rxLocationManager.getLastLocation(provider, new LocationTime(30, TimeUnit.MINUTES)).subscribe(subscriber);
        subscriber.awaitTerminalEvent();
        subscriber.assertError(ElderLocationException.class);
    }

    @Test
    public void requestLocation_Success() throws SecurityException {
        final Location expectedLocation = buildFakeLocation(provider);

        //set provider enabled
        setIsProviderEnabled(provider, true);

        //answer
        Mockito.doAnswer(new Answer() {
            @Override
            public Object answer(InvocationOnMock invocation) throws Throwable {
                final Object[] args = invocation.getArguments();
                final LocationListener l = (LocationListener) args[1];
                l.onLocationChanged(expectedLocation);
                return null;
            }
        }).when(locationManager)
                .requestSingleUpdate(Mockito.eq(provider), Mockito.any(LocationListener.class), (Looper) Mockito.isNull());

        final RxLocationManager rxLocationManager = getDefaullRxLocationManager();

        final TestSubscriber<Location> subscriber = new TestSubscriber<>();
        rxLocationManager.requestLocation(provider).subscribe(subscriber);
        subscriber.awaitTerminalEvent(10, TimeUnit.SECONDS);
        subscriber.assertNoErrors();
        subscriber.assertCompleted();
        subscriber.assertValue(expectedLocation);
    }

    /**
     * Test that request location throw Exception if provider disabled
     */
    @Test
    public void requestLocation_ProviderDisabled() {
        //set provider disabled
        setIsProviderEnabled(provider, false);

        final RxLocationManager rxLocationManager = getDefaullRxLocationManager();

        final TestSubscriber<Location> subscriber = new TestSubscriber<>();
        rxLocationManager.requestLocation(provider).subscribe(subscriber);
        subscriber.awaitTerminalEvent();
        subscriber.assertError(ProviderDisabledException.class);
    }

    /**
     * Test that request location throw TimeOutException
     */
    @Test
    public void requestLocation_TimeOutError() {
        //set provider enabled
        setIsProviderEnabled(provider, true);

        final RxLocationManager rxLocationManager = getDefaullRxLocationManager();

        final TestSubscriber<Location> subscriber = new TestSubscriber<>();
        rxLocationManager.requestLocation(provider, new LocationTime(5, TimeUnit.SECONDS)).subscribe(subscriber);
        subscriber.awaitTerminalEvent();
        subscriber.assertError(TimeoutException.class);
    }

    @Test
    public void builder_Success() throws SecurityException {
        final Location location1 = buildFakeLocation(provider);

        final LocationRequestBuilder locationRequestBuilder = getDefaultLocationRequestBuilder();

        final Observable<Location> createdObservable = locationRequestBuilder.addLastLocation(provider, false)
                .addRequestLocation(provider, new LocationTime(5, TimeUnit.SECONDS))
                .setDefaultLocation(location1)
                .create();

        //set provider enabled
        setIsProviderEnabled(provider, true);

        Mockito.when(locationManager.getLastKnownLocation(provider)).thenReturn(null);

        final TestSubscriber<Location> subscriber = new TestSubscriber<>();
        createdObservable.subscribe(subscriber);
        subscriber.awaitTerminalEvent();
        subscriber.assertCompleted();
        subscriber.assertValue(location1);
    }

    /**
     * Return null if no default location is setted and no value was emitted
     *
     * @throws SecurityException
     */
    @Test
    public void builder_Success2() throws SecurityException {
        final Location location1 = buildFakeLocation(provider);
        location1.setTime(System.currentTimeMillis() - TimeUnit.HOURS.toMillis(1));

        final LocationRequestBuilder locationRequestBuilder = getDefaultLocationRequestBuilder();

        final Observable<Location> createdObservable = locationRequestBuilder.addLastLocation(provider, new LocationTime(10, TimeUnit.MINUTES), false)
                .addRequestLocation(provider, new LocationTime(5, TimeUnit.SECONDS))
                .create();

        //set provider enabled
        setIsProviderEnabled(provider, true);

        Mockito.when(locationManager.getLastKnownLocation(provider)).thenReturn(location1);

        final TestSubscriber<Location> subscriber = new TestSubscriber<>();
        createdObservable.subscribe(subscriber);
        subscriber.awaitTerminalEvent();
        subscriber.assertValue(null);
    }

    @Test
    public void builder_Success3() throws SecurityException {
        final Location location1 = buildFakeLocation(provider);

        final LocationRequestBuilder locationRequestBuilder = getDefaultLocationRequestBuilder();

        final Observable<Location> createdObservable = locationRequestBuilder
                .setDefaultLocation(location1)
                .create();

        final TestSubscriber<Location> subscriber = new TestSubscriber<>();
        createdObservable.subscribe(subscriber);
        subscriber.awaitTerminalEvent();
        subscriber.assertValue(location1);
    }

    private void setIsProviderEnabled(String provider, boolean isEnabled) {
        Mockito.when(locationManager.isProviderEnabled(provider)).thenReturn(isEnabled);
    }

    private RxLocationManager getDefaullRxLocationManager()
    {
        return new RxLocationManager(locationManager, scheduler);
    }

    private LocationRequestBuilder getDefaultLocationRequestBuilder()
    {
        return new LocationRequestBuilder(getDefaullRxLocationManager(), scheduler);
    }

    private Location buildFakeLocation(String provider) {
        final Location location = new Location(provider);
        location.setLatitude(50.0d);
        location.setLongitude(30.0d);

        return location;
    }

}