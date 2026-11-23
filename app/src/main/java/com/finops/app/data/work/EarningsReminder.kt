package com.finops.app.data.work

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.hilt.work.HiltWorker
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.finops.app.MainActivity
import com.finops.app.R
import com.finops.app.common.runSuspendCatching
import com.finops.app.domain.model.EarningsTiming
import com.finops.app.domain.model.WatchedEarnings
import com.finops.app.domain.repository.EarningsReminderScheduler
import com.finops.app.domain.repository.SettingsRepository
import com.finops.app.domain.usecase.GetNextDayEarningsUseCase
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.first
import java.time.Clock
import java.time.Duration
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.util.Locale
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

/** Rappel quotidien (vers 18 h) via WorkManager : survit aux redémarrages du téléphone. */
@Singleton
class WorkManagerEarningsReminderScheduler @Inject constructor(
    @ApplicationContext private val context: Context,
    private val clock: Clock,
) : EarningsReminderScheduler {

    override fun schedule() {
        val now = LocalDateTime.now(clock)
        var next = now.toLocalDate().atTime(REMINDER_TIME)
        if (!next.isAfter(now)) next = next.plusDays(1)
        val request = PeriodicWorkRequestBuilder<EarningsReminderWorker>(1, TimeUnit.DAYS)
            .setInitialDelay(Duration.between(now, next).toMinutes(), TimeUnit.MINUTES)
            .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
            .build()
        WorkManager.getInstance(context).enqueueUniquePeriodicWork(WORK_NAME, ExistingPeriodicWorkPolicy.UPDATE, request)
    }

    override fun cancel() {
        WorkManager.getInstance(context).cancelUniqueWork(WORK_NAME)
    }

    private companion object {
        const val WORK_NAME = "earnings-reminder"
        val REMINDER_TIME: LocalTime = LocalTime.of(18, 0)
    }
}

/** Notifie les publications de résultats du prochain jour de bourse (une seule fois par jour). */
@HiltWorker
class EarningsReminderWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted params: WorkerParameters,
    private val getNextDayEarnings: GetNextDayEarningsUseCase,
    private val settings: SettingsRepository,
    private val clock: Clock,
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        if (!settings.observeSettings().first().notifyEarnings) return Result.success()
        val prefs = applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val today = LocalDate.now(clock).toString()
        if (prefs.getString(KEY_LAST_RUN, null) == today) return Result.success()

        val (day, events) = runSuspendCatching { getNextDayEarnings() }
            .getOrElse { return if (runAttemptCount < MAX_RETRIES) Result.retry() else Result.success() }
            ?: return Result.success()
        if (events.isNotEmpty()) EarningsNotifications.show(applicationContext, day, events)
        prefs.edit().putString(KEY_LAST_RUN, today).apply()
        return Result.success()
    }

    private companion object {
        const val PREFS = "earnings_reminder"
        const val KEY_LAST_RUN = "last_run"
        const val MAX_RETRIES = 3
    }
}

object EarningsNotifications {
    private const val CHANNEL_ID = "earnings"
    private const val NOTIFICATION_ID = 1001
    private val DAY = DateTimeFormatter.ofPattern("EEEE d MMMM", Locale.FRANCE)

    fun show(context: Context, day: LocalDate, events: List<WatchedEarnings>) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) return
        ensureChannel(context)
        val lines = events.map { e ->
            val timing = when (e.event.timing) {
                EarningsTiming.BEFORE_OPEN -> " · avant ouverture"
                EarningsTiming.AFTER_CLOSE -> " · après clôture"
                EarningsTiming.UNKNOWN -> ""
            }
            "${e.name}$timing"
        }
        val title = if (events.size == 1) "Résultats de ${events.first().name}" else "${events.size} publications de résultats"
        val dayText = DAY.format(day)
        val intent = PendingIntent.getActivity(
            context, 0,
            Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_stat_notification)
            .setContentTitle(title)
            .setContentText("$dayText : ${lines.joinToString(", ")}")
            .setStyle(NotificationCompat.InboxStyle().setSummaryText(dayText).also { s -> lines.forEach(s::addLine) })
            .setContentIntent(intent)
            .setAutoCancel(true)
            .build()
        NotificationManagerCompat.from(context).notify(NOTIFICATION_ID, notification)
    }

    private fun ensureChannel(context: Context) {
        val manager = context.getSystemService(NotificationManager::class.java)
        if (manager.getNotificationChannel(CHANNEL_ID) == null) {
            manager.createNotificationChannel(
                NotificationChannel(CHANNEL_ID, "Publications de résultats", NotificationManager.IMPORTANCE_DEFAULT)
                    .apply { description = "Rappel la veille des résultats des actions de la watchlist" }
            )
        }
    }
}
