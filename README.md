# RxLocationManager
[![Android Arsenal](https://img.shields.io/badge/Android%20Arsenal-RxLocationManager-orange.svg?style=flat)](http://android-arsenal.com/details/1/3291) [![Build Status](https://travis-ci.org/Zellius/RxLocationManager.svg?branch=master)](https://travis-ci.org/Zellius/RxLocationManager) [![Coverage Status](https://coveralls.io/repos/github/Zellius/RxLocationManager/badge.svg?branch=master&bust=1)](https://coveralls.io/github/Zellius/RxLocationManager?branch=master) ![Android Version](https://img.shields.io/badge/android-9+-blue.svg)

Android library that helps to get location using standart LocationManager, [RxJava](https://github.com/ReactiveX/RxJava) (1 and 2) and [Kotlin](https://kotlinlang.org/). It does not use the Google Play Services and it's easier to use.

## Features
 * The library have rxJava and rxJava2 implementations. It is writen in Kotlin, so you can use it in your projects with full language support.
 * Get last known device location from any location provider. You can specify how old Location can be. For example you want only those locations that have been received up to 30 minutes ago.
 * Request current device location. You can specify how long the request will continue. After that Observable will be unsubscribed automatically.
 * You can use LocationRequestBuilder to build sequence of location requests.
 * All methods will return an rxJava objects (Single, Maybe, etc.), so you can perform transofrm/map... etc methods on it.
 * No need to check is Google Play Services valid or installed on device. 

## Samples
Both rxJava and rxJava2 implementations have same interface but have some differences (like rxJava2 implementation can't emil _null_ value).

If you want to know how to use it on Java/Kotlin Android project with rxJava/rxJava2 support, please look at any **sample** projects.

#### Get last known location
###### rxJava1:
* [Single](http://reactivex.io/RxJava/1.x/javadoc/rx/Single.html);
* It will emit null if there is no last location;
* It can emit ElderLocationException if location is too old.
###### rxJava2:
* [Maybe](http://reactivex.io/RxJava/2.x/javadoc/io/reactivex/Maybe.html);
* It will not emit any value if there is no last location;
* It can emit ElderLocationException if location is too old.

```java
/**
* Get last known location from network provider (You can set any other).
* It will emit only those locations that have been received up to hour ago
*/
final RxLocationManager rxLocationManager = new RxLocationManager(this);
rxLocationManager.getLastLocation(LocationManager.NETWORK_PROVIDER, LocationTime.OneHour()).subscribe();
```
#### Request location
###### rxJava1:
* [Single](http://reactivex.io/RxJava/1.x/javadoc/rx/Single.html)
###### rxJava2:
* [Single](http://reactivex.io/RxJava/2.x/javadoc/io/reactivex/Single.html)

```java
/**
*Request current location with timeout. 
*It can emit ProviderDisabledException in case of timeout.
*/
final RxLocationManager rxLocationManager = new RxLocationManager(this);
rxLocationManager.requestLocation(LocationManager.NETWORK_PROVIDER, new LocationTime(10, TimeUnit.SECONDS)).subscribe();
```
#### LocationRequestBuilder
###### rxJava1:
* [Single](http://reactivex.io/RxJava/1.x/javadoc/rx/Single.html)
* It will emit _null_ if a result is empty and the defaultLocations is _null_ too.
###### rxJava2:
* [Maybe](http://reactivex.io/RxJava/2.x/javadoc/io/reactivex/Maybe.html);
* It will not emit any value if a result is empty and the defaultLocations is _null_. 
```java
/**
* 1. Try to get valid last known location
* 2. If last known location is not valid, try to get current location from GPS
* 3. Emit a default location if no location was emitted.
*/
final LocationRequestBuilder locationRequestBuilder = new LocationRequestBuilder(this);
locationRequestBuilder.addLastLocation(LocationManager.NETWORK_PROVIDER, new LocationTime(30, TimeUnit.SECONDS), false)
                .addRequestLocation(LocationManager.GPS_PROVIDER, new LocationTime(10, TimeUnit.SECONDS))
                .setDefaultLocation(new Location(LocationManager.PASSIVE_PROVIDER))
                .create()
                .subscribe();
```
> **Note:** By default the LocationRequestBuilder will ignore any library exceptions, but will throw any other. You can use a transformer to change it. The code below will ignore any error.
```java
addLastLocation(LocationManager.NETWORK_PROVIDER, new LocationTime(30, TimeUnit.MINUTES), new IgnoreErrorTransformer(null))
```
## Download
##### rxJava1
###### Maven:
```xml
<dependency>
  <groupId>com.github.zellius</groupId>
  <artifactId>rxlocationmanager</artifactId>
  <version>x.y.z</version>
</dependency>
```
###### Gradle:
```gradle
compile 'com.github.zellius:rxlocationmanager:x.y.z'
```
##### rxJava2
###### Maven:
```xml
<dependency>
  <groupId>com.github.zellius</groupId>
  <artifactId>rxlocationmanager-rxjava2</artifactId>
  <version>x.y.z</version>
</dependency>
```
###### Gradle:
```gradle
compile 'com.github.zellius:rxlocationmanager-rxjava2:x.y.z'
```
## Setup
Add the necessary permissions to your app manifest.xml.
```xml
<uses-permission android:name="android.permission.ACCESS_COARSE_LOCATION" />
<uses-permission android:name="android.permission.ACCESS_FINE_LOCATION" />
```
## License

```
The MIT License (MIT)

Copyright (c) 2017 Sergey Solodovnikov

Permission is hereby granted, free of charge, to any person obtaining a copy
of this software and associated documentation files (the "Software"), to deal
in the Software without restriction, including without limitation the rights
to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
copies of the Software, and to permit persons to whom the Software is
furnished to do so, subject to the following conditions:

The above copyright notice and this permission notice shall be included in all
copies or substantial portions of the Software.

THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
SOFTWARE.
```
