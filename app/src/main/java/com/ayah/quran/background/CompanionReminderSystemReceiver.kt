package com.ayah.quran.background

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import com.ayah.quran.di.ApplicationScope
import com.ayah.quran.di.IoDispatcher
import com.ayah.quran.domain.model.companion.CompanionReminderType
import com.ayah.quran.domain.repository.companion.CompanionReminderScheduler
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import java.util.concurrent.atomic.AtomicBoolean

class CompanionReminderSystemReceiver : BroadcastReceiver() {

    @EntryPoint
    @InstallIn(SingletonComponent::class)
    interface ReceiverEntryPoint {
        fun reminderScheduler(): CompanionReminderScheduler

        @ApplicationScope
        fun applicationScope(): CoroutineScope

        @IoDispatcher
        fun ioDispatcher(): CoroutineDispatcher
    }

    override fun onReceive(context: Context, intent: Intent?) {
        val action = intent?.action ?: return
        if (!isSupportedAction(action)) return
        handleSupportedAction(
            action = action,
            resolveDependencies = {
                val entryPoint = EntryPointAccessors.fromApplication(
                    context.applicationContext,
                    ReceiverEntryPoint::class.java,
                )
                ResolvedDependencies(
                    scheduler = entryPoint.reminderScheduler(),
                    applicationScope = entryPoint.applicationScope(),
                    ioDispatcher = entryPoint.ioDispatcher(),
                )
            },
            acquirePendingResult = {
                val pendingResult = goAsync()
                pendingResult::finish
            },
        )
    }

    internal data class ResolvedDependencies(
        val scheduler: CompanionReminderScheduler,
        val applicationScope: CoroutineScope,
        val ioDispatcher: CoroutineDispatcher,
    )

    companion object {
        private const val TAG = "CompanionReminderSysRcv"
        internal const val RECONCILE_TIMEOUT_MILLIS = 8_000L

        internal val SUPPORTED_ACTIONS: Set<String> = setOf(
            Intent.ACTION_BOOT_COMPLETED,
            Intent.ACTION_TIME_CHANGED,
            Intent.ACTION_TIMEZONE_CHANGED,
            Intent.ACTION_DATE_CHANGED,
            Intent.ACTION_MY_PACKAGE_REPLACED,
        )

        private val FORCE_REPLACE_ACTIONS: Set<String> = setOf(
            Intent.ACTION_TIME_CHANGED,
            Intent.ACTION_TIMEZONE_CHANGED,
        )

        internal fun isSupportedAction(action: String?): Boolean =
            action != null && action in SUPPORTED_ACTIONS

        internal fun shouldForceReplace(action: String?): Boolean =
            action != null && action in FORCE_REPLACE_ACTIONS

        internal fun handleSupportedAction(
            action: String,
            resolveDependencies: () -> ResolvedDependencies,
            acquirePendingResult: () -> () -> Unit,
            timeoutMillis: Long = RECONCILE_TIMEOUT_MILLIS,
        ): Job? {
            val dependencies = try {
                resolveDependencies()
            } catch (e: Error) {
                throw e
            } catch (e: Exception) {
                Log.e(TAG, "Failed resolving ReceiverEntryPoint dependencies for action=$action", e)
                return null
            }
            val onFinish = acquirePendingResult()
            val scheduler = dependencies.scheduler
            return launchReconciliation(
                scope = dependencies.applicationScope,
                dispatcher = dependencies.ioDispatcher,
                action = action,
                reconcile = { forceReplace ->
                    CompanionReminderType.entries.forEach { type ->
                        scheduler.reconcile(type, replaceExisting = forceReplace)
                    }
                },
                onFinish = onFinish,
                timeoutMillis = timeoutMillis,
            )
        }

        internal fun launchReconciliation(
            scope: CoroutineScope,
            dispatcher: CoroutineDispatcher,
            action: String,
            reconcile: suspend (Boolean) -> Unit,
            onFinish: () -> Unit,
            timeoutMillis: Long = RECONCILE_TIMEOUT_MILLIS,
        ): Job? {
            val finishOnce = createFinishOnce(action, onFinish)
            return try {
                val job = scope.launch(dispatcher) {
                    executeReconciliationInternal(
                        action = action,
                        timeoutMillis = timeoutMillis,
                        reconcile = reconcile,
                        finishOnce = finishOnce,
                    )
                }
                job.invokeOnCompletion { cause ->
                    finishOnce(cause)
                }
                job
            } catch (t: Throwable) {
                finishOnce(t)
                if (t is Error) {
                    throw t
                }
                Log.e(TAG, "Failed dispatching reminder reconciliation for action=$action", t)
                null
            }
        }

        internal suspend fun executeReconciliation(
            action: String?,
            timeoutMillis: Long = RECONCILE_TIMEOUT_MILLIS,
            reconcile: suspend (Boolean) -> Unit,
            onFinish: () -> Unit,
        ) {
            val finishOnce = createFinishOnce(action, onFinish)
            executeReconciliationInternal(
                action = action,
                timeoutMillis = timeoutMillis,
                reconcile = reconcile,
                finishOnce = finishOnce,
            )
        }

        private fun createFinishOnce(
            action: String?,
            onFinish: () -> Unit,
        ): (Throwable?) -> Unit {
            val finished = AtomicBoolean(false)
            return { primaryFailure ->
                if (finished.compareAndSet(false, true)) {
                    try {
                        onFinish()
                    } catch (finishError: Error) {
                        if (primaryFailure is Error) {
                            primaryFailure.addSuppressed(finishError)
                            Log.e(TAG, "Failed finishing broadcast PendingResult for action=$action", finishError)
                        } else {
                            throw finishError
                        }
                    } catch (finishException: Exception) {
                        if (primaryFailure != null) {
                            primaryFailure.addSuppressed(finishException)
                        }
                        Log.w(TAG, "Failed finishing broadcast PendingResult for action=$action", finishException)
                    }
                }
            }
        }

        private suspend fun executeReconciliationInternal(
            action: String?,
            timeoutMillis: Long,
            reconcile: suspend (Boolean) -> Unit,
            finishOnce: (Throwable?) -> Unit,
        ) {
            val forceReplace = shouldForceReplace(action)
            var primaryFailure: Throwable? = null
            try {
                val completed = withTimeoutOrNull(timeoutMillis) {
                    reconcile(forceReplace)
                    true
                }
                if (completed == null) {
                    Log.w(TAG, "Reminder reconciliation timed out after ${timeoutMillis}ms for action=$action")
                }
            } catch (ce: CancellationException) {
                primaryFailure = ce
                throw ce
            } catch (e: Error) {
                primaryFailure = e
                throw e
            } catch (e: Exception) {
                Log.e(TAG, "Reminder reconciliation failed for action=$action", e)
            } finally {
                finishOnce(primaryFailure)
            }
        }
    }
}
