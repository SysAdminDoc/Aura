package com.freevibe.di

import android.content.Context
import androidx.room.Room
import com.freevibe.data.local.CollectionDao
import com.freevibe.data.local.DatabaseDowngradeGuard
import com.freevibe.data.local.DatabaseDowngradeReceiptStore
import com.freevibe.data.local.DatabaseMigrations
import com.freevibe.data.local.FREEVIBE_DATABASE_VERSION
import com.freevibe.data.local.DownloadDao
import com.freevibe.data.local.FavoriteDao
import com.freevibe.data.local.FreeVibeDatabase
import com.freevibe.data.local.LocalWallpaperDao
import com.freevibe.data.local.LocalWallpaperFolderDao
import com.freevibe.data.local.RotationExclusionDao
import com.freevibe.data.local.SearchHistoryDao
import com.freevibe.data.local.WallpaperCacheDao
import com.freevibe.data.local.WallpaperHistoryDao
import com.freevibe.data.model.providerRetryAfterHostSuffixes
import com.freevibe.data.remote.ProviderAvailabilityInterceptor
import com.freevibe.data.remote.RateLimitInterceptor
import com.freevibe.data.remote.bing.BingDailyApi
import com.freevibe.data.remote.lemmy.LemmyApi
import com.freevibe.data.remote.nasa.NasaApodApi
import com.freevibe.data.remote.wikimedia.WikimediaPotdApi
import com.freevibe.data.remote.weather.OpenMeteoApi
import com.freevibe.data.remote.pexels.PexelsApi
import com.freevibe.data.remote.pixabay.PixabayApi
import com.freevibe.data.remote.wallhaven.WallhavenApi
import com.squareup.moshi.Moshi
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.moshi.MoshiConverterFactory
import java.util.concurrent.TimeUnit
import javax.inject.Qualifier
import javax.inject.Singleton

@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class IoDispatcher

@Module
@InstallIn(SingletonComponent::class)
object AppModule {

    // -- OkHttp --

    @Provides
    @IoDispatcher
    fun provideIoDispatcher(): CoroutineDispatcher = Dispatchers.IO

    @Provides
    @Singleton
    fun provideOkHttpClient(): OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .writeTimeout(60, TimeUnit.SECONDS)
        .apply {
            if (com.freevibe.BuildConfig.DEBUG) {
                addInterceptor(HttpLoggingInterceptor().apply {
                    level = HttpLoggingInterceptor.Level.BASIC
                })
            }
        }
        .addInterceptor(ProviderAvailabilityInterceptor())
        .addInterceptor { chain ->
            val original = chain.request()
            val request = if (original.header("User-Agent") == null) {
                original.newBuilder()
                    .header("User-Agent", "Aura/${com.freevibe.BuildConfig.VERSION_NAME} (Android; Open Source)")
                    .build()
            } else {
                original
            }
            chain.proceed(request)
        }
        // Bounded 429-aware retry for providers whose policy says Retry-After is safe to honor.
        // Other hosts pass through unchanged.
        .addInterceptor(RateLimitInterceptor(hostSuffixes = providerRetryAfterHostSuffixes()))
        .build()

    @Provides
    @Singleton
    fun provideMoshi(): Moshi = Moshi.Builder()
        .build()

    // -- API Services --

    @Provides
    @Singleton
    fun provideWallhavenApi(client: OkHttpClient, moshi: Moshi): WallhavenApi =
        Retrofit.Builder()
            .baseUrl("https://wallhaven.cc/api/v1/")
            .client(client)
            .addConverterFactory(MoshiConverterFactory.create(moshi))
            .build()
            .create(WallhavenApi::class.java)

    @Provides
    @Singleton
    fun provideNasaApodApi(client: OkHttpClient, moshi: Moshi): NasaApodApi =
        Retrofit.Builder()
            .baseUrl(NasaApodApi.BASE_URL)
            .client(client)
            .addConverterFactory(MoshiConverterFactory.create(moshi))
            .build()
            .create(NasaApodApi::class.java)

    @Provides
    @Singleton
    fun provideWikimediaPotdApi(client: OkHttpClient, moshi: Moshi): WikimediaPotdApi =
        Retrofit.Builder()
            .baseUrl(WikimediaPotdApi.BASE_URL)
            .client(client)
            .addConverterFactory(MoshiConverterFactory.create(moshi))
            .build()
            .create(WikimediaPotdApi::class.java)

    @Provides
    @Singleton
    fun provideLemmyApi(client: OkHttpClient, moshi: Moshi): LemmyApi =
        Retrofit.Builder()
            .baseUrl(LemmyApi.BASE_URL)
            .client(client)
            .addConverterFactory(MoshiConverterFactory.create(moshi))
            .build()
            .create(LemmyApi::class.java)

    @Provides
    @Singleton
    fun provideBingDailyApi(client: OkHttpClient, moshi: Moshi): BingDailyApi =
        Retrofit.Builder()
            .baseUrl(BingDailyApi.BASE_URL)
            .client(client)
            .addConverterFactory(MoshiConverterFactory.create(moshi))
            .build()
            .create(BingDailyApi::class.java)

    @Provides
    @Singleton
    fun providePexelsApi(client: OkHttpClient, moshi: Moshi): PexelsApi =
        Retrofit.Builder()
            .baseUrl(PexelsApi.BASE_URL)
            .client(client)
            .addConverterFactory(MoshiConverterFactory.create(moshi))
            .build()
            .create(PexelsApi::class.java)

    @Provides
    @Singleton
    fun providePixabayApi(client: OkHttpClient, moshi: Moshi): PixabayApi =
        Retrofit.Builder()
            .baseUrl(PixabayApi.BASE_URL)
            .client(client)
            .addConverterFactory(MoshiConverterFactory.create(moshi))
            .build()
            .create(PixabayApi::class.java)

    @Provides
    @Singleton
    fun provideOpenMeteoApi(client: OkHttpClient, moshi: Moshi): OpenMeteoApi =
        Retrofit.Builder()
            .baseUrl(OpenMeteoApi.BASE_URL)
            .client(client)
            .addConverterFactory(MoshiConverterFactory.create(moshi))
            .build()
            .create(OpenMeteoApi::class.java)

    // -- Database --

    @Provides
    @Singleton
    fun provideDatabase(
        @ApplicationContext context: Context,
        downgradeReceipts: DatabaseDowngradeReceiptStore,
    ): FreeVibeDatabase {
        // Before Room touches the file. Opening a database whose schema is ahead
        // of this build is exactly what throws, so the check has to happen first,
        // and it reads the version out of the SQLite header rather than opening a
        // connection to ask.
        DatabaseDowngradeGuard
            .inspect(
                databaseFile = DatabaseDowngradeGuard.databaseFile(context),
                currentVersion = FREEVIBE_DATABASE_VERSION,
            )
            ?.let(downgradeReceipts::record)

        return Room.databaseBuilder(context, FreeVibeDatabase::class.java, "freevibe.db")
            .addMigrations(*DatabaseMigrations.ALL_MIGRATIONS)
            // Only on downgrade. An upgrade with a missing migration must still
            // fail loudly, because that is a bug in this build rather than a user
            // installing an older APK. The guard above has already copied the
            // database aside and recorded a receipt the UI turns into a warning,
            // so this drops tables that are provably recoverable.
            .fallbackToDestructiveMigrationOnDowngrade(dropAllTables = true)
            .addCallback(object : androidx.room.RoomDatabase.Callback() {
                override fun onOpen(db: androidx.sqlite.db.SupportSQLiteDatabase) {
                    super.onOpen(db)
                    db.execSQL("PRAGMA foreign_keys = ON")
                }
            })
            .build()
    }

    @Provides
    fun provideFavoriteDao(db: FreeVibeDatabase): FavoriteDao = db.favoriteDao()

    @Provides
    fun provideDownloadDao(db: FreeVibeDatabase): DownloadDao = db.downloadDao()

    @Provides
    fun provideSearchHistoryDao(db: FreeVibeDatabase): SearchHistoryDao = db.searchHistoryDao()

    @Provides
    fun provideWallpaperCacheDao(db: FreeVibeDatabase): WallpaperCacheDao = db.wallpaperCacheDao()

    @Provides
    fun provideWallpaperHistoryDao(db: FreeVibeDatabase): WallpaperHistoryDao = db.wallpaperHistoryDao()

    @Provides
    fun provideCollectionDao(db: FreeVibeDatabase): CollectionDao = db.collectionDao()

    @Provides
    fun provideLocalWallpaperFolderDao(db: FreeVibeDatabase): LocalWallpaperFolderDao = db.localWallpaperFolderDao()

    @Provides
    fun provideLocalWallpaperDao(db: FreeVibeDatabase): LocalWallpaperDao = db.localWallpaperDao()

    @Provides
    fun provideRotationExclusionDao(db: FreeVibeDatabase): RotationExclusionDao = db.rotationExclusionDao()
}
