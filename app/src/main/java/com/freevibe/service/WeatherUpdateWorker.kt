package com.freevibe.service

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.location.LocationManager
import androidx.core.content.ContextCompat
import androidx.hilt.work.HiltWorker
import androidx.work.*
import com.freevibe.data.remote.weather.OpenMeteoApi
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlin.math.round
import java.util.concurrent.TimeUnit

/**
 * Periodic worker that fetches current weather from Open-Meteo
 * and stores the weather effect + wind speed in SharedPreferences
 * for WeatherWallpaperService to read.
 */
@HiltWorker
class WeatherUpdateWorker @AssistedInject constructor(
    @Assisted appContext: Context,
    @Assisted workerParams: WorkerParameters,
    private val openMeteoApi: OpenMeteoApi,
    private val receiptStore: BackgroundWorkReceiptStore,
) : CoroutineWorker(appContext, workerParams) {

    override suspend fun doWork(): Result {
        return try {
            val location = getLastKnownLocation() ?: return successReceipt() // No location = skip

            val response = openMeteoApi.getCurrentWeather(
                latitude = location.first,
                longitude = location.second,
            )

            val weather = response.currentWeather ?: return successReceipt()

            // Store only coarse coordinates for WeatherWallpaperService. Two decimal
            // places preserve enough regional context for weather/adaptive tint while
            // avoiding exact last-known-location retention.
            applicationContext.getSharedPreferences("freevibe_weather_wp", Context.MODE_PRIVATE)
                .edit()
                .putString("weather_effect", weather.weatherEffect.name)
                .putFloat("wind_speed", weather.windSpeed.toFloat())
                .putFloat("temperature", weather.temperature.toFloat())
                .putInt("is_day", weather.isDay)
                .putFloat("location_lat", roundWeatherCoordinate(location.first))
                .putFloat("location_lon", roundWeatherCoordinate(location.second))
                // Sentinel so a missing-location read in the service can be distinguished
                // from a legitimate (0.0, 0.0) reading on Null Island.
                .putBoolean("location_present", true)
                .apply()

            successReceipt()
        } catch (_: java.io.IOException) {
            receiptStore.recordRetry(
                uniqueWorkName = WORK_NAME,
                errorClass = "IOException",
                deferralReason = "weather endpoint or network I/O failed; check connection and Open-Meteo availability",
            )
            Result.retry()
        } catch (e: Exception) {
            if (e is kotlinx.coroutines.CancellationException) throw e
            receiptStore.recordFailure(
                uniqueWorkName = WORK_NAME,
                errorClass = e.javaClass.simpleName,
                deferralReason = "weather worker crashed before storing a refresh; verify location permission and include diagnostics bundle",
            )
            Result.failure()
        }
    }

    private fun successReceipt(): Result {
        receiptStore.recordSuccess(WORK_NAME)
        return Result.success()
    }

    private fun getLastKnownLocation(): Pair<Double, Double>? {
        val hasCoarse = ContextCompat.checkSelfPermission(applicationContext, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED
        if (!hasCoarse) return null

        val lm = applicationContext.getSystemService(Context.LOCATION_SERVICE) as? LocationManager
            ?: return null

        val location = try {
            @Suppress("DEPRECATION")
            lm.getLastKnownLocation(LocationManager.NETWORK_PROVIDER)
        } catch (_: Exception) { null }

        return location?.let { it.latitude to it.longitude }
    }

    companion object {
        const val WORK_NAME = "weather_update"

        fun schedule(context: Context) {
            val constraints = Constraints.Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED)
                .build()

            val request = PeriodicWorkRequestBuilder<WeatherUpdateWorker>(
                30, TimeUnit.MINUTES,
            )
                .setConstraints(constraints)
                .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 15, TimeUnit.MINUTES)
                .build()

            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                WORK_NAME, ExistingPeriodicWorkPolicy.KEEP, request,
            )
        }

        fun cancel(context: Context) {
            WorkManager.getInstance(context).cancelUniqueWork(WORK_NAME)
        }

        fun clearStoredWeatherState(context: Context) {
            context.getSharedPreferences("freevibe_weather_wp", Context.MODE_PRIVATE)
                .edit()
                .remove("weather_effect")
                .remove("wind_speed")
                .remove("temperature")
                .remove("is_day")
                .remove("location_lat")
                .remove("location_lon")
                .remove("location_present")
                .apply()
        }
    }
}

internal fun roundWeatherCoordinate(value: Double): Float =
    (round(value * 100.0) / 100.0).toFloat()
