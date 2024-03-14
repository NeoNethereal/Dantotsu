package ani.dantotsu.notifications.anilist

import android.Manifest
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import androidx.core.app.ActivityCompat
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.work.Worker
import androidx.work.WorkerParameters
import ani.dantotsu.R
import ani.dantotsu.connections.anilist.Anilist
import ani.dantotsu.profile.activity.ActivityItemBuilder
import ani.dantotsu.profile.activity.FeedActivity
import ani.dantotsu.settings.saving.PrefManager
import ani.dantotsu.settings.saving.PrefName
import eu.kanade.tachiyomi.data.notification.Notifications
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class AnilistNotificationWorker(appContext: Context, workerParams: WorkerParameters) :
    Worker(appContext, workerParams) {

    override fun doWork(): Result {
        val scope = CoroutineScope(Dispatchers.IO)
        scope.launch {
            PrefManager.init(applicationContext) //make sure prefs are initialized
            val userId = PrefManager.getVal<String>(PrefName.AnilistUserId)
            if (userId.isNotEmpty()) {
                Anilist.getSavedToken()
                val res = Anilist.query.getNotifications(userId.toInt(), resetNotification = false)
                val unreadNotificationCount = res?.data?.user?.unreadNotificationCount ?: 0
                if (unreadNotificationCount > 0) {
                    val unreadNotifications = res?.data?.page?.notifications?.sortedBy { it.id }
                        ?.takeLast(unreadNotificationCount)
                    val lastId = PrefManager.getVal<Int>(PrefName.LastAnilistNotificationId)
                    val newNotifications = unreadNotifications?.filter { it.id > lastId }
                    val filteredTypes =
                        PrefManager.getVal<Set<String>>(PrefName.AnilistFilteredTypes)
                    newNotifications?.forEach {
                        if (!filteredTypes.contains(it.notificationType)) {
                            val content = ActivityItemBuilder.getContent(it)
                            val notification = createNotification(applicationContext, content)
                            if (ActivityCompat.checkSelfPermission(
                                    applicationContext,
                                    Manifest.permission.POST_NOTIFICATIONS
                                ) == PackageManager.PERMISSION_GRANTED
                            ) {
                                NotificationManagerCompat.from(applicationContext)
                                    .notify(
                                        Notifications.CHANNEL_ANILIST,
                                        System.currentTimeMillis().toInt(),
                                        notification
                                    )
                            }
                        }
                    }
                    if (newNotifications?.isNotEmpty() == true) {
                        PrefManager.setVal(PrefName.LastAnilistNotificationId, newNotifications.last().id)
                    }
                }
            }
        }
        return Result.success()
    }


    private fun createNotification(
        context: Context,
        content: String
    ): android.app.Notification {
        val title = "New Anilist Notification"
        val intent = Intent(applicationContext, FeedActivity::class.java)
        intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        val pendingIntent = PendingIntent.getActivity(
            applicationContext,
            0,
            intent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        return NotificationCompat.Builder(context, Notifications.CHANNEL_ANILIST)
            .setSmallIcon(R.drawable.notification_icon)
            .setContentTitle(title)
            .setContentText(content)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .build()
    }

    companion object {
        val checkIntervals = arrayOf(0L, 30, 60, 120, 240, 360, 720, 1440)
        const val WORK_NAME = "ani.dantotsu.notifications.anilist.AnilistNotificationWorker"
    }
}