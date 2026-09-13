package org.didban.monitor

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * Unit tests for [CfPagination] — the H11 Cloudflare page-walking decision
 * logic (JVM).
 */
class CfPaginationTest {

    @Test
    fun fullPageWithMoreTotalGoesToNextPage() {
        // 100 per page, 350 total, page 1 full -> page 2
        assertEquals(2, CfPagination.nextPage(1, 100, 100, 350))
        // page 2 full (200 covered) -> page 3
        assertEquals(3, CfPagination.nextPage(2, 100, 100, 350))
        // page 3 partial (250/350) -> done
        assertNull(CfPagination.nextPage(3, 100, 50, 350))
    }

    @Test
    fun fullPageExactlyCoveringTotalStops() {
        // 200 per 100: page 2 is full AND covers the total exactly
        assertNull(CfPagination.nextPage(2, 100, 100, 200))
    }

    @Test
    fun partialPageAlwaysStops() {
        assertNull(CfPagination.nextPage(1, 50, 12, 1000))
        assertNull(CfPagination.nextPage(7, 100, 0, 1000))
    }

    @Test
    fun unknownTotalFallsBackToPartialPageSignal() {
        val unknown = -1L
        // full page, total unknown -> keep going (the next page may be partial)
        assertEquals(2, CfPagination.nextPage(1, 100, 100, unknown))
        // partial page, total unknown -> stop
        assertNull(CfPagination.nextPage(1, 100, 42, unknown))
    }

    @Test
    fun singlePageSmallListing() {
        assertNull(CfPagination.nextPage(1, 50, 3, 3))
    }

    @Test
    fun pageQueryFormatMatchesCloudflareV4() {
        assertEquals("per_page=100&page=1", CfPagination.pageQuery(1, 100))
        assertEquals("per_page=50&page=12", CfPagination.pageQuery(12, 50))
    }
}
