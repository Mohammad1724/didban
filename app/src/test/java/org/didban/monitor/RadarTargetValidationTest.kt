package org.didban.monitor

import org.junit.Assert.*
import org.junit.Test

/** Radar target form: local completeness check. No network, no Agent. */
class RadarTargetValidationTest {

    @Test
    fun `complete target passes`() {
        assertTrue(isRadarTargetComplete("Main site", "example.com", "443"))
    }

    @Test
    fun `blank name host or port fails`() {
        assertFalse(isRadarTargetComplete("", "example.com", "443"))
        assertFalse(isRadarTargetComplete("  ", "example.com", "443"))
        assertFalse(isRadarTargetComplete("Main", "", "443"))
        assertFalse(isRadarTargetComplete("Main", "example.com", ""))
        assertFalse(isRadarTargetComplete("Main", "example.com", "abc"))
    }

    @Test
    fun `whitespace is trimmed before checking`() {
        assertTrue(isRadarTargetComplete("  Main  ", "  example.com  ", "443"))
    }
}
