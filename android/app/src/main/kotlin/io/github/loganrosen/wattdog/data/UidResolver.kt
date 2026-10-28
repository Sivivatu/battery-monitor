package io.github.loganrosen.wattdog.data

import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.os.Process
import io.github.loganrosen.wattdog.batterystats.model.AppUid
import io.github.loganrosen.wattdog.batterystats.model.SystemUids
import java.util.concurrent.ConcurrentHashMap

/** Nullable metadata is scoped to the actual installed UID, never inferred across profiles. */
data class PackageIdentity(
    val packageName: String,
    val versionCode: Long? = null,
    val versionName: String? = null,
    val firstInstallTime: Long? = null,
)

data class AppLabel(
    val uid: AppUid,
    val label: String,
    val packages: List<String>,
    val isSystem: Boolean,
    /**
     * Set only when the UID is a single package installed for this user, so a settings page for it
     * means the same thing as the batterystats row. Shared, system, and other-user UIDs get null.
     */
    val settingsPackage: String? = null,
    val packageIdentities: List<PackageIdentity> = emptyList(),
) {
    val primaryPackage: String? get() = packages.firstOrNull()
}

/**
 * Maps kernel UIDs from batterystats to app names. PackageManager only sees the current user, so
 * for work-profile or secondary-user UIDs we fall back to the `Apk` package names from the dump.
 */
class UidResolver(private val pm: PackageManager) {
    private val myUserId = Process.myUid() / AppUid.PER_USER_RANGE
    private val cache = ConcurrentHashMap<AppUid, AppLabel>()

    fun resolve(uid: AppUid, dumpPackages: List<String> = emptyList()): AppLabel = cache.getOrPut(uid) { lookup(uid, dumpPackages) }

    fun clear() = cache.clear()

    private fun lookup(uid: AppUid, dumpPackages: List<String>): AppLabel {
        SystemUids.nameFor(uid)?.let { return AppLabel(uid, userSuffix(uid, it), emptyList(), isSystem = true) }
        if (uid.isIsolated) return AppLabel(uid, "Isolated process ($uid)", emptyList(), isSystem = false)

        // Asking about another user's UIDs throws without INTERACT_ACROSS_USERS. Package names from the
        // dump still resolve labels if the same app is installed for this user.
        val fromPm = if (uid.userId == myUserId) runCatching { pm.getPackagesForUid(uid.raw) }.getOrNull() else null
        val packages = (fromPm?.toList() ?: dumpPackages).sorted()
        val infos = packages.mapNotNull { pkg -> runCatching { pm.getApplicationInfo(pkg, 0) }.getOrNull() }
        val labels = infos.map { it.loadLabel(pm).toString() }.distinct()
        val label = when {
            labels.size == 1 -> labels.single()
            labels.size > 1 -> "${labels.first()} +${labels.size - 1}"
            packages.isNotEmpty() -> packages.first()
            uid.isApplication -> "Uninstalled app ($uid)"
            else -> "UID $uid"
        }
        val isSystem = infos.isNotEmpty() && infos.all { it.flags and ApplicationInfo.FLAG_SYSTEM != 0 }
        val settingsPackage = infos.singleOrNull()?.packageName
            ?.takeIf { uid.userId == myUserId && uid.isApplication && packages.size == 1 }
        val identities = packages.map { packageName -> packageIdentity(uid, packageName) }
        return AppLabel(uid, userSuffix(uid, label), packages, isSystem, settingsPackage, identities)
    }

    private fun packageIdentity(uid: AppUid, packageName: String): PackageIdentity {
        if (uid.userId != myUserId) return PackageIdentity(packageName)
        val info = runCatching { pm.getPackageInfo(packageName, 0) }.getOrNull()
            ?.takeIf { it.applicationInfo?.uid == uid.raw }
            ?: return PackageIdentity(packageName)
        return PackageIdentity(packageName, info.longVersionCode, info.versionName, info.firstInstallTime.takeIf { it > 0 })
    }

    private fun userSuffix(uid: AppUid, label: String) = if (uid.userId != 0) "$label (user ${uid.userId})" else label
}
