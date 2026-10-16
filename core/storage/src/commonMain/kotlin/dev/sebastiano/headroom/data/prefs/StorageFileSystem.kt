package dev.sebastiano.headroom.data.prefs

import okio.FileSystem

// Okio exposes SYSTEM only in platform source sets, not in common metadata.
internal expect fun storageFileSystem(): FileSystem
