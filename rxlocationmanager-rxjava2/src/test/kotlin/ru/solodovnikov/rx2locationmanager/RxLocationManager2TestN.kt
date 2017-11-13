package ru.solodovnikov.rx2locationmanager

import android.content.Context
import android.location.GnssStatus
import android.location.LocationManager
import android.os.Build
import com.nhaarman.mockito_kotlin.*
import io.reactivex.schedulers.Schedulers
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.Mock
import org.mockito.MockitoAnnotations
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.util.concurrent.TimeUnit

@RunWith(RobolectricTestRunner::class)
@Config(sdk = intArrayOf(Build.VERSION_CODES.N))
class RxLocationManager2TestN {
    @Mock
    lateinit var context: Context
    @Mock
    lateinit var locationManager: LocationManager

    val defaultRxLocationManager by lazy { RxLocationManager(context, Schedulers.trampoline()) }

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
                    o.assertNotTerminated()

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

                    o.await(1, TimeUnit.MILLISECONDS)

                    o.assertNoErrors()
                            .assertNotComplete()
                            .assertValueCount(5)
                            .assertValueAt(0, { it is BaseRxLocationManager.GnssStatusResponse.GnssStarted })
                            .assertValueAt(1, { it is BaseRxLocationManager.GnssStatusResponse.GnssStopped })
                            .assertValueAt(2, { it is BaseRxLocationManager.GnssStatusResponse.GnssFirstFix && it.ttffMillis == ttffMillis })
                            .assertValueAt(3, { it is BaseRxLocationManager.GnssStatusResponse.GnssSatelliteStatusChanged && it.status == status0 })
                            .assertValueAt(4, { it is BaseRxLocationManager.GnssStatusResponse.GnssSatelliteStatusChanged && it.status == status1 })
                            .dispose()

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
                .await()
                .assertError(ListenerNotRegisteredException::class.java)
                .assertNoValues()
    }
}