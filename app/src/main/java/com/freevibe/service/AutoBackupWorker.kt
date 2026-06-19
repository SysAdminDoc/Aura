package com.freevibe.service

import android.content.Context
import android.net.Uri
import android.provider.DocumentsContract
import androidx.hilt.work.HiltWorker
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.freevibe.data.local.PreferencesManager
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.first
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.TimeUnit

@HiltWorker
class AutoBackupWorker @AssistedInject constructor(
    @Assisted appContext: Context,
    @Assisted workerParams: WorkerParameters,
    private val favoritesExporter: FavoritesExporter,
    private val prefs: PreferencesManager,
    private val receiptStore: BackgroundWorkReceiptStore,
) : CoroutineWorker(appContext, workerParams) {

    override suspend fun doWork(): Result {
        return try {
            val folderUri = prefs.autoBackupFolderUri.first()
            if (folderUri.isBlank()) {
                receiptStore.recordFailure(
                    uniqueWorkName = WORK_NAME,
                    errorClass = "MissingFolder",
                    deferralReason = "no backup folder configured",
                )
                return Result.failure()
            }

            val treeUri = Uri.parse(folderUri)
            val treeDocId = runCatching { DocumentsContract.getTreeDocumentId(treeUri) }.getOrNull()
            if (treeDocId == null) {
                receiptStore.recordFailure(
                    uniqueWorkName = WORK_NAME,
                    errorClass = "InvalidUri",
                    deferralReason = "backup folder URI is invalid or permission was revoked",
                )
                return Result.failure()
            }

            val timestamp = timestampFormatter().format(Date())
            val fileName = "aura_backup_$timestamp.json"

            val newDocUri = DocumentsContract.createDocument(
                applicationContext.contentResolver,
                DocumentsContract.buildDocumentUriUsingTree(treeUri, treeDocId),
                "application/json",
                fileName,
            )

            if (newDocUri == null) {
                receiptStore.recordRetry(
                    uniqueWorkName = WORK_NAME,
                    errorClass = "CreateFailed",
                    deferralReason = "could not create backup file in chosen folder",
                )
                return Result.retry()
            }

            val count = favoritesExporter.export(newDocUri).getOrThrow()

            val keepCount = prefs.autoBackupKeepCount.first().coerceAtLeast(1)
            pruneOldBackups(treeUri, treeDocId, keepCount)

            receiptStore.recordSuccess(WORK_NAME)
            if (com.freevibe.BuildConfig.DEBUG) {
                android.util.Log.d("AutoBackupWorker", "Backed up $count favorites to $fileName")
            }
            Result.success()
        } catch (e: CancellationException) {
            throw e
        } catch (e: SecurityException) {
            receiptStore.recordFailure(
                uniqueWorkName = WORK_NAME,
                errorClass = e.javaClass.simpleName,
                deferralReason = "folder permission revoked",
            )
            Result.failure()
        } catch (e: Exception) {
            receiptStore.recordRetry(
                uniqueWorkName = WORK_NAME,
                errorClass = e.javaClass.simpleName,
                deferralReason = e.message ?: "backup failed",
            )
            Result.retry()
        }
    }

    private fun pruneOldBackups(treeUri: Uri, treeDocId: String, keepCount: Int) {
        val safeKeepCount = keepCount.coerceAtLeast(1)
        val childrenUri = DocumentsContract.buildChildDocumentsUriUsingTree(treeUri, treeDocId)
        val projection = arrayOf(
            DocumentsContract.Document.COLUMN_DOCUMENT_ID,
            DocumentsContract.Document.COLUMN_DISPLAY_NAME,
        )
        val backupFiles = mutableListOf<Pair<String, String>>()
        applicationContext.contentResolver.query(childrenUri, projection, null, null, null)?.use { cursor ->
            val idIdx = cursor.getColumnIndexOrThrow(DocumentsContract.Document.COLUMN_DOCUMENT_ID)
            val nameIdx = cursor.getColumnIndexOrThrow(DocumentsContract.Document.COLUMN_DISPLAY_NAME)
            while (cursor.moveToNext()) {
                val name = cursor.getString(nameIdx) ?: continue
                if (name.startsWith("aura_backup_") && name.endsWith(".json")) {
                    backupFiles.add(cursor.getString(idIdx) to name)
                }
            }
        }

        if (backupFiles.size <= safeKeepCount) return

        backupFiles.sortByDescending { it.second }
        val toDelete = backupFiles.drop(safeKeepCount)
        for ((docId, _) in toDelete) {
            val docUri = DocumentsContract.buildDocumentUriUsingTree(treeUri, docId)
            runCatching { DocumentsContract.deleteDocument(applicationContext.contentResolver, docUri) }
        }
    }

    companion object {
        const val WORK_NAME = "auto_backup"

        private val FILENAME_FORMAT = object : ThreadLocal<SimpleDateFormat>() {
            override fun initialValue() = SimpleDateFormat("yyyy-MM-dd_HHmmss", Locale.ROOT)
        }

        private fun timestampFormatter(): SimpleDateFormat =
            FILENAME_FORMAT.get() ?: SimpleDateFormat("yyyy-MM-dd_HHmmss", Locale.ROOT).also(FILENAME_FORMAT::set)

        suspend fun schedule(context: Context) {
            val prefs = PreferencesManager(context)
            val enabled = prefs.autoBackupEnabled.first()
            if (!enabled) {
                cancel(context)
                return
            }
            val intervalHours = prefs.autoBackupIntervalHours.first().coerceAtLeast(1L)

            val constraints = Constraints.Builder()
                .setRequiresBatteryNotLow(true)
                .build()

            val request = PeriodicWorkRequestBuilder<AutoBackupWorker>(
                intervalHours, TimeUnit.HOURS,
            )
                .setConstraints(constraints)
                .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 15, TimeUnit.MINUTES)
                .build()

            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                WORK_NAME,
                ExistingPeriodicWorkPolicy.UPDATE,
                request,
            )
        }

        fun cancel(context: Context) {
            WorkManager.getInstance(context).cancelUniqueWork(WORK_NAME)
        }
    }
}
