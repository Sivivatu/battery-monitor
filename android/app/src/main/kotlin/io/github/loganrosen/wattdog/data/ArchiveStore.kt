package io.github.loganrosen.wattdog.data

import android.content.ContentValues
import android.content.Context
import android.database.Cursor
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import android.util.Log
import io.github.loganrosen.wattdog.batterystats.analysis.DeltaResult
import io.github.loganrosen.wattdog.batterystats.analysis.ReportDelta
import io.github.loganrosen.wattdog.batterystats.model.AppUid
import io.github.loganrosen.wattdog.batterystats.model.BatteryStatsReport
import io.github.loganrosen.wattdog.batterystats.parse.BatteryStatsParser
import java.io.Closeable
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject

enum class ArchiveCaptureStatus { BASELINE, VALID, RESET, GAP, PARTIAL }

data class ArchiveBoundary(val capturedAtMillis: Long, val status: ArchiveCaptureStatus, val reason: String)

data class ArchivedApp(
    val uid: Int,
    val label: String,
    val packages: List<String>,
    val totalMah: Double,
    val foregroundMah: Double?,
    val backgroundMah: Double?,
    val foregroundServiceMah: Double?,
    val cachedMah: Double?,
    val dailyUsage: List<ArchivedDay> = emptyList(),
)

/** Intervals are assigned to the UTC date of capture end, not interpolated at midnight. */
data class ArchivedDay(val dayStartMillis: Long, val totalMah: Double, val foregroundMah: Double?, val backgroundMah: Double?)

data class ArchiveSummary(
    val fromMillis: Long,
    val toMillis: Long,
    val intervalCount: Int = 0,
    val onBatteryMillis: Long = 0,
    val apps: List<ArchivedApp> = emptyList(),
    val boundaries: List<ArchiveBoundary> = emptyList(),
    val boundaryCount: Int = boundaries.size,
    val days: List<ArchivedDay> = emptyList(),
)

/**
 * Local, durable interval archive independent of the short-lived raw snapshot cache.
 * A transaction replaces one raw baseline and appends immutable interval rows together.
 * Only whole intervals inside the requested range are summed: no hourly interpolation.
 */
class ArchiveStore(context: Context, databaseName: String = "battery-history.db") : Closeable {
    private val helper = ArchiveDatabase(context.applicationContext, databaseName)
    private val mutex = Mutex()
    private val _revision = MutableStateFlow(0L)
    val revision = _revision.asStateFlow()
    private val _error = MutableStateFlow<String?>(null)
    val error = _error.asStateFlow()

    /** Archive failure must remain visible without preventing live battery diagnostics. */
    suspend fun recordSafely(
        raw: String,
        report: BatteryStatsReport,
        labels: Map<AppUid, AppLabel>,
        capturedAtMillis: Long = System.currentTimeMillis(),
    ) {
        try {
            record(raw, report, labels, capturedAtMillis)
            _error.value = null
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.e("BatteryArchive", "Unable to save battery history", e)
            _error.value = "History could not be saved: ${e.message ?: e.javaClass.simpleName}"
        }
    }

    suspend fun record(
        raw: String,
        report: BatteryStatsReport,
        labels: Map<AppUid, AppLabel> = emptyMap(),
        capturedAtMillis: Long = System.currentTimeMillis(),
    ): ArchiveCaptureStatus = io {
        val db = helper.writableDatabase
        db.beginTransaction()
        try {
            val previous = previous(db)
            val identities = identities(report, labels)
            val decision = decide(previous, raw, report, capturedAtMillis)
            val captureId = insertCapture(db, report, capturedAtMillis, decision)
            val status = if (decision.delta != null && previous != null) {
                saveInterval(db, captureId, previous, report, decision.delta, identities, capturedAtMillis)
            } else {
                decision.status
            }
            if (status != decision.status) {
                db.update(
                    "captures",
                    ContentValues().apply {
                        put("status", status.name)
                        put("reason", identityBoundaryReason(previous!!.identities, identities))
                    },
                    "id = ?",
                    arrayOf(captureId.toString()),
                )
            }
            // A late arrival must never replace the newest trustworthy baseline.
            if (previous == null || capturedAtMillis > previous.at) {
                db.execSQL("DELETE FROM baseline")
                if (isComplete(raw, report)) {
                    db.insertOrThrow(
                        "baseline",
                        null,
                        ContentValues().apply {
                            put("id", 1)
                            put("captured_at", capturedAtMillis)
                            put("raw", raw)
                            put("identities", identities.toString())
                        },
                    )
                }
            }
            prune(db, maxOf(capturedAtMillis, previous?.at ?: capturedAtMillis) - RETENTION_MILLIS)
            db.setTransactionSuccessful()
            _revision.value += 1
            status
        } finally {
            db.endTransaction()
        }
    }

    suspend fun summary(fromMillis: Long = 0, toMillis: Long = System.currentTimeMillis()): ArchiveSummary = io {
        require(fromMillis <= toMillis) { "History range ends before it starts" }
        val db = helper.readableDatabase
        val args = arrayOf(fromMillis.toString(), toMillis.toString())
        val totals = db.rawQuery(
            "SELECT COUNT(*), COALESCE(SUM(on_battery_ms), 0) FROM intervals WHERE start_at >= ? AND end_at <= ?",
            args,
        ).use { c ->
            c.moveToFirst()
            c.getInt(0) to c.getLong(1)
        }
        val apps = readApps(db, args)
        val boundaries = db.rawQuery(
            "SELECT captured_at, status, reason FROM captures WHERE captured_at >= ? AND captured_at <= ? AND status != ? ORDER BY captured_at DESC LIMIT 20",
            args + ArchiveCaptureStatus.VALID.name,
        ).use { c ->
            buildList {
                while (c.moveToNext()) add(ArchiveBoundary(c.getLong(0), ArchiveCaptureStatus.valueOf(c.getString(1)), c.getString(2)))
            }
        }
        val boundaryCount = db.rawQuery(
            "SELECT COUNT(*) FROM captures WHERE captured_at >= ? AND captured_at <= ? AND status != ?",
            args + ArchiveCaptureStatus.VALID.name,
        ).use { c ->
            c.moveToFirst()
            c.getInt(0)
        }
        val days = apps.flatMap { it.dailyUsage }.groupBy { it.dayStartMillis }.map { (day, entries) ->
            ArchivedDay(
                day,
                entries.sumOf {
                    it.totalMah
                },
                entries.map { it.foregroundMah }.knownSum(),
                entries.map { it.backgroundMah }.knownSum(),
            )
        }.sortedByDescending { it.dayStartMillis }
        ArchiveSummary(fromMillis, toMillis, totals.first, totals.second, apps, boundaries, boundaryCount, days)
    }

    override fun close() = helper.close()

    private data class Previous(val at: Long, val report: BatteryStatsReport, val identities: JSONObject)
    private data class Decision(val status: ArchiveCaptureStatus, val reason: String, val delta: BatteryStatsReport? = null)

    private fun previous(db: SQLiteDatabase): Previous? =
        db.rawQuery("SELECT captured_at, raw, identities FROM baseline WHERE id = 1", null).use { c ->
            if (!c.moveToFirst()) null else Previous(c.getLong(0), BatteryStatsParser.parse(c.getString(1)), JSONObject(c.getString(2)))
        }

    private fun decide(previous: Previous?, raw: String, report: BatteryStatsReport, at: Long): Decision {
        if (previous != null && at <= previous.at) {
            return Decision(ArchiveCaptureStatus.GAP, "Stale capture timestamp; newest baseline retained")
        }
        if (!isComplete(
                raw,
                report,
            )
        ) {
            return Decision(ArchiveCaptureStatus.PARTIAL, "Missing session, on-battery time or per-app power section")
        }
        if (previous == null) return Decision(ArchiveCaptureStatus.BASELINE, "First complete reading; waiting for the next capture")
        val delta = ReportDelta.compute(previous.report, report, includeUnchangedUidPower = true)
        if (delta is DeltaResult.StatsReset) return Decision(ArchiveCaptureStatus.RESET, delta.reason)
        val wallMillis = at - previous.at
        if (wallMillis <= 0 || wallMillis > MAX_INTERVAL_MILLIS) {
            return Decision(ArchiveCaptureStatus.GAP, "Capture gap exceeded three hours or the clock moved backwards")
        }
        val valid = (delta as DeltaResult.Ok).delta
        if (valid.onBattery.realtime.inWholeMilliseconds > wallMillis + CLOCK_TOLERANCE_MILLIS) {
            return Decision(ArchiveCaptureStatus.GAP, "Battery accounting time exceeds elapsed wall-clock time")
        }
        return Decision(ArchiveCaptureStatus.VALID, "Validated interval", valid)
    }

    private fun isComplete(raw: String, report: BatteryStatsReport): Boolean =
        !report.startClockTime.isNullOrBlank() && report.hasOnBatteryTime && raw.contains("Estimated power use (mAh):") &&
            report.uidPower.isNotEmpty()

    private fun identities(report: BatteryStatsReport, labels: Map<AppUid, AppLabel>) = JSONObject().apply {
        report.uidPower.forEach { app ->
            val label = labels[app.uid]
            val packages = label?.packages ?: report.uids[app.uid]?.packages.orEmpty()
            put(
                app.uid.raw.toString(),
                JSONObject().apply {
                    put("label", label?.label ?: app.uid.toString())
                    put("packages", JSONArray(packages.sorted()))
                    put("metadata", metadata(packages, label?.packageIdentities.orEmpty()))
                },
            )
        }
    }

    private fun insertCapture(db: SQLiteDatabase, report: BatteryStatsReport, at: Long, decision: Decision) = db.insertOrThrow(
        "captures",
        null,
        ContentValues().apply {
            put("captured_at", at)
            put("session", report.startClockTime)
            put("status", decision.status.name)
            put("reason", decision.reason)
        },
    )

    private fun saveInterval(
        db: SQLiteDatabase,
        captureId: Long,
        previous: Previous,
        report: BatteryStatsReport,
        delta: BatteryStatsReport,
        identities: JSONObject,
        at: Long,
    ): ArchiveCaptureStatus {
        db.insertOrThrow(
            "intervals",
            null,
            ContentValues().apply {
                put("id", captureId)
                put("start_at", previous.at)
                put("end_at", at)
                put("on_battery_ms", delta.onBattery.realtime.inWholeMilliseconds)
            },
        )
        val missingUids = previous.report.uidPower.map { it.uid }.toSet() - report.uidPower.map { it.uid }.toSet()
        val excluded = missingUids.isNotEmpty() || report.uidPower.any { app -> !sameIdentity(previous.identities, identities, app.uid) }
        delta.uidPower.filter { sameIdentity(previous.identities, identities, it.uid) }.forEach { app ->
            val identity = identities.getJSONObject(app.uid.raw.toString())
            val packages = identity.getJSONArray("packages").toString()
            // Unresolved UIDs cannot safely be merged across accounting sessions.
            val baseKey = if (packages == "[]") "${report.startClockTime}:${app.uid.raw}" else "${app.uid.raw}:$packages"
            val versions = identity.getJSONArray("metadata")
            // Preserve legacy aggregation for wholly unknown metadata; known versions are separate histories.
            val hasKnownMetadata = (0 until versions.length()).any { index ->
                val entry = versions.getJSONObject(index)
                !entry.isNull("versionCode") || !entry.isNull("versionName") || !entry.isNull("firstInstallTime")
            }
            val key = if (hasKnownMetadata) "$baseKey:$versions" else baseKey
            db.insertOrThrow(
                "app_usage",
                null,
                ContentValues().apply {
                    put("interval_id", captureId)
                    put("identity_key", key)
                    put("uid", app.uid.raw)
                    put("label", identity.getString("label"))
                    put("packages", packages)
                    put("package_metadata", versions.toString())
                    put("total_mah", app.totalMah)
                    put("fg_mah", app.foregroundMah)
                    put("bg_mah", app.backgroundMah)
                    put("fgs_mah", app.foregroundServiceMah)
                    put("cached_mah", app.cachedMah)
                },
            )
        }
        return if (excluded) ArchiveCaptureStatus.PARTIAL else ArchiveCaptureStatus.VALID
    }

    private fun sameIdentity(before: JSONObject, after: JSONObject, uid: AppUid): Boolean {
        val old = before.optJSONObject(uid.raw.toString()) ?: return false
        val current = after.optJSONObject(uid.raw.toString()) ?: return false
        return old.getJSONArray("packages").toString() == current.getJSONArray("packages").toString() &&
            identityMetadata(old).toString() == identityMetadata(current).toString()
    }

    private fun metadata(packages: List<String>, identities: List<PackageIdentity>): JSONArray = JSONArray().apply {
        packages.sorted().forEach { name ->
            val entry = identities.firstOrNull { it.packageName == name }
            put(
                JSONObject().apply {
                    put("packageName", name)
                    put("versionCode", entry?.versionCode ?: JSONObject.NULL)
                    put("versionName", entry?.versionName ?: JSONObject.NULL)
                    put("firstInstallTime", entry?.firstInstallTime ?: JSONObject.NULL)
                },
            )
        }
    }

    private fun identityMetadata(identity: JSONObject): JSONArray = identity.optJSONArray("metadata") ?: run {
        val packages = identity.getJSONArray("packages")
        metadata(List(packages.length()) { packages.getString(it) }, emptyList())
    }

    private fun identityBoundaryReason(before: JSONObject, after: JSONObject): String {
        for (uid in after.keys()) {
            val old = before.optJSONObject(uid) ?: return "App identity lacked a baseline; affected app excluded"
            val current = after.getJSONObject(uid)
            if (old.getJSONArray("packages").toString() != current.getJSONArray("packages").toString()) {
                return "UID package ownership changed; affected app excluded"
            }
            val oldMetadata = identityMetadata(old)
            val newMetadata = identityMetadata(current)
            if (oldMetadata.toString() != newMetadata.toString()) {
                val installChanged = (0 until oldMetadata.length()).any { index ->
                    val previous = oldMetadata.getJSONObject(index)
                    val next = newMetadata.getJSONObject(index)
                    !previous.isNull("firstInstallTime") && !next.isNull("firstInstallTime") &&
                        previous.getLong("firstInstallTime") != next.getLong("firstInstallTime")
                }
                return if (installChanged) {
                    "Package installation identity changed; affected app excluded"
                } else {
                    "Package version or metadata availability changed; affected app excluded"
                }
            }
        }
        return "One or more app identities lacked a baseline; affected apps excluded"
    }

    private fun readApps(db: SQLiteDatabase, args: Array<String>): List<ArchivedApp> {
        val stateSums = listOf("fg_mah", "bg_mah", "fgs_mah", "cached_mah").joinToString(", ") {
            "CASE WHEN COUNT($it) = COUNT(*) THEN SUM($it) ELSE NULL END"
        }
        val daily = readDailyApps(db, args)
        return db.rawQuery(
            "SELECT uid, label, packages, SUM(total_mah), $stateSums, identity_key " +
                "FROM app_usage JOIN intervals ON interval_id = intervals.id " +
                "WHERE start_at >= ? AND end_at <= ? GROUP BY identity_key ORDER BY SUM(total_mah) DESC",
            args,
        ).use { c ->
            buildList {
                while (c.moveToNext()) {
                    val packages = JSONArray(c.getString(2))
                    add(
                        ArchivedApp(
                            c.getInt(0),
                            c.getString(1),
                            List(packages.length()) { packages.getString(it) },
                            c.getDouble(3),
                            c.nullableDouble(4),
                            c.nullableDouble(5),
                            c.nullableDouble(6),
                            c.nullableDouble(7),
                            daily[c.getString(8)].orEmpty(),
                        ),
                    )
                }
            }
        }
    }

    private fun readDailyApps(db: SQLiteDatabase, args: Array<String>): Map<String, List<ArchivedDay>> {
        val stateSums = listOf("fg_mah", "bg_mah").joinToString(", ") {
            "CASE WHEN COUNT($it) = COUNT(*) THEN SUM($it) ELSE NULL END"
        }
        val day = "(end_at / 86400000) * 86400000"
        return db.rawQuery(
            "SELECT identity_key, $day AS day_start, SUM(total_mah), $stateSums " +
                "FROM app_usage JOIN intervals ON interval_id = intervals.id " +
                "WHERE start_at >= ? AND end_at <= ? GROUP BY identity_key, day_start ORDER BY day_start DESC",
            args,
        ).use { c ->
            buildList {
                while (c.moveToNext()) {
                    add(
                        c.getString(0) to ArchivedDay(c.getLong(1), c.getDouble(2), c.nullableDouble(3), c.nullableDouble(4)),
                    )
                }
            }.groupBy({ it.first }, { it.second })
        }
    }

    private fun List<Double?>.knownSum(): Double? = if (any { it == null }) null else sumOf { it!! }

    private fun Cursor.nullableDouble(index: Int) = if (isNull(index)) null else getDouble(index)
    private suspend fun <T> io(block: () -> T): T = withContext(Dispatchers.IO) { mutex.withLock { block() } }

    private fun prune(db: SQLiteDatabase, cutoff: Long) {
        val args = arrayOf(cutoff.toString())
        db.delete("app_usage", "interval_id IN (SELECT id FROM intervals WHERE end_at < ?)", args)
        db.delete("intervals", "end_at < ?", args)
        db.delete("captures", "captured_at < ?", args)
    }

    private companion object {
        const val MAX_INTERVAL_MILLIS = 3 * 3_600_000L
        const val CLOCK_TOLERANCE_MILLIS = 60_000L
        const val RETENTION_MILLIS = 90 * 24 * 3_600_000L
    }
}

private class ArchiveDatabase(context: Context, name: String) : SQLiteOpenHelper(context, name, null, 2) {
    override fun onConfigure(db: SQLiteDatabase) {
        db.setForeignKeyConstraintsEnabled(true)
    }

    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL(
            "CREATE TABLE captures (id INTEGER PRIMARY KEY, captured_at INTEGER NOT NULL, session TEXT, status TEXT NOT NULL, reason TEXT NOT NULL)",
        )
        db.execSQL("CREATE INDEX captures_time ON captures(captured_at)")
        db.execSQL(
            "CREATE TABLE intervals (id INTEGER PRIMARY KEY REFERENCES captures(id), start_at INTEGER NOT NULL, end_at INTEGER NOT NULL, on_battery_ms INTEGER NOT NULL)",
        )
        db.execSQL("CREATE INDEX intervals_time ON intervals(start_at, end_at)")
        db.execSQL("CREATE INDEX intervals_end ON intervals(end_at)")
        db.execSQL(
            "CREATE TABLE app_usage (interval_id INTEGER NOT NULL REFERENCES intervals(id), identity_key TEXT NOT NULL, uid INTEGER NOT NULL, label TEXT NOT NULL, packages TEXT NOT NULL, package_metadata TEXT NOT NULL DEFAULT '[]', total_mah REAL NOT NULL, fg_mah REAL, bg_mah REAL, fgs_mah REAL, cached_mah REAL, PRIMARY KEY(interval_id, identity_key))",
        )
        db.execSQL(
            "CREATE TABLE baseline (id INTEGER PRIMARY KEY CHECK(id = 1), captured_at INTEGER NOT NULL, raw TEXT NOT NULL, identities TEXT NOT NULL)",
        )
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        check(oldVersion == 1 && newVersion == 2) { "A history database migration is required: $oldVersion to $newVersion" }
        db.execSQL("ALTER TABLE app_usage ADD COLUMN package_metadata TEXT NOT NULL DEFAULT '[]'")
    }
}
