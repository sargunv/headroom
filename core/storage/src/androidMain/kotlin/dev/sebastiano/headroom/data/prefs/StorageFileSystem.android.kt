package dev.sebastiano.headroom.data.prefs

import okio.FileSystem

internal actual fun storageFileSystem(): FileSystem = FileSystem.SYSTEM
