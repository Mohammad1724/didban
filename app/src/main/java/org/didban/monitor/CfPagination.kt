package org.didban.monitor

/**
 * Cloudflare v4 API pagination (H11) — pure logic, unit-tested on the JVM
 * ([CfPaginationTest] + a local-stub integration test in
 * [CloudflareManagerTest]).
 *
 * The v4 API paginates with `page` + `per_page` and reports
 * `result_info: {page, per_page, count, total_count}`. A page is the last
 * one when it holds fewer items than `perPage`, or when the items already
 * fetched cover `total_count`. Older responses may omit `result_info` —
 * [nextPage] treats a negative total as "unknown" and falls back to the
 * partial-page signal alone.
 */
object CfPagination {

    /**
     * The next page to fetch, or null when the listing is complete.
     * [totalCount] < 0 means the API did not report it.
     */
    fun nextPage(currentPage: Int, perPage: Int, pageItemCount: Int, totalCount: Long): Int? {
        // A partial page is always the last one.
        if (pageItemCount < perPage) return null
        // A full page that already covers the total is the last one.
        if (totalCount >= 0 && currentPage * perPage >= totalCount) return null
        return currentPage + 1
    }

    /** Pagination query string to append to the endpoint URL. */
    fun pageQuery(page: Int, perPage: Int): String = "per_page=$perPage&page=$page"
}
