package cn.edu.ubaa.plus.api.platform

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import cn.edu.ubaa.plus.model.dto.LibBookGeoPoint
import kotlin.coroutines.resume
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext

fun hasLocationPermission(context: Context): Boolean {
  val fine =
      context.checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) ==
          PackageManager.PERMISSION_GRANTED
  val coarse =
      context.checkSelfPermission(Manifest.permission.ACCESS_COARSE_LOCATION) ==
          PackageManager.PERMISSION_GRANTED
  return fine || coarse
}

/**
 * 获取一次设备真实定位，供图书馆座位的离开/签到校验使用。
 *
 * 先使用 60 秒内的已知位置；否则请求一次定位，超时返回最近一次已知位置或 null。 不会生成或伪造坐标。
 */
@Suppress("DEPRECATION")
suspend fun currentLocationPoint(
    context: Context,
    timeoutMs: Long = 5_000,
): LibBookGeoPoint? {
  if (!hasLocationPermission(context)) return null
  return withContext(Dispatchers.Main.immediate) {
    val manager =
        context.getSystemService(Context.LOCATION_SERVICE) as? LocationManager
            ?: return@withContext null
    val providers =
        listOf(LocationManager.NETWORK_PROVIDER, LocationManager.GPS_PROVIDER).filter { provider ->
          runCatching { manager.isProviderEnabled(provider) }.getOrDefault(false)
        }
    val lastKnown =
        providers
            .mapNotNull { provider ->
              runCatching { manager.getLastKnownLocation(provider) }.getOrNull()
            }
            .maxByOrNull { it.time }
    if (lastKnown != null && System.currentTimeMillis() - lastKnown.time <= 60_000L) {
      return@withContext lastKnown.toGeoPoint()
    }
    val provider = providers.firstOrNull() ?: return@withContext lastKnown?.toGeoPoint()

    suspendCancellableCoroutine { continuation ->
      val handler = Handler(Looper.getMainLooper())
      var completed = false
      lateinit var listener: LocationListener

      fun complete(point: LibBookGeoPoint?) {
        if (completed) return
        completed = true
        runCatching { manager.removeUpdates(listener) }
        handler.removeCallbacksAndMessages(null)
        if (continuation.isActive) continuation.resume(point)
      }

      listener =
          object : LocationListener {
            override fun onLocationChanged(location: Location) {
              complete(location.toGeoPoint())
            }

            override fun onProviderDisabled(provider: String) {}

            override fun onProviderEnabled(provider: String) {}

            override fun onStatusChanged(provider: String?, status: Int, extras: Bundle?) {}
          }

      continuation.invokeOnCancellation {
        if (!completed) {
          completed = true
          runCatching { manager.removeUpdates(listener) }
          handler.removeCallbacksAndMessages(null)
        }
      }
      handler.postDelayed({ complete(lastKnown?.toGeoPoint()) }, timeoutMs)
      runCatching { manager.requestSingleUpdate(provider, listener, Looper.getMainLooper()) }
          .onFailure { complete(lastKnown?.toGeoPoint()) }
    }
  }
}

private fun Location.toGeoPoint(): LibBookGeoPoint =
    LibBookGeoPoint(
        lat = latitude,
        lng = longitude,
        time = System.currentTimeMillis() / 1000,
    )
