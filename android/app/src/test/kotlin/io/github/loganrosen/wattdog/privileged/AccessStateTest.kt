package io.github.loganrosen.wattdog.privileged

import org.junit.Assert.assertEquals
import org.junit.Test

class AccessStateTest {
    @Test fun verifiedDirectAccessTakesPriorityOverRunningShizuku() {
        assertEquals(AccessMode.DIRECT, AccessState(ShizukuStatus.Granted, true, directPreferred = true).mode)
    }

    @Test fun unverifiedDirectAccessPreservesExistingShizukuPreference() {
        assertEquals(AccessMode.SHIZUKU, AccessState(ShizukuStatus.Granted, true).mode)
    }

    @Test fun revokedDirectAccessCanStillUseShizuku() {
        assertEquals(AccessMode.SHIZUKU, AccessState(ShizukuStatus.Granted, false, directPreferred = true).mode)
    }
}
