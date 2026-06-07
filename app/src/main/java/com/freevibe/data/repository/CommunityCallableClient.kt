package com.freevibe.data.repository

import com.freevibe.data.model.CommunityFollowInput
import com.freevibe.data.model.CommunityQuotaPolicies
import com.freevibe.data.model.CommunityReportInput
import com.freevibe.data.model.buildCommunityFollowCallablePayload
import com.freevibe.data.model.buildCommunityReportCallablePayload
import com.freevibe.data.model.buildCommunityVoteCallablePayload
import com.google.firebase.functions.FirebaseFunctions
import com.google.firebase.functions.FirebaseFunctionsException
import com.google.firebase.functions.HttpsCallableOptions
import kotlinx.coroutines.tasks.await
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

data class CommunityCallableRequest(
    val functionName: String,
    val data: Map<String, Any>,
    val consumeLimitedUseAppCheckToken: Boolean,
)

data class CommunityCallableWriteResult(
    val operationId: String,
    val status: String,
    val targetPath: String,
    val serverTimeMillis: Long,
    val resourceId: String = "",
) {
    fun targetId(): String =
        resourceId.ifBlank { targetPath.substringAfterLast("/", "") }
}

class CommunityCallableException(
    val functionName: String,
    val code: String,
    override val message: String?,
    cause: Throwable? = null,
    val details: Any? = null,
) : Exception(message, cause) {
    fun isMissingEndpoint(): Boolean =
        code.equals("NOT_FOUND", ignoreCase = true) ||
            code.equals("UNIMPLEMENTED", ignoreCase = true)
}

interface CommunityCallableInvoker {
    suspend fun call(request: CommunityCallableRequest): Map<String, Any?>
}

@Singleton
class FirebaseCommunityCallableInvoker @Inject constructor() : CommunityCallableInvoker {
    private val functions by lazy { FirebaseFunctions.getInstance() }

    override suspend fun call(request: CommunityCallableRequest): Map<String, Any?> {
        val callable = if (request.consumeLimitedUseAppCheckToken) {
            functions.getHttpsCallable(
                request.functionName,
                HttpsCallableOptions.Builder()
                    .setLimitedUseAppCheckTokens(true)
                    .build(),
            )
        } else {
            functions.getHttpsCallable(request.functionName)
        }

        val result = try {
            callable.call(request.data).await()
        } catch (e: FirebaseFunctionsException) {
            throw CommunityCallableException(
                functionName = request.functionName,
                code = e.code.name,
                message = e.message,
                cause = e,
                details = e.details,
            )
        }
        return result.data.asStringKeyMap(request.functionName)
    }
}

@Singleton
class CommunityCallableClient @Inject constructor(
    private val invoker: CommunityCallableInvoker,
) {
    suspend fun submitCommunityReport(input: CommunityReportInput): CommunityCallableWriteResult {
        val policy = CommunityQuotaPolicies.reports.callable
        val request = CommunityCallableRequest(
            functionName = policy.functionName,
            data = buildCommunityCallableEnvelope(
                payload = buildCommunityReportCallablePayload(input),
                operationId = communityOperationId("report"),
                clientSentAt = System.currentTimeMillis(),
            ),
            consumeLimitedUseAppCheckToken = policy.consumeLimitedUseAppCheckToken,
        )
        return invoker.call(request).toWriteResult(resourceIdField = "reportId")
    }

    suspend fun recordCommunityVote(contentId: String): CommunityCallableWriteResult {
        val policy = CommunityQuotaPolicies.votes.callable
        val request = CommunityCallableRequest(
            functionName = policy.functionName,
            data = buildCommunityCallableEnvelope(
                payload = buildCommunityVoteCallablePayload(contentId),
                operationId = communityOperationId("vote"),
                clientSentAt = System.currentTimeMillis(),
            ),
            consumeLimitedUseAppCheckToken = policy.consumeLimitedUseAppCheckToken,
        )
        return invoker.call(request).toWriteResult(resourceIdField = "voteId")
    }

    suspend fun setCreatorFollow(input: CommunityFollowInput): CommunityCallableWriteResult {
        val policy = CommunityQuotaPolicies.follows.callable
        val operationPrefix = if (input.following) "follow" else "unfollow"
        val request = CommunityCallableRequest(
            functionName = policy.functionName,
            data = buildCommunityCallableEnvelope(
                payload = buildCommunityFollowCallablePayload(input),
                operationId = communityOperationId(operationPrefix),
                clientSentAt = System.currentTimeMillis(),
            ),
            consumeLimitedUseAppCheckToken = policy.consumeLimitedUseAppCheckToken,
        )
        return invoker.call(request).toWriteResult(resourceIdField = "creatorId")
    }
}

internal fun buildCommunityCallableEnvelope(
    payload: Map<String, Any>,
    operationId: String,
    clientSentAt: Long,
): Map<String, Any> {
    val normalizedOperationId = operationId.trim()
    require(normalizedOperationId.isNotBlank()) { "Operation ID is required" }
    require(normalizedOperationId.length <= 120) { "Operation ID is too long" }
    require(clientSentAt > 0L) { "Client timestamp is required" }
    return mapOf(
        "operationId" to normalizedOperationId,
        "clientSentAt" to clientSentAt,
        "payload" to payload,
    )
}

private fun communityOperationId(prefix: String): String =
    "${prefix}_${UUID.randomUUID()}"

private fun Any?.asStringKeyMap(functionName: String): Map<String, Any?> {
    val value = this as? Map<*, *>
        ?: throw CommunityCallableException(
            functionName = functionName,
            code = "INVALID_RESPONSE",
            message = "Callable response must be an object.",
        )
    return value.entries.associate { (key, entryValue) ->
        val stringKey = key as? String
            ?: throw CommunityCallableException(
                functionName = functionName,
                code = "INVALID_RESPONSE",
                message = "Callable response contains a non-string key.",
            )
        stringKey to entryValue
    }
}

private fun Map<String, Any?>.toWriteResult(resourceIdField: String): CommunityCallableWriteResult =
    CommunityCallableWriteResult(
        operationId = stringField("operationId"),
        status = stringField("status"),
        targetPath = stringField("targetPath"),
        serverTimeMillis = longField("serverTimeMillis"),
        resourceId = optionalStringField(resourceIdField),
    )

private fun Map<String, Any?>.stringField(name: String): String =
    optionalStringField(name).ifBlank {
        throw CommunityCallableException(
            functionName = "communityCallable",
            code = "INVALID_RESPONSE",
            message = "Callable response missing $name.",
        )
    }

private fun Map<String, Any?>.optionalStringField(name: String): String =
    (this[name] as? String).orEmpty()

private fun Map<String, Any?>.longField(name: String): Long {
    val value = this[name] ?: throw CommunityCallableException(
        functionName = "communityCallable",
        code = "INVALID_RESPONSE",
        message = "Callable response missing $name.",
    )
    return when (value) {
        is Long -> value
        is Int -> value.toLong()
        is Double -> value.toLong()
        is Float -> value.toLong()
        else -> throw CommunityCallableException(
            functionName = "communityCallable",
            code = "INVALID_RESPONSE",
            message = "Callable response field $name must be numeric.",
        )
    }
}
