package iad1tya.echo.music.devicesync

import iad1tya.echo.music.devicesync.LibrarySyncCore.Reg
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.longOrNull
import kotlinx.serialization.json.put
import timber.log.Timber

/**
 * Row 536 — the library (likes, dislikes, playlists) kept equal across the user's devices THROUGH THE WORKER, not through the
 * YouTube account (owner 2026-10-30: *«las sincronizaciones las quiero a través del Worker»* and *«que cuando esté conectado entre
 * dispositivos la sincronización a la nube de YouTube no intervenga con esto»*).
 *
 * How it works, in one paragraph: every fact is a register with the time of its last change ([LibrarySyncCore]); the newest change
 * wins. The ledger on disk remembers what this device last agreed on. A change HERE shows up as a difference between the database and
 * the ledger → registers pushed to the room (`lib_push`); the room tells the others (`lib_ops`), who pull what changed since the last
 * sequence number they saw (`lib_pull` → `lib_state`) and apply it. When a device was offline, the same two steps run when it
 * connects, so nothing is lost and nothing needs to be «in real time» for it to arrive.
 *
 * While this runs and the room has answered once, YouTube's PULL syncs of likes and playlists stay out of the way
 * ([DeviceSyncHub.libraryOwnedByDevices]); the pushes of the user's own actions to their YouTube account are not touched.
 *
 * Safety: removals that look like a mass deletion are held ([LibrarySyncCore.brakeTrips]) and the user is told; registers arrive
 * in a fixed order (songs, playlists, members, likes, dislikes); the log carries counts only (AGENTS rule 4).
 */
class LibrarySync(
    /** What the sync reads from and writes to the app (the database and the dislike store; a fake in tests). */
    private val adapter: LibraryBackend,
    /** The memory of what was last agreed (a file; a fake in tests). */
    private val store: LedgerPersistence,
    /** The last sequence number seen from the room. */
    private val sinceStore: SinceStore,
    private val scope: CoroutineScope,
    private val sendMessage: (JsonObject) -> Boolean,
    private val isEnabled: () -> Boolean,
    /** Starts whatever notices local changes (the database, the dislike store) and calls [kick]; returns how to stop it. */
    private val watchChanges: (kick: (fast: Boolean) -> Unit) -> () -> Unit = { {} },
    private val clock: () -> Long = { DeviceSyncHub.serverNow() },
    private val timing: Timing = Timing(),
    /** True once [clock] has the server's offset in this process (a `hello_ok` with its time); before that it is the raw phone clock. */
    private val clockKnown: () -> Boolean = { DeviceSyncHub.serverClockKnown },
    /** Row 900: the changes seen being made before this instance can date them against the room ([LibraryJournal.kt]). */
    private val journal: JournalPersistence = MemoryJournal(),
    /** Row 900: the device's monotonic clock (boot + time since boot): when a change was made, whatever the phone clock says. */
    private val mono: () -> Mono = { Mono(0L, System.nanoTime() / 1_000_000L) },
) {
    /** Delays, in one place so a test can make them tiny. */
    data class Timing(
        val fastDelayMs: Long = 800L,
        val slowDelayMs: Long = 4_000L,
        val minIntervalMs: Long = 30_000L,
        val pullTimeoutMs: Long = 12_000L,
    )

    private val mutex = Mutex()

    private var ledgerMap: HashMap<String, Reg>? = null
    private var since = sinceStore.get()
    private var freshStart = sinceStore.freshStart()
    private val inFlight = ArrayDeque<List<Reg>>()
    private val sentKeys = HashSet<String>()
    private var kickJob: Job? = null
    private var pullWatchdog: Job? = null
    private var lastDiffAt = 0L
    private var stopWatching: (() -> Unit)? = null

    @Volatile private var connected = false
    @Volatile private var pulledOnce = false
    @Volatile private var stopped = false

    /** Caught up with the room in THIS instance and watching, enabled, ever since: see [stampOfflineLocked]. Never persisted. */
    @Volatile private var watchingSinceCaughtUp = false

    /**
     * Row 900: the library as this instance last read it while watching, before it caught up with the room. A difference from it is
     * a change seen being made, and goes into the [journal]. Null = not watching: the next read only sets it. [watchGen] grows each
     * time watching stops, so a pass that was already reading does not set a baseline that hides a change nobody watched.
     */
    @Volatile private var baseline: HashMap<String, String>? = null
    @Volatile private var watchGen = 0
    private var journalCache: MutableList<JournalEntry>? = null

    /** Row 900: the moment the first change of the next pass was announced ([kick]), so the journal dates it then, not when it is read. */
    @Volatile private var seenAt: Pair<Mono, Long>? = null

    /** One `lib_pull` at a time: a big push makes the room send one `lib_ops` per batch, and a pull per signal is a storm (beta 4: 1656 pages in 5 s). */
    private val pullLock = Any()
    private var pullInFlight = false
    private var pullAgain = false

    fun start() {
        stopWatching = watchChanges { fast -> kick(fast) }
        // Row 807: what the brake held persists in the ledger. Shown from the start, offline, with «Biblioteca» off or an outdated
        // server too: «Borrados retenidos» must not say «Ninguno» while removals are still held.
        scope.launch {
            mutex.withLock {
                runCatching { ledger(); publishHeldLocked() }
                    .onFailure { Timber.tag(TAG).w("LIB_SYNC held removals not read: %s", it.javaClass.simpleName) }
                // Row 900: what the library is as this instance starts; from here a change seen being made is journaled with its time.
                runCatching { if (isEnabled() && !sinceStore.freshStart() && baseline == null) readBaselineLocked() }
                    .onFailure { Timber.tag(TAG).w("LIB_SYNC journal baseline not read: %s", it.javaClass.simpleName) }
            }
        }
    }

    fun stop() {
        stopped = true
        watchingSinceCaughtUp = false
        dropBaseline()
        runCatching { stopWatching?.invoke() }
        stopWatching = null
        connected = false
        pulledOnce = false
        DeviceSyncHub.libraryOwnedByDevices = false
        DeviceSyncHub.heldRemovals.value = emptyMap()
        kickJob?.cancel()
        pullWatchdog?.cancel()
    }

    /**
     * The socket is up and the room said hello: ask for what changed while this device was away. [serverLib] is the library protocol
     * version the deployed Worker announced in `hello_ok` (0 when it announced none = an older deployment that ignores `lib_pull`).
     */
    fun onConnected(serverLib: Int) {
        connected = true
        pulledOnce = false
        synchronized(pullLock) { pullInFlight = false; pullAgain = false }
        inFlight.clear()
        sentKeys.clear()
        if (!isEnabled()) {
            watchingSinceCaughtUp = false
            dropBaseline()
            DeviceSyncHub.libraryStatus.value = DeviceSyncHub.LibraryStatus.Off
            return
        }
        if (serverLib < MIN_SERVER_LIB) {
            // Without this the sync would sit silent forever and look like «never works»: say what is wrong.
            DeviceSyncHub.libraryOwnedByDevices = false
            DeviceSyncHub.libraryStatus.value = DeviceSyncHub.LibraryStatus.ServerOutdated
            Timber.tag(TAG).w("LIB_SYNC server is older than this app (lib=%d)", serverLib)
            DeviceSyncHub.notice("El servidor de sincronización está desactualizado: vuelve a desplegarlo para sincronizar la biblioteca.")
            return
        }
        requestPull()
    }

    fun onDisconnected() {
        connected = false
        pulledOnce = false
        synchronized(pullLock) { pullInFlight = false; pullAgain = false }
        pullWatchdog?.cancel()
        DeviceSyncHub.libraryOwnedByDevices = false
        if (isEnabled()) DeviceSyncHub.libraryStatus.value = DeviceSyncHub.LibraryStatus.Waiting
    }

    /** «Sincronizar ahora»: pull again, and the pull answer pushes whatever is different here. */
    fun resync() {
        if (!connected || !isEnabled()) return
        requestPull()
    }

    /** Called by the user's own actions (a like, a playlist edit): look for differences very soon. */
    fun kick(fast: Boolean) {
        if (stopped) return
        if (!isEnabled()) {
            // A change made while «Biblioteca» is off is not watched: once it is back on, the room decides first again.
            watchingSinceCaughtUp = false
            dropBaseline()
            return
        }
        if (seenAt == null) seenAt = mono() to System.currentTimeMillis()
        if (!fast && kickJob?.isActive == true) return // an observer storm collapses into one pass
        if (fast) kickJob?.cancel()
        kickJob = scope.launch {
            val wait = if (fast) timing.fastDelayMs else maxOf(timing.slowDelayMs, timing.minIntervalMs - (System.currentTimeMillis() - lastDiffAt))
            delay(wait)
            mutex.withLock {
                lastDiffAt = System.currentTimeMillis()
                val announced = seenAt
                seenAt = null
                runCatching { if (connected && pulledOnce) pushDiffLocked() else stampOfflineLocked(announced) }
                    .onFailure { Timber.tag(TAG).w("LIB_SYNC diff failed: %s", it.javaClass.simpleName) }
            }
        }
    }

    /**
     * Owner 2026-11-22: a change made while this device is away from the room keeps the time it was MADE. It used to get its
     * time when it was sent, after the room's state had been applied over it, so an older change from another device undid it
     * (a like made offline lost to an unlike from the day before). Recorded now as pending, it competes newest-wins with what the
     * room sends ([LibrarySyncCore.accepts]) and goes out when the device is back.
     *
     * Only when THIS instance caught up with the room and has been watching, enabled, ever since ([watchingSinceCaughtUp]), and the
     * server's clock is known in this process ([clockKnown]). Anything else (the app or its service restarted, «Biblioteca» was
     * off, another room was in use, a backup was restored) may hide an OLD change nobody saw being made: stamped now it would beat
     * newer changes of the other devices. Then the room decides first and the diff follows, as before — except for a change SEEN
     * being made by this instance before it caught up: row 900 journals it with its moment ([journalLocked]).
     */
    private suspend fun stampOfflineLocked(announced: Pair<Mono, Long>? = null) {
        if (!watchingSinceCaughtUp || !clockKnown() || sinceStore.freshStart()) {
            journalLocked(announced)
            return
        }
        convertJournalLocked()
        val ledger = ledger()
        val current = adapter.readLocal(LibrarySyncCore.PlaylistIds.of(ledger)) { key -> ledger.containsKey(key) }
        val fresh = LibrarySyncCore.localOps(current, ledger, clock())
        if (fresh.isEmpty()) return
        fresh.forEach { ledger[it.key] = it }
        store.append(fresh)
        Timber.tag(TAG).i("LIB_SYNC offline changes recorded=%d", fresh.size)
    }

    // ── row 900: the journal ───────────────────────────────────────────────────────────────────────────────────────────────────

    private fun dropBaseline() {
        watchGen++
        baseline = null
    }

    private suspend fun readBaselineLocked() {
        val gen = watchGen
        val ledger = ledger()
        val current = adapter.readLocal(LibrarySyncCore.PlaylistIds.of(ledger)) { key -> ledger.containsKey(key) }
        if (gen == watchGen) baseline = HashMap(current)
    }

    /**
     * A change seen being made before this instance can date it against the room: written to the [journal] at once with the moment
     * it was announced ([announced], from [kick]) on the monotonic clock, so it survives a restart and an offline day with its time.
     * Nothing is written while not watching (no baseline yet: this read only sets it), with «Biblioteca» off, or after a restore.
     */
    private suspend fun journalLocked(announced: Pair<Mono, Long>?) {
        if (!isEnabled() || sinceStore.freshStart()) return
        val gen = watchGen
        val ledger = ledger()
        val current = adapter.readLocal(LibrarySyncCore.PlaylistIds.of(ledger)) { key -> ledger.containsKey(key) }
        if (gen != watchGen) return
        val before = baseline
        baseline = HashMap(current)
        if (before == null) return
        val changed = JournalRules.observed(before, current)
        if (changed.isEmpty()) return
        val (at, wall) = announced ?: (mono() to System.currentTimeMillis())
        val first = journal.nextSeq(changed.size)
        val entries = changed.entries.mapIndexed { i, (key, value) -> JournalEntry(first + i, key, value, at.boot, at.elapsedMs, wall) }
        journal.append(entries)
        journalEntriesLocked().addAll(entries)
        Timber.tag(TAG).i("LIB_SYNC journal recorded=%d", entries.size)
    }

    private fun journalEntriesLocked(): MutableList<JournalEntry> =
        journalCache ?: journal.load().toMutableList().also { journalCache = it }

    /**
     * With the room's clock known, the journal becomes pending registers of the ledger with the room's time of the moment each change
     * was made ([JournalRules.toRegs]); they then compete newest-wins with what the room sends and stay pending until it acknowledges
     * them. Written to the ledger before the journal is cleared, so a crash in between only converts them again (to the same thing).
     */
    private suspend fun convertJournalLocked() {
        if (!clockKnown()) return
        val entries = journalEntriesLocked()
        if (entries.isEmpty()) return
        val ledger = ledger()
        val (regs, undated) = JournalRules.toRegs(entries, ledger, clock(), mono(), System.currentTimeMillis(), journal.calibration())
        regs.forEach { ledger[it.key] = it }
        store.append(regs)
        journal.clear()
        entries.clear()
        Timber.tag(TAG).i("LIB_SYNC journal dated=%d undated=%d", regs.size, undated)
    }

    private var calibrationSaved: Calibration? = null

    /** At most once an hour, or when the offset moved by more than a second: one tiny file write, never per message. */
    private fun saveCalibrationLocked() {
        if (!clockKnown()) return
        val wall = System.currentTimeMillis()
        val cal = Calibration(clock() - wall, wall)
        val last = calibrationSaved
        if (last != null && kotlin.math.abs(last.offsetMs - cal.offsetMs) <= 1_000L && wall - last.atWallMs < 3_600_000L) return
        runCatching { journal.saveCalibration(cal) }.onSuccess { calibrationSaved = cal }
    }

    /** Row 900: what the batch just applied changed here is not a change of the user's: the baseline follows it. */
    private suspend fun followBaselineLocked(applied: List<Reg>) {
        val base = baseline ?: return
        if (applied.any { it.key.startsWith("pl:") || it.key.startsWith("pb:") }) {
            // A list may now travel under another key (row 740): read it again rather than guess.
            readBaselineLocked()
            return
        }
        for (reg in applied) {
            if (reg.parked) continue
            if (reg.value == LibrarySyncCore.OFF) base.remove(reg.key) else base[reg.key] = reg.value
        }
    }

    /** Handles the library messages of the room. True when [msg] was one of them. */
    suspend fun handle(msg: JsonObject): Boolean {
        when ((msg["t"] as? JsonPrimitive)?.contentOrNull) {
            "lib_state" -> if (isEnabled() && connected && !stopped) mutex.withLock {
                runCatching { onStateLocked(msg) }.onFailure {
                    Timber.tag(TAG).w("LIB_SYNC state failed: %s", it.javaClass.simpleName)
                    DeviceSyncHub.libraryStatus.value = DeviceSyncHub.LibraryStatus.Failed(it.javaClass.simpleName)
                }
            }
            "lib_ops" -> if (isEnabled()) requestPull()
            "lib_ack" -> mutex.withLock { onAckLocked(msg) }
            else -> return false
        }
        return true
    }

    private fun requestPull() {
        synchronized(pullLock) {
            if (pullInFlight) {
                // Something new was announced while a pull is on its way: ask once more when it lands, not now.
                pullAgain = true
                return
            }
            pullInFlight = true
        }
        sendPull()
    }

    private fun sendPull() {
        if (stopped) return
        DeviceSyncHub.libraryStatus.value = DeviceSyncHub.LibraryStatus.Syncing
        if (!sendMessage(buildJsonObject { put("t", "lib_pull"); put("since", since) })) {
            synchronized(pullLock) { pullInFlight = false; pullAgain = false }
            DeviceSyncHub.libraryStatus.value = DeviceSyncHub.LibraryStatus.Failed("sin conexión")
            Timber.tag(TAG).w("LIB_SYNC pull not sent: no connection")
            return
        }
        // A server that does not answer is a server that does not understand: do not wait for ever in silence.
        pullWatchdog?.cancel()
        pullWatchdog = scope.launch {
            delay(timing.pullTimeoutMs)
            if (stopped) return@launch
            synchronized(pullLock) { pullInFlight = false } // a lost answer must not block every later pull
            if (DeviceSyncHub.libraryStatus.value == DeviceSyncHub.LibraryStatus.Syncing && !pulledOnce) {
                DeviceSyncHub.libraryStatus.value = DeviceSyncHub.LibraryStatus.Failed("el servidor no responde")
                Timber.tag(TAG).w("LIB_SYNC no answer to lib_pull")
            }
        }
    }

    /** The pull in flight was answered; true when another one must follow right away. */
    private fun pullLanded(more: Boolean): Boolean = synchronized(pullLock) {
        val again = more || pullAgain
        pullAgain = false
        pullInFlight = again
        again
    }

    // ── incoming ───────────────────────────────────────────────────────────────────────────────────────────────────────────────

    private suspend fun onStateLocked(msg: JsonObject) {
        val ledger = ledger()
        // Row 900: what was journaled before this page competes with it by when it was made, not by when it arrives.
        convertJournalLocked()
        if ((msg["reset"] as? JsonPrimitive)?.booleanOrNull == true) {
            // The room was emptied or recreated: it has nothing of ours any more. Everything we know is pending again.
            since = 0L
            ledger.replaceAll { _, r -> if (LibrarySyncCore.isHeld(r)) r else r.copy(pending = true) }
            store.rewrite(ledger)
            sentKeys.clear()
            inFlight.clear()
        }
        val incoming = ((msg["items"] as? JsonArray).orEmpty()).mapNotNull { item ->
            val a = item as? JsonArray ?: return@mapNotNull null
            if (a.size < 3) return@mapNotNull null
            val key = (a[0] as? JsonPrimitive)?.contentOrNull ?: return@mapNotNull null
            val ts = (a[1] as? JsonPrimitive)?.longOrNull ?: return@mapNotNull null
            val value = (a[2] as? JsonPrimitive)?.contentOrNull ?: return@mapNotNull null
            Reg(key, ts, value)
        }
        var accepted = LibrarySyncCore.acceptedFrom(ledger, incoming)
        // Row 740: which key each playlist had before this batch and has with it, so a register only reaches the list it names.
        val before = LibrarySyncCore.PlaylistIds.of(ledger)
        var plan = LibrarySyncCore.plan(accepted, before, LibrarySyncCore.PlaylistIds.of(ledger, accepted))
        val known = ledger.values.count { it.value != LibrarySyncCore.OFF && !LibrarySyncCore.isMeta(it.key) }
        var held = emptyList<Reg>()
        if (LibrarySyncCore.brakeTrips(plan.removalCount, known)) {
            // A pile of removals at once is not a person tidying up. Keep every addition, hold every removal, and say so.
            held = accepted.filter { it.value == LibrarySyncCore.OFF && !LibrarySyncCore.isMeta(it.key) }
            accepted = accepted - held.toSet()
            plan = LibrarySyncCore.plan(accepted, before, LibrarySyncCore.PlaylistIds.of(ledger, accepted + held.map { it.copy(parked = true) }))
            Timber.tag(TAG).w("LIB_SYNC held removals=%d known=%d", held.size, known)
            // Row 807: the app offers «Revisar» once per batch (MainActivity), which opens the screen that applies or discards them.
            DeviceSyncHub.heldTrip.value = DeviceSyncHub.HeldTrip((DeviceSyncHub.heldTrip.value?.token ?: 0) + 1, held.size)
        }
        val more = (msg["more"] as? JsonPrimitive)?.booleanOrNull == true
        // Row 746: what was parked is retried once, after the LAST page (a page in the middle cannot be the end of a download).
        val parkedBefore = if (more) emptyList() else ledger.values.filter { it.parked && !LibrarySyncCore.isHeld(it) }
        val result = adapter.apply(plan)
        val stored = accepted.map { it.copy(pending = false, parked = it.key in result.unplaced || it.key in plan.undecoded) } +
            held.map { it.copy(pending = false, parked = true) }
        stored.forEach { ledger[it.key] = it }
        store.append(stored)
        followBaselineLocked(stored)
        store.compactIfBloated(ledger)
        since = maxOf(since, (msg["seq"] as? JsonPrimitive)?.longOrNull ?: since)
        sinceStore.put(since)
        Timber.tag(TAG).i("LIB_SYNC pulled items=%d applied=%d unresolved=%d", incoming.size, result.applied, result.unresolved)

        if (pullLanded(more)) {
            // More pages, or the room announced new changes while this one was on its way: the next pull goes out now.
            sendPull()
            if (more) return
        }
        // Caught up: from here the room is the source of truth between the devices, and what is different HERE goes out.
        pulledOnce = true
        if (freshStart) {
            freshStart = false
            sinceStore.endFreshStart()
        }
        pullWatchdog?.cancel()
        Timber.tag(TAG).i("LIB_SYNC caught up since=%d known=%d", since, ledger.size)
        DeviceSyncHub.libraryOwnedByDevices = connected && isEnabled()
        val retry = parkedBefore.filter { ledger[it.key] == it }
        if (retry.isNotEmpty()) {
            val ids = LibrarySyncCore.PlaylistIds.of(ledger)
            val retryPlan = LibrarySyncCore.plan(retry, ids, ids)
            val again = adapter.apply(retryPlan)
            val placed = retry.filter { it.key !in again.unplaced && it.key !in retryPlan.undecoded }.map { it.copy(parked = false) }
            placed.forEach { ledger[it.key] = it }
            store.append(placed)
        }
        pushDiffLocked()
        // Row 900: from here the offline stamp dates every change; the journal's baseline is not needed, and the room's clock is
        // remembered for a change journaled after the next reboot (JournalRules.realTime).
        baseline = null
        saveCalibrationLocked()
        // Ledger and database agree now and the watchers are running: from here a difference is a change seen being made here.
        watchingSinceCaughtUp = isEnabled()
        publishStatusLocked()
    }

    private fun publishStatusLocked() {
        val ledger = ledgerMap ?: return
        DeviceSyncHub.libraryStatus.value = DeviceSyncHub.LibraryStatus.Synced(
            atMs = System.currentTimeMillis(),
            known = ledger.values.count { it.value != LibrarySyncCore.OFF && !LibrarySyncCore.isMeta(it.key) },
            pending = ledger.values.count { it.pending },
        )
        publishHeldLocked()
    }

    /** Row 807: what the brake holds, as counts per key family (`lk`, `dk`, `pl`, `pm`, `ab`, `pn`) — never which items. */
    private fun publishHeldLocked() {
        if (stopped) return
        val ledger = ledgerMap ?: return
        DeviceSyncHub.heldRemovals.value =
            ledger.values.filter { LibrarySyncCore.isHeld(it) }.groupingBy { it.key.substringBefore(':') }.eachCount()
    }

    /**
     * Row 807 (owner 2026-11-24, H10) — the user's answer to the removals the brake held ([LibrarySyncCore.isHeld]). [apply] =
     * «Aplicar»: they are made here through the same [LibraryBackend.apply] as any change from the room. Otherwise «Descartar»: the
     * copies here are kept and go back to the room as the newest change ([LibrarySyncCore.localOps] sees a "0" in the ledger and the
     * item still here), so the other devices get them again — union: nothing is deleted without this explicit tap. Returns how many
     * were resolved.
     */
    suspend fun resolveHeld(apply: Boolean): Int = mutex.withLock {
        val ledger = ledger()
        val held = ledger.values.filter { LibrarySyncCore.isHeld(it) }
        if (held.isEmpty()) return@withLock 0
        val released = held.map { it.copy(parked = false) }
        if (apply) {
            // Row 740: «before» is the ledger as it was when the removals arrived (the held lists still live), so each removal
            // reaches the row whose key it names and nothing else.
            val before = LibrarySyncCore.PlaylistIds.of(ledger, held.map { it.copy(value = LibrarySyncCore.ON) })
            adapter.apply(LibrarySyncCore.plan(released, before, LibrarySyncCore.PlaylistIds.of(ledger, released)))
        }
        released.forEach { ledger[it.key] = it }
        store.append(released)
        Timber.tag(TAG).i("LIB_SYNC held removals resolved apply=%b count=%d", apply, held.size)
        if (!apply && connected && pulledOnce && isEnabled()) pushDiffLocked()
        publishHeldLocked()
        held.size
    }

    /**
     * Row 900: a room from lib 3 on lists in `ids` every op it is done with; only those stop being pending here. The others (a full
     * room) stay pending and go out again with the next pass. An older room sends no `ids`: the whole batch is settled, as before.
     */
    private suspend fun onAckLocked(msg: JsonObject) {
        val batch = inFlight.removeFirstOrNull() ?: return
        val done = (msg["ids"] as? JsonArray)?.mapNotNullTo(HashSet()) { (it as? JsonPrimitive)?.contentOrNull }
        val ledger = ledger()
        val settled = ArrayList<Reg>()
        for (sent in batch) {
            sentKeys.remove(sent.key)
            if (done != null && LibrarySyncCore.opId(sent) !in done) continue
            // Only if it was not changed again while the room was answering.
            val now = ledger[sent.key]
            if (now != null && now.ts == sent.ts && now.value == sent.value && now.pending) {
                val done = now.copy(pending = false)
                ledger[sent.key] = done
                settled += done
            }
        }
        store.append(settled)
        if (pulledOnce) publishStatusLocked()
    }

    // ── outgoing ───────────────────────────────────────────────────────────────────────────────────────────────────────────────

    private suspend fun pushDiffLocked() {
        if (!connected || !isEnabled() || !pulledOnce) {
            // The first thing to look for in a log when a change «did not go out»: it was waiting for the first full pull.
            Timber.tag(TAG).i("LIB_SYNC diff held: connected=%b enabled=%b caughtUp=%b", connected, isEnabled(), pulledOnce)
            return
        }
        convertJournalLocked()
        val ledger = ledger()
        val current = adapter.readLocal(LibrarySyncCore.PlaylistIds.of(ledger)) { key -> ledger.containsKey(key) }
        val released = ledger.values.filter {
            it.parked && (if (LibrarySyncCore.isHeld(it)) it.key !in current else current[it.key] == it.value)
        }.map { it.copy(parked = false) }
        released.forEach { ledger[it.key] = it }
        store.append(released)
        // Row 807: a held removal whose item is already gone here is no longer held; the count must not wait for the next ack.
        if (released.any { it.value == LibrarySyncCore.OFF }) publishHeldLocked()
        val fresh = LibrarySyncCore.localOps(current, ledger, clock())
        fresh.forEach { ledger[it.key] = it }
        store.append(fresh)

        // Everything not yet acknowledged, parents before children, in batches the room accepts.
        val pending = ledger.values.filter { it.pending && it.key !in sentKeys }.sortedBy { rank(it.key) }
        if (pending.isEmpty()) return
        var sentCount = 0
        for (batch in pending.chunked(BATCH)) {
            val message = buildJsonObject {
                put("t", "lib_push")
                put("ops", buildJsonArray {
                    batch.forEach { r ->
                        add(buildJsonArray {
                            add(JsonPrimitive(r.key))
                            add(JsonPrimitive(r.ts))
                            add(JsonPrimitive(r.value))
                            add(JsonPrimitive(LibrarySyncCore.opId(r)))
                        })
                    }
                })
            }
            if (!sendMessage(message)) break
            inFlight.addLast(batch)
            batch.forEach { sentKeys += it.key }
            sentCount += batch.size
        }
        Timber.tag(TAG).i("LIB_SYNC pushed changes=%d pending=%d", fresh.size, sentCount)
        store.compactIfBloated(ledger)
    }

    private fun rank(key: String) = when (key.substringBefore(':')) {
        "sg", "ag" -> 0
        "pb" -> 0
        "pl" -> 1
        "pm", "ab" -> 2
        "lk" -> 3
        else -> 4
    }

    private suspend fun ledger(): HashMap<String, Reg> =
        ledgerMap ?: store.load().also { ledgerMap = it }

    private companion object {
        const val TAG = "DeviceSync"
        const val BATCH = 200
        const val MIN_SERVER_LIB = 2
    }
}

/** What an [LibraryBackend.apply] did: changes made, and registers it could not apply (a playlist or a song this device does not have). */
data class ApplyResult(val applied: Int, val unresolved: Int, val unplaced: Set<String> = emptySet())

/** What [LibrarySync] needs from the app: the present facts, and a way to write what other devices changed. */
interface LibraryBackend {
    /** [playlists] = what the ledger says about the playlists, to give each one its key (row 740). */
    suspend fun readLocal(playlists: LibrarySyncCore.PlaylistIds, songMetaKnown: (String) -> Boolean): Map<String, String>
    suspend fun apply(plan: LibrarySyncCore.Plan): ApplyResult
}

/** The ledger's storage. */
interface LedgerPersistence {
    fun load(): HashMap<String, Reg>
    fun append(regs: Collection<Reg>)
    fun compactIfBloated(map: Map<String, Reg>): Boolean
    fun rewrite(map: Map<String, Reg>)
}

/** Where the last seen sequence number lives. */
interface SinceStore {
    fun get(): Long
    fun put(value: Long)

    /** True after a backup was restored, until the first pull catches up: the room wins over the restored library, except what the brake holds. */
    fun freshStart(): Boolean = false
    fun endFreshStart() {}
}
