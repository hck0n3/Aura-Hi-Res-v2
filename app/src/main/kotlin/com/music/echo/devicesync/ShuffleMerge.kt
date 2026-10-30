package iad1tya.echo.music.devicesync

/**
 * What to do locally with a shuffle report that arrived from another device (row 528). The mirror of the Worker's `mergeShuffle`
 * (docs/worker/sync/src/merge.js) on the receiving side.
 *
 * `epoch` is the context's cycle counter: a context that finished a full no-repeat cycle somewhere has a higher one, and its
 * played-set starts again. Same epoch: the played-sets are united. A lower epoch is stale and ignored (the device then pushes its
 * own state, which the room corrects).
 */
object ShuffleMerge {
    sealed interface Plan {
        data object Ignore : Plan

        /** The remote cycle is newer: wipe the local played-set, adopt [epoch] and insert [ids]. */
        data class Reset(val epoch: Int, val ids: List<String>) : Plan

        /** Same cycle: insert only the [ids] this device does not have. */
        data class Add(val ids: List<String>) : Plan
    }

    fun plan(localEpoch: Int, localIds: Set<String>, remoteEpoch: Int, remoteIds: List<String>): Plan = when {
        remoteEpoch > localEpoch -> Plan.Reset(remoteEpoch, remoteIds.distinct())
        remoteEpoch < localEpoch -> Plan.Ignore
        else -> remoteIds.distinct().filter { it !in localIds }.let { if (it.isEmpty()) Plan.Ignore else Plan.Add(it) }
    }

    /**
     * What this device has to tell the room about one context: the ids played since the last report ([newIds]) at [epoch], or — when
     * the local cycle moved past what the room was told ([pushedEpoch]) — the WHOLE current set, which starts the new cycle there.
     * Null when there is nothing to report.
     */
    fun outgoing(epoch: Int, pushedEpoch: Int?, allIds: List<String>, newIds: List<String>): Pair<Int, List<String>>? = when {
        pushedEpoch == null || epoch > pushedEpoch -> epoch to allIds.distinct()
        newIds.isEmpty() -> null
        else -> epoch to newIds.distinct()
    }
}
