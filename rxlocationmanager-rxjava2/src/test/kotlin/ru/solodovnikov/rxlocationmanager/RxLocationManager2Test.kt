package ru.solodovnikov.rxlocationmanager

import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Build
import io.reactivex.schedulers.Schedulers
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.Mock
import org.mockito.Mockito
import org.mockito.MockitoAnnotations
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.util.concurrent.TimeUnit
import java.util.concurrent.TimeoutException

@RunWith(RobolectricTestRunner::class)
@Config(sdk = intArrayOf(Build.VERSION_CODES.JELLY_BEAN))
class RxLocationManager2Test {
    private val networkProvider = LocationManager.NETWORK_PROVIDER
    private val scheduler = Schedulers.trampoline()

    @Mock
    lateinit var locationManager: LocationManager

    @Before
    fun setUp() {
        MockitoAnnotations.initMocks(this)
    }

    /**
     * Test that all fine
     */
    @Test
    fun getLastLocation_Success() {
        val expectedLocation = buildFakeLocation()

        Mockito.`when`(locationManager.getLastKnownLocation(networkProvider)).thenReturn(expectedLocation)

        val rxLocationManager = RxLocationManager(locationManager, scheduler)

        rxLocationManager.getLastLocation(networkProvider)
                .test()
                .await()
                .assertNoErrors()
                .assertComplete()
                .assertValue(expectedLocation)
    }

    /**
     * Test that getLastLocation throw [ElderLocationException] if howOldCanBe is provided
     *
     */
    @Test
    fun getLastLocation_Old() {
        val expectedLocation = buildFakeLocation()
        expectedLocation.time = System.currentTimeMillis() - TimeUnit.HOURS.toMillis(1)

        Mockito.`when`(locationManager.getLastKnownLocation(networkProvider)).thenReturn(expectedLocation)

        val rxLocationManager = getDefaultRxLocationManager()

        rxLocationManager.getLastLocation(networkProvider, LocationTime(30, TimeUnit.MINUTES))
                .test()
                .await()
                .assertError(ElderLocationException::class.java)
    }

    /**
     * Test that getLastLocation throw [ProviderHasNoLastLocationException] if locationManager emit null
     */
    @Test
    fun getLastLocation_NoLocation() {
        Mockito.`when`(locationManager.getLastKnownLocation(networkProvider)).thenReturn(null)

        getDefaultRxLocationManager().getLastLocation(networkProvider)
                .test()
                .await()
                .assertNoErrors()
                .assertComplete()
                .assertNoValues()
    }

    @Test
    fun requestLocation_Success() {
        val expectedLocation = buildFakeLocation()

        //set provider enabled
        setIsProviderEnabled(isEnabled = true)

        //answer
        Mockito.doAnswer {
            val args = it.arguments
            val locationListener = args[1] as LocationListener
            locationListener.onLocationChanged(expectedLocation)
            return@doAnswer null
        }.`when`(locationManager).requestSingleUpdate(Mockito.eq(networkProvider), Mockito.any(), Mockito.any())

        val rxLocationManager = getDefaultRxLocationManager()

        rxLocationManager.requestLocation(networkProvider)
                .test()
                .await()
                .assertNoErrors()
                .assertComplete()
                .assertValue(expectedLocation)
    }

    /**
     * Test that request location throw Exception if provider disabled
     */
    @Test
    fun requestLocation_ProviderDisabled() {
        //set provider disabled
        setIsProviderEnabled(isEnabled = false)

        val rxLocationManager = getDefaultRxLocationManager()

        rxLocationManager.requestLocation(networkProvider)
                .test()
                .await()
                .assertError(ProviderDisabledException::class.java)
    }

    /**
     * Test that request location throw TimeOutException
     */
    @Test
    fun requestLocation_TimeOutError() {
        //set provider enabled
        setIsProviderEnabled(isEnabled = true)

        val rxLocationManager = getDefaultRxLocationManager()

        rxLocationManager.requestLocation(networkProvider, LocationTime(5, TimeUnit.SECONDS))
                .test()
                .await()
                .assertError(TimeoutException::class.java)
    }

    @Test
    fun builder_Success() {
        val location = buildFakeLocation()

        val locationRequestBuilder = getDefaultLocationRequestBuilder()

        val createdObservable = locationRequestBuilder.addLastLocation(provider = networkProvider)
                .addRequestLocation(provider = networkProvider, timeOut = LocationTime(5, TimeUnit.SECONDS))
                .setDefaultLocation(location)
                .create()

        //set provider enabled
        setIsProviderEnabled(isEnabled = true)

        Mockito.`when`(locationManager.getLastKnownLocation(networkProvider)).thenReturn(null)

        createdObservable.test()
                .await()
                .assertNoErrors()
                .assertComplete()
                .assertValue(location)
    }

    /**
     * Return null if no default location is setted and no value was emitted
     */
    @Test
    fun builder_Success2() {
        val location1 = buildFakeLocation()
        location1.time = System.currentTimeMillis() - TimeUnit.HOURS.toMillis(1)

        val locationRequestBuilder = getDefaultLocationRequestBuilder()

        val createdObservable = locationRequestBuilder.addLastLocation(provider = networkProvider, howOldCanBe = LocationTime(10, TimeUnit.MINUTES))
                .addRequestLocation(provider = networkProvider, timeOut = LocationTime(5, TimeUnit.SECONDS))
                .create()

        //set provider enabled
        setIsProviderEnabled(isEnabled = true)

        Mockito.`when`(locationManager.getLastKnownLocation(networkProvider)).thenReturn(location1)

        createdObservable.test()
                .await()
                .assertNoValues()
    }

    @Test
    fun builder_Success3() {
        val location1 = buildFakeLocation()

        val locationRequestBuilder = getDefaultLocationRequestBuilder()

        val createdObservable = locationRequestBuilder.setDefaultLocation(location1).create()

        createdObservable.test()
                .await()
                .assertValue(location1)
    }

    private fun setIsProviderEnabled(provider: String = networkProvider, isEnabled: Boolean = false) {
        Mockito.`when`(locationManager.isProviderEnabled(provider)).thenReturn(isEnabled)
    }

    private fun getDefaultRxLocationManager() = RxLocationManager(locationManager, scheduler)

    private fun getDefaultLocationRequestBuilder() = LocationRequestBuilder(getDefaultRxLocationManager(), scheduler)

    private fun buildFakeLocation(provider: String = networkProvider): Location {
        val location = Location(provider)
        location.latitude = 50.0
        location.longitude = 30.0

        return location
    }
}