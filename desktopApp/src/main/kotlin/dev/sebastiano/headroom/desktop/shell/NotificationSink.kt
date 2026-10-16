package dev.sebastiano.headroom.desktop.shell

internal interface NotificationSink {
    suspend fun send(id: String, title: String, body: String): Boolean

    fun dismiss(id: String)
}
