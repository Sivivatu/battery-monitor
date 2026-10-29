package io.github.loganrosen.wattdog.data

import android.content.Context
import android.os.Process
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.github.loganrosen.wattdog.batterystats.model.AppUid
import io.github.loganrosen.wattdog.batterystats.parse.BatteryStatsParser
import java.util.UUID
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class PackageHistoryTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val databaseName = "versions-${UUID.randomUUID()}.db"
    private val store = ArchiveStore(context, databaseName)
    private val uid = AppUid(10101)
    private val start = 1_800_000_000_000L
    private val hour = 3_600_000L

    @After fun cleanup() {
        store.close()
        context.deleteDatabase(databaseName)
    }

    @Test fun updateCreatesVersionBoundaryAndPreservesBothHistoricalVersions() = runBlocking {
        record(1, 10.0, identity(1, 100))
        record(2, 16.0, identity(1, 100))
        assertEquals(ArchiveCaptureStatus.PARTIAL, record(3, 20.0, identity(2, 100)))
        record(4, 23.0, identity(2, 100))
        val summary = store.summary(start, start + 5 * hour)
        assertTrue(summary.boundaries.any { it.reason.contains("version") })
        val apps = summary.apps
        assertEquals(2, apps.size)
        assertEquals(listOf(3.0, 6.0), apps.map { it.totalMah }.sorted())
        // Metadata is checked directly in SQLite so the public summary API stays compatible.
        val db = android.database.sqlite.SQLiteDatabase.openDatabase(context.getDatabasePath(databaseName).path, null, 0)
        try {
            db.rawQuery("SELECT package_metadata FROM app_usage ORDER BY interval_id", null).use { cursor ->
                assertTrue(cursor.moveToFirst())
                assertEquals(1L, org.json.JSONArray(cursor.getString(0)).getJSONObject(0).getLong("versionCode"))
                assertTrue(cursor.moveToNext())
                assertEquals(2L, org.json.JSONArray(cursor.getString(0)).getJSONObject(0).getLong("versionCode"))
            }
        } finally {
            db.close()
        }
    }

    @Test fun samePackageReinstallCannotMergeAcrossInstallIdentity() = runBlocking {
        record(1, 10.0, identity(1, 100))
        assertEquals(ArchiveCaptureStatus.PARTIAL, record(2, 16.0, identity(1, 200)))
        val summary = store.summary(start, start + 3 * hour)
        assertTrue(summary.apps.isEmpty())
        assertTrue(summary.boundaries.any { it.reason.contains("installation") })
    }

    @Test fun unknownMetadataStaysUnknownAndCannotBridgeKnownMetadata() = runBlocking {
        record(1, 10.0, identity(null, null))
        assertEquals(ArchiveCaptureStatus.VALID, record(2, 16.0, identity(null, null)))
        assertEquals(ArchiveCaptureStatus.PARTIAL, record(3, 20.0, identity(1, 100)))
        assertEquals(6.0, store.summary(start, start + 4 * hour).apps.single().totalMah, 0.0001)
    }

    @Test fun resolverUsesMetadataOnlyForTheActualCurrentProfileUid() {
        val resolver = UidResolver(context.packageManager)
        val current = resolver.resolve(AppUid(Process.myUid()), listOf(context.packageName))
        assertTrue(current.packageIdentities.isNotEmpty())
        assertTrue(current.packageIdentities.all { it.versionCode != null && it.firstInstallTime != null })
        val otherUid = AppUid(Process.myUid() + AppUid.PER_USER_RANGE)
        val other = resolver.resolve(otherUid, listOf(context.packageName))
        assertEquals(listOf(context.packageName), other.packages)
        assertEquals(1, other.packageIdentities.size)
        assertNull(other.packageIdentities.single().versionCode)
        assertNull(other.packageIdentities.single().firstInstallTime)
    }

    @Test fun versionOneMigrationPreservesRowsAndContinuesItsBaseline() = runBlocking {
        val legacyName = "legacy-${UUID.randomUUID()}.db"
        val legacy = context.openOrCreateDatabase(legacyName, 0, null)
        val raw = """
            Statistics since last charge:
              Start clock time: session
              Time on battery: 2h (100%) realtime, 2h (100%) uptime
              Estimated power use (mAh):
                UID u0a101: 16.0
        """.trimIndent()
        try {
            legacy.execSQL(
                "CREATE TABLE captures (id INTEGER PRIMARY KEY, captured_at INTEGER NOT NULL, session TEXT, status TEXT NOT NULL, reason TEXT NOT NULL)",
            )
            legacy.execSQL(
                "CREATE TABLE intervals (id INTEGER PRIMARY KEY REFERENCES captures(id), start_at INTEGER NOT NULL, end_at INTEGER NOT NULL, on_battery_ms INTEGER NOT NULL)",
            )
            legacy.execSQL(
                "CREATE TABLE app_usage (interval_id INTEGER NOT NULL REFERENCES intervals(id), identity_key TEXT NOT NULL, uid INTEGER NOT NULL, label TEXT NOT NULL, packages TEXT NOT NULL, total_mah REAL NOT NULL, fg_mah REAL, bg_mah REAL, fgs_mah REAL, cached_mah REAL, PRIMARY KEY(interval_id, identity_key))",
            )
            legacy.execSQL(
                "CREATE TABLE baseline (id INTEGER PRIMARY KEY, captured_at INTEGER NOT NULL, raw TEXT NOT NULL, identities TEXT NOT NULL)",
            )
            legacy.execSQL("INSERT INTO captures VALUES(1, ?, 'session', 'VALID', 'legacy')", arrayOf(start + hour))
            legacy.execSQL("INSERT INTO intervals VALUES(1, ?, ?, ?)", arrayOf(start, start + hour, hour))
            legacy.execSQL("INSERT INTO app_usage VALUES(1, 'session:10101', 10101, 'Legacy', '[]', 6.0, NULL, NULL, NULL, NULL)")
            val identities = """{"10101":{"label":"Legacy","packages":[]}}"""
            legacy.execSQL("INSERT INTO baseline VALUES(1, ?, ?, ?)", arrayOf<Any>(start + hour, raw, identities))
            legacy.version = 1
        } finally {
            legacy.close()
        }
        val migrated = ArchiveStore(context, legacyName)
        try {
            assertEquals(6.0, migrated.summary(start, start + 3 * hour).apps.single().totalMah, 0.0001)
            val migratedDb = android.database.sqlite.SQLiteDatabase.openDatabase(context.getDatabasePath(legacyName).path, null, 0)
            try {
                assertEquals(2, migratedDb.version)
                migratedDb.rawQuery("SELECT package_metadata FROM app_usage", null).use { cursor ->
                    assertTrue(cursor.moveToFirst())
                    assertEquals("[]", cursor.getString(0))
                }
            } finally {
                migratedDb.close()
            }
            val current = raw.replace("2h", "3h").replace("16.0", "18.0")
            assertEquals(
                ArchiveCaptureStatus.VALID,
                migrated.record(current, BatteryStatsParser.parse(current), capturedAtMillis = start + 2 * hour),
            )
            assertEquals(8.0, migrated.summary(start, start + 3 * hour).apps.single().totalMah, 0.0001)
        } finally {
            migrated.close()
            context.deleteDatabase(legacyName)
        }
    }

    private fun identity(version: Long?, install: Long?) = AppLabel(
        uid,
        "Example",
        listOf("example.app"),
        false,
        packageIdentities = listOf(PackageIdentity("example.app", version, version?.toString(), install)),
    )

    private suspend fun record(index: Int, total: Double, label: AppLabel): ArchiveCaptureStatus {
        val raw = """
            Statistics since last charge:
              Start clock time: session
              Time on battery: ${index}h (100%) realtime, ${index}h (100%) uptime
              Estimated power use (mAh):
                UID u0a101: $total
        """.trimIndent()
        return store.record(raw, BatteryStatsParser.parse(raw), mapOf(uid to label), start + (index - 1) * hour)
    }
}
