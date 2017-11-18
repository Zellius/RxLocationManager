package ru.solodovnikov.rxlocationmanager

import android.content.Context
import android.location.*
import android.os.Build
import com.nhaarman.mockito_kotlin.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.Mock
import org.mockito.MockitoAnnotations
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import rx.schedulers.Schedulers
import java.util.concurrent.TimeUnit
import kotlin.test.assertTrue

@RunWith(RobolectricTestRunner::class)
@Config(sdk = intArrayOf(Build.VERSION_CODES.N))
class RxLocationManagerTestN {
    @Mock
    lateinit var context: Context
    @Mock
    lateinit var locationManager: LocationManager

    val defaultRxLocationManager by lazy { RxLocationManager(context, Schedulers.immediate()) }

    @Before
    fun setUp() {
        MockitoAnnotations.initMocks(this)
        whenever(context.getSystemService(eq(Context.LOCATION_SERVICE)))
                .thenReturn(locationManager)
    }

    /**
     * Test [RxLocationManager.addGnssStatusListener]
     */
    @Test
    fun addGnssStatusListener_success() {
        val ttffMillis = 20
        val status0 = mock<GnssStatus>()
        val status1 = mock<GnssStatus>()

        whenever(locationManager.registerGnssStatusCallback(any())).thenReturn(true)

        defaultRxLocationManager.addGnssStatusListener()
                .test()
                .also { o ->
                    o.assertNoTerminalEvent()

                    val callback = argumentCaptor<GnssStatus.Callback>().run {
                        verify(locationManager, times(1)).registerGnssStatusCallback(capture())
                        firstValue
                    }.apply {
                        onStarted()
                        onStopped()
                        onFirstFix(ttffMillis)
                        onSatelliteStatusChanged(status0)
                        onSatelliteStatusChanged(status1)
                    }

                    o.awaitTerminalEvent(1, TimeUnit.MILLISECONDS)

                    o.assertNoErrors()
                            .assertNotCompleted()
                            .assertValueCount(5)
                            .apply {
                                val values = onNextEvents
                                assertTrue { values[0] is BaseRxLocationManager.GnssStatusResponse.GnssStarted }
                                assertTrue { values[1] is BaseRxLocationManager.GnssStatusResponse.GnssStopped }
                                values[2].also {
                                    assertTrue { it is BaseRxLocationManager.GnssStatusResponse.GnssFirstFix && it.ttffMillis == ttffMillis }
                                }
                                values[3].also {
                                    assertTrue { it is BaseRxLocationManager.GnssStatusResponse.GnssSatelliteStatusChanged && it.status == status0 }
                                }
                                values[4].also {
                                    assertTrue { it is BaseRxLocationManager.GnssStatusResponse.GnssSatelliteStatusChanged && it.status == status1 }
                                }
                            }.unsubscribe()

                    verify(locationManager, times(1)).unregisterGnssStatusCallback(callback)
                }
    }

    /**
     * Test whenever [RxLocationManager.addGnssStatusListener] throw exception if callback is not registered
     */
    @Test
    fun addGnssStatusListener_error() {
        whenever(locationManager.registerGnssStatusCallback(any())).thenReturn(false)

        defaultRxLocationManager.addGnssStatusListener()
                .test()
                .awaitTerminalEvent()
                .assertError(ListenerNotRegisteredException::class.java)
                .assertNoValues()
    }

    /**
     * Test [RxLocationManager.addNmeaListenerN]
     */
    @Test
    fun addNmeaListener_test() {
        val message = "test_message"
        val timestamp = System.currentTimeMillis()


        whenever(locationManager.addNmeaListener(any<OnNmeaMessageListener>())).thenReturn(true)

        defaultRxLocationManager.addNmeaListenerN()
                .test()
                .also { o ->
                    o.assertNoTerminalEvent()

                    val listener = argumentCaptor<OnNmeaMessageListener>().run {
                        verify(locationManager, times(1)).addNmeaListener(capture())
                        firstValue
                    }.apply {
                        onNmeaMessage(message, timestamp)
                        onNmeaMessage(message, timestamp)
                    }

                    o.assertNoErrors()
                            .assertNotCompleted()
                            .assertValueCount(2)
                            .also {
                                val values = it.onNextEvents
                                values[0].also { assertTrue { it.nmea == message && it.timestamp == timestamp } }
                                values[1].also { assertTrue { it.nmea == message && it.timestamp == timestamp } }
                            }
                            .unsubscribe()

                    verify(locationManager, times(1)).removeNmeaListener(listener)
                }
    }

    /**
     * Test whenever [RxLocationManager.addNmeaListenerN] throw exception if callback is not registered
     */
    @Test
    fun addNmeaListener_error() {
        whenever(locationManager.registerGnssStatusCallback(any())).thenReturn(false)

        defaultRxLocationManager.addNmeaListenerN()
                .test()
                .awaitTerminalEvent()
                .assertError(ListenerNotRegisteredException::class.java)
                .assertNoValues()
    }

    @Test
    fun registerGnssMeasurementsCallback_success() {
        val status0 = GnssMeasurementsEvent.Callback.STATUS_READY
        val status1 = GnssMeasurementsEvent.Callback.STATUS_LOCATION_DISABLED
        val event = mock<GnssMeasurementsEvent>()

        whenever(locationManager.registerGnssMeasurementsCallback(any())).thenReturn(true)

        defaultRxLocationManager.registerGnssMeasurementsCallback()
                .test()
                .also { o ->
                    o.assertNoTerminalEvent()

                    val callback = argumentCaptor<GnssMeasurementsEvent.Callback>().run {
                        verify(locationManager, times(1)).registerGnssMeasurementsCallback(capture())
                        firstValue
                    }.apply {
                        onStatusChanged(status0)
                        onStatusChanged(status1)
                        onGnssMeasurementsReceived(event)
                    }

                    o.assertNoErrors()
                            .assertNotCompleted()
                            .assertValueCount(3)
                            .apply {
                                onNextEvents[0].also { assertTrue { it is BaseRxLocationManager.GnssMeasurementsResponse.StatusChanged && it.status == status0 } }
                                onNextEvents[1].also { assertTrue { it is BaseRxLocationManager.GnssMeasurementsResponse.StatusChanged && it.status == status1 } }
                                onNextEvents[2].also { assertTrue { it is BaseRxLocationManager.GnssMeasurementsResponse.GnssMeasurementsReceived && it.eventArgs == event } }

                            }
                            .unsubscribe()

                    verify(locationManager, times(1)).unregisterGnssMeasurementsCallback(callback)
                }
    }

    @Test
    fun registerGnssMeasurementsCallback_error() {
        defaultRxLocationManager.registerGnssMeasurementsCallback()
                .test()
                .awaitTerminalEvent()
                .assertError(ListenerNotRegisteredException::class.java)
                .assertNoValues()
    }

    @Test
    fun registerGnssNavigationMessageCallback_success() {
        val status0 = GnssNavigationMessage.Callback.STATUS_READY
        val status1 = GnssNavigationMessage.Callback.STATUS_LOCATION_DISABLED
        val event = mock<GnssNavigationMessage>()

        whenever(locationManager.registerGnssNavigationMessageCallback(any())).thenReturn(true)

        defaultRxLocationManager.registerGnssNavigationMessageCallback()
                .test()
                .also { o ->
                    o.assertNoTerminalEvent()

                    val callback = argumentCaptor<GnssNavigationMessage.Callback>().run {
                        verify(locationManager, times(1)).registerGnssNavigationMessageCallback(capture())
                        firstValue
                    }.apply {
                        onStatusChanged(status0)
                        onStatusChanged(status1)
                        onGnssNavigationMessageReceived(event)
                    }

                    o.assertNoErrors()
                            .assertNotCompleted()
                            .assertValueCount(3)
                            .apply {
                                onNextEvents[0].also { assertTrue { it is BaseRxLocationManager.GnssNavigationResponse.StatusChanged && it.status == status0 } }
                                onNextEvents[1].also { assertTrue { it is BaseRxLocationManager.GnssNavigationResponse.StatusChanged && it.status == status1 } }
                                onNextEvents[2].also { assertTrue { it is BaseRxLocationManager.GnssNavigationResponse.GnssNavigationMessageReceived && it.event == event } }
                            }
                            .unsubscribe()

                    verify(locationManager, times(1)).unregisterGnssNavigationMessageCallback(callback)
                }
    }

    @Test
    fun registerGnssNavigationMessageCallback_error() {
        defaultRxLocationManager.registerGnssNavigationMessageCallback()
                .test()
                .awaitTerminalEvent()
                .assertError(ListenerNotRegisteredException::class.java)
                .assertNoValues()
    }
}