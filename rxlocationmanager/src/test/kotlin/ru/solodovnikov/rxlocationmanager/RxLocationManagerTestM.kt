package ru.solodovnikov.rxlocationmanager

import android.Manifest
import android.annotation.TargetApi
import android.content.Context
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
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
import kotlin.test.assertEquals

@RunWith(RobolectricTestRunner::class)
@Config(sdk = intArrayOf(Build.VERSION_CODES.M))
@TargetApi(Build.VERSION_CODES.M)
class RxLocationManagerTestM {
    private val networkProvider = LocationManager.NETWORK_PROVIDER

    @Mock
    lateinit var context: Context
    @Mock
    lateinit var locationManager: LocationManager

    val defaultRxLocationManager by lazy { RxLocationManager(context, Schedulers.trampoline()) }

    @Before
    fun setUp() {
        MockitoAnnotations.initMocks(this)

        val stubPackageName = "com.package.name"

        val mockedAplicationContext = mock<Context> {
            on { packageName } doReturn stubPackageName
        }

        val mockedPackageManager = mock<PackageManager> {
            on { getPackageInfo(eq(stubPackageName), eq(PackageManager.GET_PERMISSIONS)) } doReturn
                    PackageInfo().apply {
                        requestedPermissions = arrayOf(Manifest.permission.ACCESS_COARSE_LOCATION,
                                Manifest.permission.ACCESS_FINE_LOCATION)
                    }
        }

        whenever(context.getSystemService(eq(Context.LOCATION_SERVICE))).thenReturn(locationManager)
        whenever(context.applicationContext).thenReturn(mockedAplicationContext)
        whenever(context.applicationContext.packageManager).thenReturn(mockedPackageManager)
    }

    @Test
    fun test_PermissionBehaviorSuccess() {
        val caller: PermissionCaller = mock()
        val location: Location = mock()

        whenever(context.applicationContext.checkSelfPermission(any()))
                .thenReturn(PackageManager.PERMISSION_DENIED)
        whenever(locationManager.getLastKnownLocation(eq(networkProvider)))
                .thenReturn(location)

        val susbcriber = defaultRxLocationManager.getLastLocation(networkProvider,
                behaviors = PermissionBehavior(context, defaultRxLocationManager, caller))
                .test()

        Thread.sleep(100L)

        argumentCaptor<String>().apply {
            verify(context.applicationContext, times(2)).checkSelfPermission(capture())
            val permissions = allValues
            assertEquals(2, allValues.size)
            assert(permissions.contains(Manifest.permission.ACCESS_COARSE_LOCATION))
            assert(permissions.contains(Manifest.permission.ACCESS_FINE_LOCATION))
            verify(caller, only()).requestPermissions(permissions.toTypedArray())

            defaultRxLocationManager.onRequestPermissionsResult(permissions.toTypedArray(),
                    intArrayOf(PackageManager.PERMISSION_GRANTED, PackageManager.PERMISSION_GRANTED))

            susbcriber.awaitTerminalEvent()
                    .assertNoErrors()
                    .assertCompleted()
                    .assertValue(location)
        }
    }

    @Test
    fun test_PermissionTransformerDenied() {
        val caller: PermissionCaller = mock()

        whenever(context.applicationContext.checkSelfPermission(any()))
                .thenReturn(PackageManager.PERMISSION_DENIED)

        val susbcriber = defaultRxLocationManager.getLastLocation(networkProvider,
                behaviors = PermissionBehavior(context, defaultRxLocationManager, caller))
                .test()

        Thread.sleep(100L)

        argumentCaptor<String>().apply {
            verify(context.applicationContext, times(2)).checkSelfPermission(capture())
            val permissions = allValues
            assertEquals(2, allValues.size)
            assert(permissions.contains(Manifest.permission.ACCESS_COARSE_LOCATION))
            assert(permissions.contains(Manifest.permission.ACCESS_FINE_LOCATION))
            verify(caller, only()).requestPermissions(permissions.toTypedArray())

            defaultRxLocationManager.onRequestPermissionsResult(permissions.toTypedArray(),
                    intArrayOf(PackageManager.PERMISSION_GRANTED, PackageManager.PERMISSION_DENIED))

            susbcriber.awaitTerminalEvent()
                    .assertError(SecurityException::class.java)
        }
    }

    @Test
    fun test_PermissionTransformerBuilder() {
        val caller: PermissionCaller = mock()
        val locationBuilder = LocationRequestBuilder(defaultRxLocationManager)
        val location: Location = mock()

        whenever(context.applicationContext.checkSelfPermission(any()))
                .thenReturn(PackageManager.PERMISSION_DENIED)
                .thenReturn(PackageManager.PERMISSION_DENIED)
                .thenReturn(PackageManager.PERMISSION_GRANTED)
                .thenReturn(PackageManager.PERMISSION_GRANTED)

        whenever(locationManager.isProviderEnabled(eq(networkProvider)))
                .thenReturn(true)

        doAnswer {
            val args = it.arguments
            val locationListener = args[1] as LocationListener
            locationListener.onLocationChanged(location)
            return@doAnswer null
        }.whenever(locationManager).requestSingleUpdate(eq(networkProvider), any(), isNull())

        val permissionTransformer = PermissionBehavior(context, defaultRxLocationManager, caller)

        val susbcriber = locationBuilder.addLastLocation(networkProvider, behaviors = permissionTransformer)
                .addRequestLocation(networkProvider, behaviors = permissionTransformer)
                .create()
                .test()

        Thread.sleep(100L)

        argumentCaptor<String>().apply {
            verify(context.applicationContext, times(2)).checkSelfPermission(capture())
            val permissions = allValues
            assertEquals(2, allValues.size)
            assert(permissions.contains(Manifest.permission.ACCESS_COARSE_LOCATION))
            assert(permissions.contains(Manifest.permission.ACCESS_FINE_LOCATION))
            verify(caller, only()).requestPermissions(permissions.toTypedArray())

            defaultRxLocationManager.onRequestPermissionsResult(permissions.toTypedArray(),
                    intArrayOf(PackageManager.PERMISSION_GRANTED, PackageManager.PERMISSION_GRANTED))

            susbcriber.awaitTerminalEvent()
                    .assertNoErrors()
                    .assertCompleted()
                    .assertValue(location)

            verify(caller, only()).requestPermissions(permissions.toTypedArray())
        }
    }
}