package io.github.loganrosen.wattdog

import android.content.Context
import io.github.loganrosen.wattdog.alerts.AlertStore
import io.github.loganrosen.wattdog.alerts.AnomalyNotifier
import io.github.loganrosen.wattdog.alerts.BackgroundCheck
import io.github.loganrosen.wattdog.alerts.BackgroundChecks
import io.github.loganrosen.wattdog.batterystats.parse.BatteryStatsParser
import io.github.loganrosen.wattdog.data.ArchiveStore
import io.github.loganrosen.wattdog.data.BatteryRepository
import io.github.loganrosen.wattdog.data.SnapshotStore
import io.github.loganrosen.wattdog.data.UidResolver
import io.github.loganrosen.wattdog.privileged.DirectDumpSource
import io.github.loganrosen.wattdog.privileged.DumpTarget
import io.github.loganrosen.wattdog.privileged.PrivilegedAccess
import io.github.loganrosen.wattdog.privileged.ShizukuManager
import io.github.loganrosen.wattdog.privileged.ShizukuShellClient
import io.github.loganrosen.wattdog.setup.AdbSetupController
import io.github.loganrosen.wattdog.setup.AndroidLocalAdbSession
import java.io.File
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/** Manual DI; the graph is small enough that Hilt would cost more than it saves. */
class AppContainer(context: Context) {
    val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    private val direct = DirectDumpSource(context)
    private val accessPreferences = context.getSharedPreferences("diagnostic-access", Context.MODE_PRIVATE)

    val adbSetup = AdbSetupController(BuildConfig.APPLICATION_ID, ::AndroidLocalAdbSession) {
        check(direct.isAvailable()) { "Battery diagnostic permissions are incomplete." }
        val raw = direct.dump(DumpTarget.BATTERYSTATS_CHARGED)
        BatteryStatsParser.parse(raw)
        check(raw.contains("Statistics since last charge:")) { "Battery statistics were unavailable." }
        check(direct.dump(DumpTarget.POWER).contains("POWER MANAGER")) { "Power diagnostics were unavailable." }
        accessPreferences.edit().putBoolean("verified-direct", true).apply()
    }

    val access = PrivilegedAccess(
        shizuku = ShizukuManager(context),
        shizukuClient = ShizukuShellClient(context),
        direct = direct,
        scope = appScope,
        isDirectPreferred = { accessPreferences.getBoolean("verified-direct", false) },
    )

    val uidResolver = UidResolver(context.packageManager)
    val snapshots = SnapshotStore(File(context.filesDir, "snapshots"))
    val archive = ArchiveStore(context)
    val repository = BatteryRepository(access, snapshots, uidResolver, archive = archive)

    val alertStore = AlertStore(File(context.filesDir, "alerts")).also { appScope.launch { it.load() } }
    val notifier = AnomalyNotifier(context)
    val backgroundCheck = BackgroundCheck(access, repository, snapshots, alertStore, notifier)
    val backgroundChecks = BackgroundChecks(context, alertStore, snapshots)
}
