package dev.sebastiano.headroom.desktop.shell

import dev.nucleusframework.notification.AuthorizationOption
import dev.nucleusframework.notification.AuthorizationStatus
import dev.nucleusframework.notification.DeliveredNotification
import dev.nucleusframework.notification.NotificationCenter
import dev.nucleusframework.notification.NotificationCenterDelegate
import dev.nucleusframework.notification.NotificationContent
import dev.nucleusframework.notification.NotificationRequest
import dev.nucleusframework.notification.NotificationResponse
import dev.nucleusframework.notification.NotificationSound
import dev.nucleusframework.notification.PresentationOption
import kotlin.coroutines.resume
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeoutOrNull

/**
 * A successful result means UNUserNotificationCenter accepted the request, not just enqueued JNI.
 */
internal class MacNotifications(
    private val scope: CoroutineScope,
    private val onOpen: () -> Unit,
    private val onStatus: (String?) -> Unit,
) : NotificationSink {
    private var requested = false

    fun install() {
        if (!NotificationCenter.isAvailable) {
            onStatus("Notifications require the installed Headroom app, not a Gradle run.")
            return
        }
        NotificationCenter.setDelegate(
            object : NotificationCenterDelegate {
                override fun willPresent(
                    notification: DeliveredNotification
                ): Set<PresentationOption> =
                    setOf(PresentationOption.BANNER, PresentationOption.SOUND)

                override fun didReceive(response: NotificationResponse) {
                    scope.launch { onOpen() }
                }
            }
        )
    }

    override suspend fun send(id: String, title: String, body: String): Boolean {
        if (!authorize()) return false
        val request =
            NotificationRequest(
                identifier = id,
                content =
                    NotificationContent(
                        title = title,
                        body = body,
                        sound = NotificationSound.Default,
                    ),
            )
        val accepted =
            withTimeoutOrNull(CALLBACK_TIMEOUT_MILLIS) {
                suspendCancellableCoroutine { continuation ->
                    NotificationCenter.add(request) { error ->
                        if (continuation.isActive) continuation.resume(error == null)
                    }
                }
            } == true
        if (!accepted) onStatus("macOS did not accept the notification. Headroom will retry.")
        return accepted
    }

    override fun dismiss(id: String) {
        if (!NotificationCenter.isAvailable) return
        NotificationCenter.removePendingNotifications(listOf(id))
        NotificationCenter.removeDeliveredNotifications(listOf(id))
    }

    fun close() {
        if (NotificationCenter.isAvailable) NotificationCenter.setDelegate(null)
    }

    private suspend fun authorize(): Boolean {
        if (!NotificationCenter.isAvailable) return false
        val status =
            withTimeoutOrNull(CALLBACK_TIMEOUT_MILLIS) {
                suspendCancellableCoroutine { continuation ->
                    NotificationCenter.getNotificationSettings { settings ->
                        if (continuation.isActive) continuation.resume(settings.authorizationStatus)
                    }
                }
            }
        val granted =
            when (status) {
                AuthorizationStatus.AUTHORIZED,
                AuthorizationStatus.PROVISIONAL -> true
                AuthorizationStatus.NOT_DETERMINED -> if (requested) false else requestPermission()
                else -> false
            }
        onStatus(
            if (granted) null
            else "Notifications are off. Enable Headroom in macOS System Settings."
        )
        return granted
    }

    private suspend fun requestPermission(): Boolean {
        requested = true
        return withTimeoutOrNull(CALLBACK_TIMEOUT_MILLIS) {
            suspendCancellableCoroutine { continuation ->
                NotificationCenter.requestAuthorization(
                    setOf(AuthorizationOption.ALERT, AuthorizationOption.SOUND)
                ) { granted, _ ->
                    if (continuation.isActive) continuation.resume(granted)
                }
            }
        } == true
    }

    private companion object {
        const val CALLBACK_TIMEOUT_MILLIS = 10_000L
    }
}
