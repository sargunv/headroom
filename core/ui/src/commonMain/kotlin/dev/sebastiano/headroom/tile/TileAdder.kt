package dev.sebastiano.headroom.tile

/** What happened when the app asked the system to add its Quick Settings tile. */
enum class TileAddResult {
    Added,
    AlreadyAdded,
    /** The user said no. */
    NotAdded,
    /** The system could not ask, for example because another request was showing. */
    Failed,
}

/** Asks the system to add Headroom's tile to Quick Settings. */
fun interface TileAdder {
    fun request(onResult: (TileAddResult) -> Unit)

    companion object {
        /** For tests and previews: every request fails at once. */
        val Unavailable: TileAdder = TileAdder { it(TileAddResult.Failed) }
    }
}
