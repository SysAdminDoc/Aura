package com.freevibe.data.remote

import com.freevibe.data.model.ContentSource
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicLong
import javax.inject.Inject
import javax.inject.Singleton
import okhttp3.Interceptor
import okhttp3.Request
import okhttp3.Response

/** Why a request went out, so a support bundle can split a provider's traffic by what it was for. */
enum class RequestPurpose {
    /** Search, list and feed calls. Retrofit services carry no tag and count as this. */
    CATALOG,
    IMAGE,
    DOWNLOAD,
    APPLY,
    EDIT,
    RESOLVE,
    UPDATE_CHECK,
}

fun Request.Builder.purpose(purpose: RequestPurpose): Request.Builder =
    tag(RequestPurpose::class.java, purpose)

/** Counts only: no URL, query or identifier reaches this, so its rows are safe to share. */
@Singleton
class RequestPurposeMetrics @Inject constructor() {
    private val counts = ConcurrentHashMap<Pair<ContentSource?, RequestPurpose>, AtomicLong>()

    fun record(source: ContentSource?, purpose: RequestPurpose) {
        counts.computeIfAbsent(source to purpose) { AtomicLong() }.incrementAndGet()
    }

    fun rows(): List<String> = counts.entries
        .groupBy { (key, _) -> key.first?.name?.lowercase(Locale.ROOT) ?: OTHER_HOSTS }
        .toSortedMap()
        .map { (source, entries) ->
            val purposes = entries
                .sortedBy { (key, _) -> key.second.ordinal }
                .joinToString(" ") { (key, count) -> "${key.second.name.lowercase(Locale.ROOT)}=${count.get()}" }
            "$source: $purposes"
        }

    private companion object {
        const val OTHER_HOSTS = "other hosts"
    }
}

/** Application interceptor, so a redirect or a 429 retry doesn't count as a second request. */
class RequestPurposeInterceptor(private val metrics: RequestPurposeMetrics) : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response {
        val request = chain.request()
        metrics.record(
            providerSourceForHost(request.url.host),
            request.tag(RequestPurpose::class.java) ?: RequestPurpose.CATALOG,
        )
        return chain.proceed(request)
    }
}

/** Tags everything a client sends when its caller can't: Coil builds its own requests. */
class DefaultRequestPurposeInterceptor(private val purpose: RequestPurpose) : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response {
        val request = chain.request()
        if (request.tag(RequestPurpose::class.java) != null) return chain.proceed(request)
        return chain.proceed(request.newBuilder().purpose(purpose).build())
    }
}
