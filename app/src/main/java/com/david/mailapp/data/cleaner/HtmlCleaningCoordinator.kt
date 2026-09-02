package com.david.mailapp.data.cleaner

import android.util.Log
import com.david.mailapp.data.local.dao.EmailDao
import com.david.mailapp.domain.model.Email
import com.david.mailapp.domain.model.EmailBodyKind
import com.david.mailapp.domain.model.EmailContentState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.security.MessageDigest

sealed interface HtmlCleanResult {
    data class Cleaned(val displayBody: String) : HtmlCleanResult
    data class Fallback(val displayBody: String) : HtmlCleanResult
    object Stale : HtmlCleanResult
}

internal class HtmlCleaningCoordinator(
    private val emailDao: EmailDao,
    private val sessionGenerationProvider: suspend () -> Long,
    private val coroutineScope: CoroutineScope = CoroutineScope(Dispatchers.Default)
) {
    companion object {
        private const val TAG = "HtmlCleanTrace"
    }

    private val mutex = Mutex()
    private val inFlightMap = mutableMapOf<String, Deferred<HtmlCleanResult>>()

    private fun computeDigest(body: String): String {
        return try {
            val bytes = MessageDigest.getInstance("MD5").digest(body.toByteArray(Charsets.UTF_8))
            bytes.joinToString("") { "%02x".format(it) }
        } catch (e: Exception) {
            body.hashCode().toString()
        }
    }

    suspend fun cleanAndPersist(email: Email): HtmlCleanResult {
        // Validation checks
        if (email.contentState != EmailContentState.READY || email.bodyKind != EmailBodyKind.HTML) {
            Log.d(TAG, "[HTML_CLEAN_SKIP] emailId=${email.id} reason=not_ready_or_not_html")
            return HtmlCleanResult.Fallback(email.body)
        }

        if (email.cleanBody.isNotBlank()) {
            Log.d(TAG, "[HTML_CLEAN_SKIP] emailId=${email.id} reason=already_cleaned")
            return HtmlCleanResult.Cleaned(email.cleanBody)
        }

        val currentSessionGen = sessionGenerationProvider()
        val bodyDigest = computeDigest(email.body)
        val flightKey = "gen_${currentSessionGen}_${email.id}_$bodyDigest"

        var deferredToAwait: Deferred<HtmlCleanResult>? = null
        var isOriginator = false

        mutex.withLock {
            val existing = inFlightMap[flightKey]
            if (existing != null) {
                Log.d(TAG, "[HTML_CLEAN_JOIN] emailId=${email.id} flightKey=$flightKey")
                deferredToAwait = existing
            } else {
                Log.d(TAG, "[HTML_CLEAN_START] emailId=${email.id} flightKey=$flightKey digest=$bodyDigest")
                isOriginator = true
                val deferred = coroutineScope.async(Dispatchers.Default) {
                    executeCleaningAndPersist(
                        sessionGen = currentSessionGen,
                        email = email,
                        bodyDigest = bodyDigest
                    )
                }
                inFlightMap[flightKey] = deferred
                deferredToAwait = deferred
            }
        }

        return try {
            val result = deferredToAwait!!.await()
            result
        } finally {
            if (isOriginator) {
                mutex.withLock {
                    inFlightMap.remove(flightKey)
                }
            }
        }
    }

    private suspend fun executeCleaningAndPersist(
        sessionGen: Long,
        email: Email,
        bodyDigest: String
    ): HtmlCleanResult {
        val t0 = System.currentTimeMillis()
        val rawBody = email.body

        // Check session stale before computation
        if (sessionGenerationProvider() != sessionGen) {
            Log.d(TAG, "[HTML_CLEAN_STALE] emailId=${email.id} reason=session_changed_before_start")
            return HtmlCleanResult.Stale
        }

        val cleanedOutput = EmailHtmlCleaner.clean(rawBody)
        val isFallback = cleanedOutput == rawBody || cleanedOutput.isBlank()
        val displayBody = if (isFallback) rawBody else cleanedOutput

        // Check session stale before persistence
        if (sessionGenerationProvider() != sessionGen) {
            Log.d(TAG, "[HTML_CLEAN_STALE] emailId=${email.id} reason=session_changed_before_persist")
            return HtmlCleanResult.Stale
        }

        val inlineRefsJson = com.david.mailapp.data.local.converter.InlineContentReferenceCodec.encode(email.inlineReferences)
        val cachedContentBytes = rawBody.toByteArray(Charsets.UTF_8).size.toLong() +
            displayBody.toByteArray(Charsets.UTF_8).size.toLong() +
            inlineRefsJson.toByteArray(Charsets.UTF_8).size.toLong()

        val casSuccess = emailDao.updateCleanBodyIfCurrentAndEnforceLru(
            emailId = email.id,
            expectedRawBody = rawBody,
            cleanBody = displayBody,
            cachedContentBytes = cachedContentBytes
        )

        val duration = System.currentTimeMillis() - t0
        return if (casSuccess) {
            val resultType = if (isFallback) "fallback" else "cleaned"
            Log.d(TAG, "[HTML_CLEAN_DONE] emailId=${email.id} result=$resultType durationMs=$duration digest=$bodyDigest")
            if (isFallback) HtmlCleanResult.Fallback(displayBody) else HtmlCleanResult.Cleaned(displayBody)
        } else {
            Log.d(TAG, "[HTML_CLEAN_STALE] emailId=${email.id} reason=cas_update_failed durationMs=$duration")
            HtmlCleanResult.Stale
        }
    }
}
